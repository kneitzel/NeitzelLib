package de.neitzel.fx.example.docking;

import de.neitzel.fx.docking.DockGroupDefinition;
import de.neitzel.fx.docking.ActionResult;
import de.neitzel.fx.docking.DockLayout;
import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/** A small ordinary JavaFX application with editor and tool tabs, without MVVM. */
public final class DockingApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        DockPane pane = getParameters().getRaw().contains("--fxml")
                ? FXMLLoader.load(DockingApp.class.getResource("Docking.fxml")) : initialPane();
        Button activate = new Button("Activate Notes");
        Button toTools = new Button("Move Notes to tools");
        Button toEditors = new Button("Move Notes to editors");
        Label status = new Label("Drag a tab or use its context menu to move it.");
        BorderPane shell = new BorderPane(pane);
        shell.setTop(new ToolBar(activate, toTools, toEditors));
        shell.setBottom(status);
        stage.setScene(new Scene(shell, 900, 600));
        DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
        DockableId notesId = new DockableId("notes");
        TextArea notes = new TextArea("Unsaved notes stay in this live editor.");
        Dockable editor = new Dockable(notesId, notes);
        editor.setTitle("Notes");
        manager.open(editor, DockTarget.area("editors"));
        Dockable other = new Dockable(new DockableId("readme"), new TextArea("Readme"));
        other.setTitle("Readme");
        manager.open(other, DockTarget.area("editors"));
        Dockable project = new Dockable(new DockableId("project"), new Label("Project files"));
        project.setTitle("Project");
        project.setIcon(new Label("▦"));
        manager.open(project, DockTarget.area("tools"));
        activate.setOnAction(event -> manager.activate(notesId));
        toTools.setOnAction(event -> report(status, manager.move(notesId, DockTarget.area("tools"))));
        toEditors.setOnAction(event -> report(status, manager.move(notesId, DockTarget.area("editors"))));
        stage.setTitle("NeitzelLib docking — Java or ordinary FXML");
        stage.show();
        manager.activate(notesId);
    }

    private static void report(Label status, ActionResult result) {
        status.setText(switch (result) {
            case ActionResult.Applied ignored -> "Notes moved.";
            case ActionResult.NoChange ignored -> "Notes is already at that position.";
            case ActionResult.Rejected rejected -> rejected.failure().message();
        });
    }

    private static DockPane initialPane() {
        DockLayout layout = new DockLayout();
        layout.setCenter(new DockGroupDefinition("editors"));
        DockGroupDefinition tools = new DockGroupDefinition("tools");
        tools.setTabSide(Side.RIGHT);
        tools.setPreferredWidth(200);
        layout.setRight(tools);
        DockPane pane = new DockPane();
        pane.setInitialLayout(layout);
        return pane;
    }
}
