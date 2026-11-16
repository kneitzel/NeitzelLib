package de.neitzel.fx.example.docking;

import de.neitzel.fx.docking.CloseConsent;
import de.neitzel.fx.docking.CloseResult;
import de.neitzel.fx.docking.DockGroupDefinition;
import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.util.concurrent.CompletableFuture;

/** Demonstrates a pending close decision while the editor remains open. */
public final class CloseDockingApp extends Application {
    @Override
    public void start(Stage stage) {
        DockPane pane = new DockPane();
        pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
        Label notice = new Label("Close Notes, then allow the close or keep the editor open.");
        Button close = new Button("Close Notes");
        Button allow = new Button("Allow close");
        Button veto = new Button("Keep open");
        var pending = new SimpleObjectProperty<CompletableFuture<CloseConsent>>();
        allow.disableProperty().bind(pending.isNull());
        veto.disableProperty().bind(pending.isNull());
        BorderPane shell = new BorderPane(pane);
        shell.setTop(new ToolBar(close, allow, veto));
        shell.setBottom(notice);
        stage.setScene(new Scene(shell, 760, 420));
        DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
        Dockable editor = new Dockable(new DockableId("notes"), new TextArea("Edit these notes before closing."));
        editor.setTitle("Notes");
        editor.setCloseCheck(request -> {
            notice.setText("Notes remains open while you decide.");
            CompletableFuture<CloseConsent> answer = new CompletableFuture<>();
            pending.set(answer);
            return answer;
        });
        editor.setOnClosed(() -> {
            close.setDisable(true);
            notice.setText("Notes closed. Its resources can now be released.");
        });
        manager.open(editor, DockTarget.area("editors"));
        close.setOnAction(event -> manager.requestTabClose(editor.getId()).thenAcceptAsync(result -> {
            if (result instanceof CloseResult.Vetoed) notice.setText("Close canceled. Notes remains editable.");
        }, Platform::runLater));
        allow.setOnAction(event -> answer(pending, CloseConsent.ALLOW));
        veto.setOnAction(event -> answer(pending, CloseConsent.VETO));
        stage.setTitle("NeitzelLib — asynchronous tab close");
        stage.show();
    }

    private static void answer(SimpleObjectProperty<CompletableFuture<CloseConsent>> pending, CloseConsent consent) {
        CompletableFuture<CloseConsent> answer = pending.get();
        pending.set(null);
        if (answer != null) answer.complete(consent);
    }
}
