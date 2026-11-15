package de.neitzel.fx.example.docking;

import de.neitzel.fx.docking.ActionResult;
import de.neitzel.fx.docking.DockGroupDefinition;
import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import de.neitzel.fx.docking.MoveCheck;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/** Demonstrates live editor transfers between the main window and floating windows. */
public final class FloatingDockingApp extends Application {
    @Override
    public void start(Stage stage) {
        DockPane pane = new DockPane();
        pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
        pane.getInitialLayout().setLeft(new DockGroupDefinition("project"));
        Label notice = new Label("Edit Notes and select a word before moving it. Project stays in the main window.");
        BorderPane shell = new BorderPane(pane);
        shell.setBottom(notice);
        stage.setScene(new Scene(shell, 1040, 560));
        DockingOptions options = new DockingOptions();
        options.setFloatingWindowCustomizer(configuration -> configuration.setTitle("NeitzelLib — floating editor"));
        DockingManager manager = DockingManager.attach(stage, pane, options);
        Dockable notes = editor("notes", "Notes", "Unsaved notes travel with this editor.\nSelect a word, float the tab, and continue typing.");
        Dockable readme = editor("readme", "Readme", "A second editor can share a floating window with Notes.");
        Dockable project = editor("project", "Project", "This panel is restricted to the main window.\nIts Float menu command is disabled.");
        project.setMainWindowOnly(true);
        manager.open(project, DockTarget.area("project"));
        manager.open(readme, DockTarget.area("editors"));
        manager.open(notes, DockTarget.area("editors"));

        Button floatNotes = new Button("Float Notes");
        Button floatReadme = new Button("Float Readme");
        Button join = new Button("Join Readme");
        Button returnNotes = new Button("Return Notes");
        Button returnReadme = new Button("Return Readme");
        shell.setTop(new ToolBar(floatNotes, floatReadme, join, returnNotes, returnReadme));
        floatNotes.setOnAction(event -> move(manager, notes, DockTarget.floating(), notice));
        floatReadme.setOnAction(event -> move(manager, readme, DockTarget.floating(), notice));
        join.setOnAction(event -> {
            DockTarget target = floatingGroup(manager, readme.getId());
            if (target != null) move(manager, notes, target, notice);
        });
        returnNotes.setOnAction(event -> move(manager, notes, DockTarget.returnToMain(), notice));
        returnReadme.setOnAction(event -> move(manager, readme, DockTarget.returnToMain(), notice));
        Runnable refresh = () -> {
            floatNotes.setDisable(!(manager.checkMove(notes.getId(), DockTarget.floating()) instanceof MoveCheck.Allowed));
            floatReadme.setDisable(!(manager.checkMove(readme.getId(), DockTarget.floating()) instanceof MoveCheck.Allowed));
            returnNotes.setDisable(!(manager.checkMove(notes.getId(), DockTarget.returnToMain()) instanceof MoveCheck.Allowed));
            returnReadme.setDisable(!(manager.checkMove(readme.getId(), DockTarget.returnToMain()) instanceof MoveCheck.Allowed));
            DockTarget target = floatingGroup(manager, readme.getId());
            join.setDisable(target == null || !(manager.checkMove(notes.getId(), target) instanceof MoveCheck.Allowed));
        };
        manager.layoutProperty().addListener((observable, old, current) -> refresh.run());
        refresh.run();
        stage.setTitle("NeitzelLib — floating windows");
        stage.show();
    }

    private static Dockable editor(String id, String title, String text) {
        Dockable editor = new Dockable(new DockableId(id), new TextArea(text));
        editor.setTitle(title);
        return editor;
    }

    private static DockTarget floatingGroup(DockingManager manager, DockableId id) {
        return manager.layout().windows().stream().filter(window -> !window.main())
                .flatMap(window -> window.areas().stream()).flatMap(area -> area.groups().stream())
                .filter(group -> group.dockableIds().contains(id)).findFirst()
                .map(group -> DockTarget.group(group.id())).orElse(null);
    }

    private static void move(DockingManager manager, Dockable editor, DockTarget target, Label notice) {
        ActionResult result = manager.move(editor.getId(), target);
        notice.setText(result instanceof ActionResult.Rejected rejected ? rejected.failure().message()
                : editor.getTitle() + " moved. Continue editing in its destination window.");
    }
}
