package de.neitzel.fx.example.docking;

import de.neitzel.fx.docking.DockGroupDefinition;
import de.neitzel.fx.docking.DockSplitDefinition;
import de.neitzel.fx.docking.SplitDirections;
import javafx.geometry.Orientation;
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
        boolean splits = getParameters().getRaw().contains("--splits");
        DockPane pane = getParameters().getRaw().contains("--fxml")
                ? FXMLLoader.load(DockingApp.class.getResource(splits ? "SplitDocking.fxml" : "Docking.fxml"))
                : initialPane(splits);
        Button activate = new Button("Activate Notes");
        Button toTools = new Button("Move Notes to tools");
        Button toEditors = new Button("Move Notes to editors");
        Button splitNotes = new Button("Split Notes right");
        Label status = new Label("Drag a tab to an edge to split; drag dividers to resize.");
        BorderPane shell = new BorderPane(pane);
        shell.setTop(new ToolBar(activate, toTools, toEditors, splitNotes));
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
        manager.open(other, DockTarget.area(splits ? "preview" : "editors"));
        if (splits) {
            Dockable output = new Dockable(new DockableId("output"), new TextArea("Build output"));
            output.setTitle("Output");
            manager.open(output, DockTarget.area("output"));
        }
        Dockable project = new Dockable(new DockableId("project"), new Label("Project files"));
        project.setTitle("Project");
        project.setIcon(new Label("▦"));
        manager.open(project, DockTarget.area("tools"));
        activate.setOnAction(event -> manager.activate(notesId));
        toTools.setOnAction(event -> report(status, manager.move(notesId, DockTarget.area("tools"))));
        toEditors.setOnAction(event -> report(status, manager.move(notesId, DockTarget.area("editors"))));
        splitNotes.setOnAction(event -> manager.layout().windows().stream()
                .flatMap(window -> window.areas().stream()).flatMap(area -> area.groups().stream())
                .filter(group -> group.dockableIds().contains(notesId)).findFirst()
                .ifPresent(group -> report(status, manager.move(notesId, DockTarget.split(group.id(), Side.RIGHT)))));
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

    private static DockPane initialPane(boolean splits) {
        DockLayout layout = new DockLayout();
        if (splits) {
            DockSplitDefinition secondary = new DockSplitDefinition();
            secondary.setDestinationId("secondary");
            secondary.setOrientation(Orientation.VERTICAL);
            secondary.setFirst(new DockGroupDefinition("preview"));
            secondary.setSecond(new DockGroupDefinition("output"));
            DockSplitDefinition editors = new DockSplitDefinition();
            editors.setDestinationId("editors");
            editors.setFirst(new DockGroupDefinition("primary"));
            editors.setSecond(secondary);
            layout.setCenter(editors);
        } else {
            layout.setCenter(new DockGroupDefinition("editors"));
        }
        DockGroupDefinition tools = new DockGroupDefinition("tools");
        tools.setTabSide(Side.RIGHT);
        tools.setPreferredWidth(200);
        tools.setSplitDirections(SplitDirections.VERTICAL);
        tools.setVisibleWhenEmpty(false);
        tools.setReleaseSpaceWhenEmpty(true);
        layout.setRight(tools);
        DockPane pane = new DockPane();
        pane.setInitialLayout(layout);
        return pane;
    }
}
