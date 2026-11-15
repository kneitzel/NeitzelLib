package de.neitzel.fx.example.docking;

import de.neitzel.fx.docking.DockGroupDefinition;
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
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/** A small ordinary JavaFX application with editor and tool tabs, without MVVM. */
public final class DockingApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        DockPane pane = getParameters().getRaw().contains("--fxml")
                ? FXMLLoader.load(DockingApp.class.getResource("Docking.fxml")) : initialPane();
        Button activate = new Button("Activate Notes");
        BorderPane shell = new BorderPane(pane);
        shell.setTop(activate);
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
        stage.setTitle("NeitzelLib docking — Java or ordinary FXML");
        stage.show();
        manager.activate(notesId);
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
