package de.neitzel.fx.example.docking;

import de.neitzel.fx.docking.ActionResult;
import de.neitzel.fx.docking.DockFailure;
import de.neitzel.fx.docking.DockGroupId;
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
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.util.Optional;

/** Demonstrates reset with live editors and atomic rejection under changed placement rules. */
public final class ResetDockingApp extends Application {
    @Override public void start(Stage stage) throws Exception {
        DockPane pane = FXMLLoader.load(DockingApp.class.getResource("Docking.fxml"));
        Button rearrange = new Button("Rearrange");
        Button reset = new Button("Reset layout");
        reset.setId("resetLayout");
        CheckBox allowProject = new CheckBox("Allow Project placement");
        allowProject.setSelected(true);
        Label status = new Label("Edit Notes, rearrange, then reset. Disable Project placement to see a rejected reset.");
        stage.setScene(new Scene(new BorderPane(pane, new ToolBar(rearrange, reset, allowProject),
                null, status, null), 1000, 600));
        DockingOptions options = new DockingOptions();
        options.setPlacementRule((dockable, destination) -> allowProject.isSelected() || !dockable.getId().value().equals("project")
                ? Optional.empty() : Optional.of(new DockFailure(DockFailure.Code.PLACEMENT_REJECTED,
                        dockable.getId(), "Project placement is disabled", null)));
        DockingManager manager = DockingManager.attach(stage, pane, options);
        Dockable notes = new Dockable(new DockableId("notes"), new TextArea("These unsaved edits survive reset."));
        notes.setTitle("Notes");
        Dockable readme = new Dockable(new DockableId("readme"), new TextArea("Readme stays open."));
        readme.setTitle("Readme");
        Dockable project = new Dockable(new DockableId("project"), new TextArea("Project tools"));
        project.setTitle("Project");
        manager.open(notes, DockTarget.area("editors"));
        manager.open(readme, DockTarget.area("editors"));
        manager.open(project, DockTarget.area("tools"));
        rearrange.setOnAction(event -> {
            ActionResult moved = manager.move(project.getId(), DockTarget.area("editors"));
            if (moved instanceof ActionResult.Rejected) { report(status, moved); return; }
            manager.move(readme.getId(), DockTarget.split(new DockGroupId("editors"), Side.BOTTOM));
            manager.move(notes.getId(), DockTarget.floating());
            manager.setGroupCollapsed(new DockGroupId("tools"), true);
            status.setText("Notes is floating, Readme is split and Project moved. Reset restores the starting arrangement.");
        });
        reset.setOnAction(event -> report(status, manager.resetLayout()));
        stage.setTitle("NeitzelLib — reset without losing edits");
        stage.show();
    }

    private static void report(Label status, ActionResult result) {
        status.setText(switch (result) {
            case ActionResult.Applied ignored -> "Starting layout restored; all document contents remain open.";
            case ActionResult.NoChange ignored -> "The layout already has its starting arrangement.";
            case ActionResult.Rejected rejected -> "Layout unchanged: " + rejected.failure().message();
        });
    }
}
