package de.neitzel.fx.example.docking;

import de.neitzel.fx.docking.ActionResult;
import de.neitzel.fx.docking.DockGroupChanges;
import de.neitzel.fx.docking.DockGroupMode;
import de.neitzel.fx.docking.DockLayoutView;
import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.util.Optional;

/** An ordinary FXML application with mutually exclusive overlays and pinnable tool groups. */
public final class OverlayDockingApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        DockPane pane = FXMLLoader.load(OverlayDockingApp.class.getResource("OverlayDocking.fxml"));
        Button showProject = new Button("Show Project");
        Button showChanges = new Button("Show Changes");
        Button showEditor = new Button("Focus Editor");
        Button pinProject = new Button("Pin Project");
        Button pinChanges = new Button("Pin Changes");
        Label status = new Label();
        BorderPane shell = new BorderPane(pane);
        shell.setTop(new ToolBar(showProject, showChanges, showEditor, pinProject, pinChanges));
        shell.setBottom(status);
        stage.setScene(new Scene(shell, 920, 540));
        DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
        Dockable editor = content("notes", "Notes", "Edit these notes. Opening an overlay keeps the editor's layout space.");
        Dockable project = content("project", "Project", "src/\n  main/\n  test/\ndocs/");
        Dockable changes = content("changes", "Changes", "Modified files:\n  Notes.java\n  README.md");
        project.setIcon(new Label("▦"));
        changes.setIcon(new Label("Δ"));
        manager.open(editor, DockTarget.area("editors"));
        manager.open(project, DockTarget.area("project"));
        manager.open(changes, DockTarget.area("changes"));
        manager.setGroupCollapsed(group(manager, project.getId()).orElseThrow().id(), true);
        manager.setGroupCollapsed(group(manager, changes.getId()).orElseThrow().id(), true);
        manager.activate(editor.getId());

        Runnable update = () -> {
            describePinButton(pinProject, "Project", group(manager, project.getId()));
            describePinButton(pinChanges, "Changes", group(manager, changes.getId()));
            showProject.setDisable(manager.findDockable(project.getId()).isEmpty());
            showChanges.setDisable(manager.findDockable(changes.getId()).isEmpty());
            showEditor.setDisable(manager.findDockable(editor.getId()).isEmpty());
            status.setText("Project: " + state(group(manager, project.getId()))
                    + "  |  Changes: " + state(group(manager, changes.getId())));
        };
        manager.layoutProperty().addListener((observable, previous, current) -> update.run());
        showProject.setOnAction(event -> manager.activate(project.getId()));
        showChanges.setOnAction(event -> manager.activate(changes.getId()));
        showEditor.setOnAction(event -> manager.activate(editor.getId()));
        pinProject.setOnAction(event -> togglePin(manager, project.getId(), status));
        pinChanges.setOnAction(event -> togglePin(manager, changes.getId(), status));
        update.run();
        stage.setTitle("NeitzelLib — overlays and pinned groups");
        stage.show();
    }

    private static Dockable content(String id, String title, String text) {
        Dockable dockable = new Dockable(new DockableId(id), new TextArea(text));
        dockable.setTitle(title);
        return dockable;
    }

    private static Optional<DockLayoutView.Group> group(DockingManager manager, DockableId id) {
        return manager.layout().windows().stream().flatMap(window -> window.areas().stream())
                .flatMap(area -> area.groups().stream()).filter(group -> group.dockableIds().contains(id)).findFirst();
    }

    private static void togglePin(DockingManager manager, DockableId id, Label status) {
        // A toolbar press may dismiss an overlay before the action is delivered.
        manager.activate(id);
        group(manager, id).ifPresent(group -> {
            ActionResult result = manager.updateGroup(group.id(), DockGroupChanges.builder()
                    .mode(group.mode() == DockGroupMode.OVERLAY ? DockGroupMode.DOCKED : DockGroupMode.OVERLAY).build());
            if (result instanceof ActionResult.Rejected) status.setText("The group cannot change while another operation is pending.");
        });
    }

    private static void describePinButton(Button button, String title, Optional<DockLayoutView.Group> group) {
        button.setDisable(group.isEmpty());
        button.setText(group.map(value -> value.mode() == DockGroupMode.DOCKED ? "Unpin " : "Pin ").orElse("Pin ") + title);
    }

    private static String state(Optional<DockLayoutView.Group> group) {
        return group.map(value -> (value.mode() == DockGroupMode.OVERLAY ? "overlay" : "pinned")
                + (value.collapsed() ? ", closed" : ", open")).orElse("tab closed");
    }
}
