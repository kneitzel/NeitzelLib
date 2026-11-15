package de.neitzel.fx.example.docking.mvvm;

import de.neitzel.fx.control.StatusBar;
import de.neitzel.fx.docking.DockGroupDefinition;
import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import javafx.scene.Scene;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import lombok.Getter;

/** Composes global bars with an optional MVVM editor and ordinary JavaFX content. */
public final class MvvmDockingWorkspace implements AutoCloseable {
    @Getter private final DockingManager manager;
    @Getter private final MvvmEditor editor;
    @Getter private final Dockable plain;
    private final ToolBar globalTools;
    private final Button save;
    private final Label active;
    private final BooleanBinding saveDisabled;
    private final StringBinding activeText;

    public MvvmDockingWorkspace(Stage stage) {
        DockPane pane = new DockPane();
        pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
        DockGroupDefinition tools = new DockGroupDefinition("tools");
        tools.setPreferredWidth(360);
        pane.getInitialLayout().setRight(tools);
        Button move = new Button("Move Notes to tools");
        Button floating = new Button("Float Notes");
        Button returning = new Button("Return Notes");
        save = new Button("Checkpoint active");
        save.setId("saveActive");
        globalTools = new ToolBar(save, move, floating, returning);
        globalTools.setId("globalTools");
        StatusBar globalStatus = new StatusBar();
        globalStatus.setId("globalStatus");
        active = new Label();
        active.setId("activeStatus");
        globalStatus.getLeftItems().add(active);
        globalStatus.getRightItems().add(new Label("Checkpoints stay in memory"));
        BorderPane shell = new BorderPane(pane, globalTools, null, globalStatus, null);
        stage.setScene(new Scene(shell, 1040, 620));
        manager = DockingManager.attach(stage, pane, new DockingOptions());
        editor = new MvvmEditor("notes");
        plain = new Dockable(new DockableId("plain"), new TextArea("A plain JavaFX editor needs no ViewLoader."));
        plain.setTitle("Plain editor");
        saveDisabled = Bindings.createBooleanBinding(
                () -> manager.activeDockable() != editor.getDockable() || !editor.dirtyProperty().get(),
                manager.activeDockableProperty(), editor.dirtyProperty());
        save.disableProperty().bind(saveDisabled);
        activeText = Bindings.createStringBinding(
                () -> manager.activeDockable() == null ? "No active content" : "Active: " + manager.activeDockable().getTitle(),
                manager.activeDockableProperty(), editor.getDockable().titleProperty(), plain.titleProperty());
        active.textProperty().bind(activeText);
        save.setOnAction(event -> {
            if (manager.activeDockable() == editor.getDockable()) editor.checkpoint();
        });
        manager.open(plain, DockTarget.area("editors"));
        manager.open(editor.getDockable(), DockTarget.area("editors"));
        move.setOnAction(event -> manager.move(editor.getDockable().getId(), DockTarget.area("tools")));
        floating.setOnAction(event -> manager.move(editor.getDockable().getId(), DockTarget.floating()));
        returning.setOnAction(event -> manager.move(editor.getDockable().getId(), DockTarget.returnToMain()));
    }

    /** Releases application-owned global bindings when this demonstration ends. */
    @Override public void close() {
        save.disableProperty().unbind();
        saveDisabled.dispose();
        active.textProperty().unbind();
        activeText.dispose();
        for (var item : globalTools.getItems()) {
            if (item instanceof Button button) {
                button.setOnAction(null);
                button.setDisable(true);
            }
        }
        editor.close();
    }
}
