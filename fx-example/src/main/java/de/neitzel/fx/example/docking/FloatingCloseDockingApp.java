package de.neitzel.fx.example.docking;

import de.neitzel.fx.docking.CloseConsent;
import de.neitzel.fx.docking.CloseResult;
import de.neitzel.fx.docking.DockGroupDefinition;
import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.DockWindowId;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import javafx.application.Application;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Two editors make early/late vetoes and independent window requests visible. */
public final class FloatingCloseDockingApp extends Application {
    private final SimpleObjectProperty<PendingAnswer> pending = new SimpleObjectProperty<>();
    private final TextArea history = new TextArea();
    private final Label question = new Label("Close Editors to ask First and Second in order.");

    @Override
    public void start(Stage stage) {
        DockPane pane = new DockPane();
        pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
        Button close = new Button("Close Editors");
        Button join = new Button("Repeat Editors close");
        Button tab = new Button("Close Second tab");
        Button other = new Button("Close Other window");
        Button allow = new Button("Save and allow");
        Button veto = new Button("Keep open");
        allow.disableProperty().bind(pending.isNull());
        veto.disableProperty().bind(pending.isNull());
        allow.setOnAction(event -> answer(CloseConsent.ALLOW));
        veto.setOnAction(event -> answer(CloseConsent.VETO));
        history.setEditable(false);
        history.setPrefRowCount(10);
        BorderPane shell = new BorderPane(pane);
        shell.setTop(new VBox(new ToolBar(close, join, tab, other), new ToolBar(allow, veto), question));
        shell.setBottom(history);
        stage.setScene(new Scene(shell, 920, 460));
        DockingOptions options = new DockingOptions();
        options.setFloatingWindowCustomizer(configuration -> configuration.setTitle("Editors — close me to ask every tab"));
        DockingManager manager = DockingManager.attach(stage, pane, options);
        stage.setTitle("NeitzelLib — shared floating-window consent");
        stage.show();

        Dockable first = editor("First");
        Dockable second = editor("Second");
        manager.open(first, DockTarget.floating());
        var editors = manager.layout().windows().getLast();
        DockWindowId editorsId = editors.id();
        manager.open(second, DockTarget.group(editors.areas().getFirst().groups().getFirst().id()));
        Dockable independent = editor("Other");
        manager.open(independent, DockTarget.floating());
        DockWindowId otherId = manager.layout().windows().getLast().id();

        close.setOnAction(event -> report("Editors", manager.requestFloatingWindowClose(editorsId)));
        join.setOnAction(event -> report("Repeated Editors", manager.requestFloatingWindowClose(editorsId)));
        tab.setOnAction(event -> report("Second tab", manager.requestTabClose(second.getId())));
        other.setOnAction(event -> report("Other window", manager.requestFloatingWindowClose(otherId)));
        stage.setOnHidden(event -> {
            if (first.getContent().getScene() != null) first.getContent().getScene().getWindow().hide();
            if (independent.getContent().getScene() != null) independent.getContent().getScene().getWindow().hide();
        });
    }

    private Dockable editor(String title) {
        TextArea content = new TextArea(title + " unsaved text");
        Dockable editor = new Dockable(new DockableId(title.toLowerCase(java.util.Locale.ROOT)), content);
        editor.setTitle(title);
        editor.setCloseCheck(request -> {
            log("Checking " + title + " in " + request.scope().kind() + "; every editor stays open.");
            question.setText("Decision for " + title + " — Save and allow, or Keep open");
            CompletableFuture<CloseConsent> answer = new CompletableFuture<>();
            pending.set(new PendingAnswer(title, content, answer));
            return answer;
        });
        editor.setOnClosed(() -> log("Released " + title + " after the complete close."));
        return editor;
    }

    private void answer(CloseConsent consent) {
        PendingAnswer current = pending.get();
        if (current == null) return;
        pending.set(null);
        if (consent == CloseConsent.ALLOW) {
            log("Saved " + current.title + ": " + current.content.getText());
        } else {
            log("Veto from " + current.title + "; remaining editors will still be asked.");
        }
        question.setText("Decision recorded.");
        current.answer.complete(consent);
    }

    private void report(String label, CompletionStage<CloseResult> result) {
        result.thenAccept(outcome -> log(label + ": " + outcome));
    }

    private void log(String message) { history.appendText(message + "\n"); }
    private record PendingAnswer(String title, TextArea content, CompletableFuture<CloseConsent> answer) { }
}
