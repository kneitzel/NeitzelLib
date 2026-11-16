package de.neitzel.fx.example.docking;

import de.neitzel.fx.docking.*;
import javafx.application.Application;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** A visible save/retry workflow owns storage decisions while the manager owns close ordering. */
public final class ShutdownDockingApp extends Application {
    private final SimpleObjectProperty<CompletableFuture<ShutdownDecision>> pendingSave = new SimpleObjectProperty<>();
    private final SimpleObjectProperty<CompletableFuture<CloseConsent>> pendingConsent = new SimpleObjectProperty<>();
    private final SimpleBooleanProperty shutdownRequested = new SimpleBooleanProperty();
    private final SimpleBooleanProperty ended = new SimpleBooleanProperty();
    private final Label status = new Label();
    private final TextArea history = new TextArea();
    private final CheckBox delayFloating = new CheckBox("Delay Preview window consent");
    private final CheckBox lateVeto = new CheckBox("Preview vetoes last");
    private final CheckBox saveFails = new CheckBox("Simulate save failure");
    private final CheckBox throwShutdown = new CheckBox("Throw before saving");
    private final ComboBox<MainWindowCloseMode> nextOwnership = new ComboBox<>();
    private final Button quit = new Button("Quit workspace");
    private final Button replace = new Button("Replace workspace");
    private final Button applicationClose = new Button("Close application window");
    private DockingManager manager;
    private DockPane pane;
    private Stage stage;
    private MainWindowCloseMode ownership;
    private LayoutSnapshot pendingSnapshot;
    private LayoutSnapshot savedSnapshot;
    private int generation;
    private int retries;

    @Override public void start(Stage stage) throws IOException {
        this.stage = stage;
        pane = FXMLLoader.load(ShutdownDockingApp.class.getResource("ShutdownDocking.fxml"));
        status.setId("shutdown-status"); history.setId("shutdown-history");
        history.setEditable(false); history.setPrefRowCount(9);
        lateVeto.setId("late-veto"); saveFails.setId("save-fails"); throwShutdown.setId("throw-shutdown");
        saveFails.setSelected(true);
        delayFloating.setId("delay-floating-consent"); delayFloating.setSelected(true);
        nextOwnership.setId("next-ownership");
        nextOwnership.getItems().setAll(MainWindowCloseMode.values());
        nextOwnership.setValue(MainWindowCloseMode.AUTOMATIC);
        nextOwnership.setConverter(new StringConverter<>() {
            @Override public String toString(MainWindowCloseMode value) {
                return value == MainWindowCloseMode.AUTOMATIC ? "Library closes main" : "Application closes main";
            }
            @Override public MainWindowCloseMode fromString(String text) { throw new UnsupportedOperationException("Selection only"); }
        });
        quit.setId("quit-workspace"); replace.setId("replace-workspace"); applicationClose.setId("close-application-window");
        applicationClose.setDisable(true);
        quit.setOnAction(event -> requestClose(false));
        replace.setOnAction(event -> requestClose(true));
        applicationClose.setOnAction(event -> stage.hide());
        Button retry = saveButton("Retry save", "retry-save", () -> attemptSave(false));
        Button proceed = saveButton("Save successfully", "proceed-save", () -> attemptSave(true));
        Button cancel = saveButton("Cancel shutdown", "cancel-save", () -> finishSave(ShutdownDecision.CANCEL, null));
        Button fail = saveButton("Fail asynchronously", "fail-save", () -> finishSave(null,
                new IllegalStateException("Simulated asynchronous storage failure")));
        Button closePreview = new Button("Close Preview window"); closePreview.setId("close-preview-window");
        closePreview.disableProperty().bind(pendingConsent.isNotNull().or(shutdownRequested).or(ended));
        closePreview.setOnAction(event -> requestPreviewClose());
        Button allowPreview = consentButton("Allow Preview", "allow-preview", CloseConsent.ALLOW);
        Button vetoPreview = consentButton("Veto Preview", "veto-preview", CloseConsent.VETO);
        BorderPane shell = new BorderPane(pane);
        shell.setTop(new VBox(new ToolBar(quit, replace, applicationClose),
                new ToolBar(lateVeto, saveFails, throwShutdown),
                new ToolBar(delayFloating, closePreview, allowPreview, vetoPreview),
                new ToolBar(new Label("Next workspace:"), nextOwnership, retry, proceed, cancel, fail), status));
        shell.setBottom(history);
        stage.setScene(new Scene(shell, 1100, 640));
        stage.setTitle("NeitzelLib — Quit, save and replace");
        stage.setOnCloseRequest(event -> {
            if (ownership == MainWindowCloseMode.APPLICATION_COORDINATED && !ended.get()) {
                event.consume(); requestClose(false);
            }
        });
        attachWorkspace();
        stage.show();
    }

    private Button saveButton(String text, String id, Runnable action) {
        Button button = new Button(text); button.setId(id);
        button.disableProperty().bind(pendingSave.isNull());
        button.setOnAction(event -> action.run()); return button;
    }

    private Button consentButton(String text, String id, CloseConsent answer) {
        Button button = new Button(text); button.setId(id);
        button.disableProperty().bind(pendingConsent.isNull());
        button.setOnAction(event -> {
            CompletableFuture<CloseConsent> pending = pendingConsent.get();
            if (pending == null) return;
            pendingConsent.set(null); pending.complete(answer);
        });
        return button;
    }

    private void requestPreviewClose() {
        var window = manager.layout().windows().stream().filter(candidate -> !candidate.main())
                .filter(candidate -> candidate.areas().stream().flatMap(area -> area.groups().stream())
                        .anyMatch(group -> group.dockableIds().contains(new DockableId("preview")))).findFirst();
        if (window.isEmpty()) { status.setText("Preview is not in a floating window."); return; }
        manager.requestFloatingWindowClose(window.get().id()).thenAccept(result -> {
            log("Preview window: " + result);
            if (!shutdownRequested.get()) status.setText("Preview window: " + result);
        });
    }

    private void attachWorkspace() {
        ownership = nextOwnership.getValue();
        DockingOptions options = new DockingOptions();
        options.setMainWindowCloseMode(ownership);
        options.setBeforeShutdown(this::beforeShutdown);
        options.setFloatingWindowCustomizer(configuration -> configuration.setTitle("Preview — included in Quit and Replace"));
        manager = DockingManager.attach(stage, pane, options);
        ended.set(false); shutdownRequested.set(false);
        quit.disableProperty().unbind(); replace.disableProperty().unbind();
        var scope = manager.closingScopeProperty();
        var globalBusy = javafx.beans.binding.Bindings.createBooleanBinding(() -> scope.get() != null
                && (scope.get().kind() == CloseScope.Kind.MAIN_WINDOW || scope.get().kind() == CloseScope.Kind.MANAGER_ONLY), scope);
        quit.disableProperty().bind(globalBusy.or(shutdownRequested).or(ended));
        replace.disableProperty().bind(globalBusy.or(shutdownRequested).or(ended));
        applicationClose.setDisable(true);
        generation++;
        manager.open(editor("notes", "Notes"), DockTarget.area("editors"));
        manager.open(editor("project", "Project"), DockTarget.area("tools"));
        manager.setGroupCollapsed(new DockGroupId("tools"), true);
        manager.open(editor("preview", "Preview"), DockTarget.floating());
        status.setText("Workspace " + generation + " attached; Project is collapsed, Preview is floating.");
        log("Attached workspace " + generation + " with " + ownership);
    }

    private Dockable editor(String id, String title) {
        TextArea content = new TextArea(title + " in workspace " + generation); content.setId(id + "-content");
        Dockable dockable = new Dockable(new DockableId(id), content); dockable.setTitle(title);
        dockable.setCloseCheck(request -> {
            log("Checked " + id + " in " + request.scope().kind() + "; all contents remain open.");
            if (id.equals("preview") && delayFloating.isSelected() && request.scope().kind() == CloseScope.Kind.FLOATING_WINDOW) {
                CompletableFuture<CloseConsent> answer = new CompletableFuture<>(); pendingConsent.set(answer);
                status.setText("Preview consent pending; Quit can wait for this window.");
                return answer;
            }
            boolean veto = id.equals("preview") && lateVeto.isSelected();
            if (veto) status.setText("Late veto from Preview; no content was closed.");
            return CompletableFuture.completedFuture(veto ? CloseConsent.VETO : CloseConsent.ALLOW);
        });
        dockable.setOnClosed(() -> log("Released " + id));
        return dockable;
    }

    private CompletionStage<ShutdownDecision> beforeShutdown(ShutdownRequest request) {
        if (throwShutdown.isSelected()) throw new IllegalStateException("Simulated synchronous save setup failure");
        pendingSnapshot = request.snapshot(); retries = 0;
        log("Snapshot " + request.reason() + ": " + pendingSnapshot.windows().size() + " windows before any content closes.");
        status.setText("Save pending: " + request.reason() + ". Retry, save successfully, cancel or fail.");
        CompletableFuture<ShutdownDecision> decision = new CompletableFuture<>(); pendingSave.set(decision);
        return decision;
    }

    private void attemptSave(boolean forceSuccess) {
        if (pendingSave.get() == null) return;
        if (!forceSuccess && saveFails.isSelected()) {
            retries++; status.setText("Retry failed (" + retries + "); the same shutdown remains pending.");
            log("Retry " + retries + " failed; every window and editor stays open."); return;
        }
        savedSnapshot = pendingSnapshot;
        log("Saved " + savedSnapshot.windows().size() + " windows in application-owned memory.");
        finishSave(ShutdownDecision.PROCEED, null);
    }

    private void finishSave(ShutdownDecision decision, RuntimeException failure) {
        CompletableFuture<ShutdownDecision> pending = pendingSave.get();
        if (pending == null) return;
        pendingSave.set(null); pendingSnapshot = null;
        status.setText(failure == null ? "Save decision: " + decision : "Save failed: " + failure.getMessage());
        if (failure == null) pending.complete(decision); else pending.completeExceptionally(failure);
    }

    private void requestClose(boolean replaceWorkspace) {
        CloseScope scope = manager.closingScopeProperty().get();
        if (scope != null && scope.kind() == CloseScope.Kind.FLOATING_WINDOW) {
            status.setText((replaceWorkspace ? "Replacement" : "Quit") + " waiting for Preview consent.");
        }
        shutdownRequested.set(true);
        CompletionStage<CloseResult> request = replaceWorkspace ? manager.requestManagerClose() : manager.requestMainWindowClose();
        request.thenAccept(result -> {
            shutdownRequested.set(false);
            status.setText(result.toString()); log(result.toString());
            if (result instanceof CloseResult.Closed) {
                ended.set(true);
                if (replaceWorkspace) attachWorkspace();
                else if (ownership == MainWindowCloseMode.APPLICATION_COORDINATED) applicationClose.setDisable(false);
            }
        });
    }

    private void log(String message) { history.appendText(message + "\n"); }
}
