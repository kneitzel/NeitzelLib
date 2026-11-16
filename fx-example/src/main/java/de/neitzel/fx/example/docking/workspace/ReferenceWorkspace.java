package de.neitzel.fx.example.docking.workspace;

import de.neitzel.fx.control.StatusBar;
import de.neitzel.fx.docking.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Application-owned persistence, editor resources and save decisions around an independent docking manager. */
public final class ReferenceWorkspace implements AutoCloseable {
    private final Stage stage;
    private final WorkspaceStore store;
    private final boolean mvvm;
    private final DockPane pane;
    private final ExecutorService storage = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("workspace-storage").factory());
    private final Map<DockableId, WorkspaceEditor> editors = new LinkedHashMap<>();
    private final Label status = identified(new Label(), "workspaceStatus");
    private final Label active = identified(new Label(), "activeLabel");
    private final TextArea events = identified(new TextArea(), "workspaceEvents");
    private final CheckBox veto = check("Readme vetoes close", "vetoReadme");
    private final CheckBox saveFailure = check("Simulate save failure", "saveFailure");
    private final CheckBox skipReadme = check("Readme missing on reload", "skipReadme");
    private final CheckBox mainOnly = check("Project main window only", "projectMainOnly");
    private final CheckBox blockProject = check("Project cannot use original group", "blockProject");
    private final CheckBox fallback = check("Allow designated editor fallback", "allowFallback");
    private final SimpleObjectProperty<CompletableFuture<ShutdownDecision>> pending = new SimpleObjectProperty<>();
    private DockingManager manager;
    private LayoutSnapshot pendingLayout;
    private StoredWorkspace lastSaved;
    private boolean saving;
    private boolean closeRequested;
    private boolean closed;

    public ReferenceWorkspace(Stage stage, boolean fxml, boolean mvvm, WorkspaceStore store,
                              Optional<StoredWorkspace> saved) throws IOException {
        this.stage = stage; this.store = store; this.mvvm = mvvm; pane = WorkspaceView.create(fxml);
        fallback.setSelected(true);
        ToolBar tools = identified(new ToolBar(
                button("Save workspace", "saveWorkspace", () -> save(null)),
                button("Reload saved", "reloadWorkspace", this::reload),
                button("Reset layout", "resetLayout", () -> report(manager.resetLayout())),
                button("Replace workspace", "replaceWorkspace", () -> requestClose(true)),
                button("Quit", "quitWorkspace", () -> requestClose(false))), "globalTools");
        ToolBar placement = new ToolBar(
                button("Float active", "floatActive", () -> moveActive(DockTarget.floating())),
                button("Return active", "returnActive", () -> moveActive(DockTarget.returnToMain())),
                button("Split active right", "splitActive", this::splitActive),
                button("Project", "showProject", () -> report(manager.activate(new DockableId("project")))),
                button("Changed files", "showChanges", () -> report(manager.activate(new DockableId("changes")))),
                button("Clear active editor", "clearActive", this::clearActive));
        Button retry = button("Save / Retry", "retrySave", () -> save(pending.get()));
        Button cancel = button("Cancel close", "cancelSave", this::cancelSave);
        retry.disableProperty().bind(pending.isNull()); cancel.disableProperty().bind(pending.isNull());
        TitledPane scenarios = new TitledPane("Restore and close scenarios", new VBox(
                new ToolBar(skipReadme, mainOnly, blockProject), new ToolBar(fallback, veto, saveFailure)));
        scenarios.setExpanded(false); scenarios.setId("workspaceScenarios");
        events.setEditable(false); events.setPrefRowCount(4);
        StatusBar globalStatus = identified(new StatusBar(), "globalStatus");
        globalStatus.getLeftItems().add(status); globalStatus.getRightItems().add(active);
        BorderPane shell = new BorderPane(pane);
        shell.setTop(new VBox(tools, placement, scenarios, new ToolBar(retry, cancel)));
        shell.setBottom(new VBox(events, globalStatus));
        stage.setTitle("NeitzelLib — Reference workspace" + (mvvm ? " (MVVM Notes)" : " (plain JavaFX)"));
        stage.setScene(new Scene(shell, 1180, 780));
        attach(saved); stage.show();
    }

    private void attach(Optional<StoredWorkspace> saved) {
        DockingOptions options = new DockingOptions();
        options.setBeforeShutdown(this::beforeShutdown);
        options.setFallbackTarget(fallback.isSelected() ? DockTarget.area("editors") : null);
        options.setFloatingWindowCustomizer(configuration -> configuration.setTitle("Reference workspace — floating"));
        options.setPlacementRule((dockable, target) -> blockProject.isSelected() && dockable.getId().value().equals("project")
                && target instanceof ResolvedDockTarget.Insertion insertion && insertion.groupId().value().equals("project")
                ? Optional.of(new DockFailure(DockFailure.Code.PLACEMENT_REJECTED, dockable.getId(), "Project group disabled by application", null))
                : Optional.empty());
        manager = DockingManager.attach(stage, pane, options);
        active.textProperty().unbind();
        active.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(() -> {
            Dockable selected = manager.activeDockableProperty().get();
            return "Active: " + (selected == null ? "none" : selected.getId().value());
        }, manager.activeDockableProperty()));
        if (saved.isPresent()) {
            lastSaved = saved.get();
            RestoreResult result = manager.restore(lastSaved.layout(), id -> resolve(id, lastSaved));
            report(result);
            if (result instanceof RestoreResult.Applied) { log("Restored workspace before showing new windows"); return; }
            log("Restore rejected atomically; opening initial layout");
            defaults();
            status.setText("Restore rejected: " + ((RestoreResult.Rejected) result).failure().code()
                    + "; opened initial workspace. See events for details.");
            return;
        }
        defaults();
    }

    private void defaults() {
        manager.open(editor("readme", new StoredWorkspace.Document("Readme", "Drag a tab, resize groups, or open its keyboard menu with Shift+F10.")), DockTarget.area("editors"));
        manager.open(editor("notes", new StoredWorkspace.Document("Notes", "Write notes here. Unsaved text survives move, float and reset.")), DockTarget.area("editors"));
        manager.open(tool("project"), DockTarget.area("project"));
        manager.open(tool("changes"), DockTarget.area("changes"));
        manager.activate(new DockableId("notes"));
        status.setText("Workspace ready; " + store.getFile());
    }

    private DockableResolution resolve(DockableId id, StoredWorkspace saved) {
        if (id.value().equals("readme") && skipReadme.isSelected()) return new DockableResolution.Missing();
        if (id.value().equals("project") || id.value().equals("changes")) return new DockableResolution.Available(tool(id.value()));
        StoredWorkspace.Document document = saved.documents().get(id);
        return document == null ? new DockableResolution.Missing() : new DockableResolution.Available(editor(id.value(), document));
    }

    private Dockable editor(String id, StoredWorkspace.Document document) {
        WorkspaceEditor editor = new WorkspaceEditor(id, document, mvvm && id.equals("notes"));
        editors.put(editor.getDockable().getId(), editor);
        lifecycle(editor.getDockable(), () -> { editors.remove(editor.getDockable().getId(), editor); editor.close(); });
        return editor.getDockable();
    }

    private Dockable tool(String id) {
        ListView<String> content = new ListView<>(); content.setId(id + "Content");
        content.getItems().addAll(id.equals("project") ? new String[]{"Notes", "Readme", "Workspace layout"}
                : new String[]{"Application owns document state", "Save workspace stores current text"});
        Dockable dockable = new Dockable(new DockableId(id), content);
        dockable.setIcon(new Label(id.equals("project") ? "P" : "Δ"));
        dockable.setAccessibleName(id.equals("project") ? "Project" : "Changed files");
        dockable.setFocusTarget(() -> content); dockable.setMainWindowOnly(id.equals("project") && mainOnly.isSelected());
        lifecycle(dockable, () -> { }); return dockable;
    }

    private void lifecycle(Dockable dockable, Runnable cleanup) {
        dockable.setCloseCheck(request -> {
            log("Checked " + dockable.getId().value() + " — " + request.scope().kind());
            return CompletableFuture.completedFuture(dockable.getId().value().equals("readme") && veto.isSelected()
                    ? CloseConsent.VETO : CloseConsent.ALLOW);
        });
        dockable.setOnClosed(() -> { cleanup.run(); log("Closed " + dockable.getId().value()); });
        dockable.setOnDiscarded(() -> { cleanup.run(); log("Discarded " + dockable.getId().value()); });
    }

    private CompletionStage<ShutdownDecision> beforeShutdown(ShutdownRequest request) {
        pendingLayout = request.snapshot();
        CompletableFuture<ShutdownDecision> decision = new CompletableFuture<>(); pending.set(decision);
        status.setText("Save pending — Save / Retry or Cancel close"); log("All consent obtained; save pending " + request.reason());
        return decision;
    }

    private StoredWorkspace capture(LayoutSnapshot layout) {
        Map<DockableId, StoredWorkspace.Document> documents = new LinkedHashMap<>();
        editors.forEach((id, editor) -> documents.put(id, editor.document()));
        return new StoredWorkspace(StoredWorkspace.CURRENT_VERSION, layout, documents);
    }

    private void save(CompletableFuture<ShutdownDecision> decision) {
        if (saving || closed) return;
        if (decision == null && pending.get() != null) { status.setText("Use Save / Retry for the pending close"); return; }
        StoredWorkspace snapshot = capture(decision == null ? manager.snapshot() : pendingLayout);
        if (saveFailure.isSelected()) { status.setText("Save failed (simulated); retry or cancel keeps every editor open"); return; }
        saving = true; status.setText("Saving workspace…");
        CompletableFuture.runAsync(() -> {
            try { store.save(snapshot); } catch (IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
        }, storage).whenComplete((unused, failure) -> Platform.runLater(() -> {
            saving = false;
            if (closed || decision != null && pending.get() != decision) return;
            if (failure != null) { status.setText("Save failed: " + failure.getCause().getMessage() + "; retry or cancel"); return; }
            lastSaved = snapshot;
            if (!capture(snapshot.layout()).documents().equals(snapshot.documents())) {
                status.setText("Text changed while saving; save again before closing"); return;
            }
            editors.values().forEach(WorkspaceEditor::checkpoint);
            status.setText("Saved workspace to " + store.getFile()); log("Saved " + snapshot.layout().windows().size() + " windows");
            if (decision != null) { pending.set(null); pendingLayout = null; decision.complete(ShutdownDecision.PROCEED); }
        }));
    }

    private void cancelSave() {
        CompletableFuture<ShutdownDecision> decision = pending.get();
        if (decision == null) return;
        pending.set(null); pendingLayout = null; decision.complete(ShutdownDecision.CANCEL);
    }

    private void requestClose(boolean replace) {
        if (closeRequested) return;
        closeRequested = true;
        CompletionStage<CloseResult> result = replace ? manager.requestManagerClose() : manager.requestMainWindowClose();
        result.thenAccept(answer -> {
            closeRequested = false;
            report(answer);
            if (answer instanceof CloseResult.Closed) {
                active.textProperty().unbind();
                if (replace) { attach(Optional.ofNullable(lastSaved)); log("Reattached workspace to the same Stage and DockPane"); }
                else close();
            }
        });
    }

    private void reload() {
        if (lastSaved == null) { status.setText("Save a workspace first; Reload uses that immutable saved state"); return; }
        // The same consent and save protocol protects existing content before replacement.
        requestClose(true);
    }
    private void moveActive(DockTarget target) {
        Dockable selected = manager.activeDockableProperty().get();
        if (selected != null) report(manager.move(selected.getId(), target));
    }
    private void splitActive() {
        Dockable selected = manager.activeDockableProperty().get(); if (selected == null) return;
        manager.layout().windows().stream().flatMap(window -> window.areas().stream()).flatMap(area -> area.groups().stream())
                .filter(group -> group.dockableIds().contains(selected.getId())).findFirst()
                .ifPresent(group -> report(manager.move(selected.getId(), DockTarget.split(group.id(), Side.RIGHT))));
    }
    private void clearActive() {
        Dockable selected = manager.activeDockableProperty().get();
        if (selected != null && editors.containsKey(selected.getId())) editors.get(selected.getId()).getInput().clear();
    }
    private void report(Object result) { status.setText(result.toString()); log(result.toString()); }
    private void log(String text) { events.appendText(text + "\n"); }
    private static Button button(String title, String id, Runnable action) {
        Button button = identified(new Button(title), id); button.setOnAction(event -> action.run()); return button;
    }
    private static CheckBox check(String title, String id) { return identified(new CheckBox(title), id); }
    private static <T extends javafx.scene.Node> T identified(T node, String id) { node.setId(id); return node; }
    /** Stops application I/O and bindings; it does not bypass the manager's consent protocol. */
    @Override public void close() {
        if (closed) return; closed = true; storage.shutdown();
        active.textProperty().unbind(); editors.values().forEach(WorkspaceEditor::close);
    }
}
