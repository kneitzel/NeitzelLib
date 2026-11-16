package de.neitzel.fx.example.docking;

import de.neitzel.fx.control.StatusBar;
import de.neitzel.fx.docking.ActionResult;
import de.neitzel.fx.docking.DockGroupDefinition;
import de.neitzel.fx.docking.DockGroupMode;
import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import de.neitzel.fx.docking.MoveCheck;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.List;

/** A desktop exercise for live tab transfers, selection preservation and invalid destinations. */
public final class NativeDragDockingApp extends Application {
    @Override
    public void start(Stage stage) {
        DockPane pane = new DockPane();
        pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
        DockGroupDefinition projectGroup = new DockGroupDefinition("project");
        projectGroup.setTabSide(Side.LEFT);
        projectGroup.setCollapsible(true);
        projectGroup.setMode(DockGroupMode.OVERLAY);
        projectGroup.setPreferredWidth(240);
        pane.getInitialLayout().setLeft(projectGroup);

        Button showProject = new Button("Show Project");
        Button floatReadme = new Button("Separate Readme window");
        Button returnNotes = new Button("Return Notes");
        Button returnReadme = new Button("Return Readme");
        ToolBar toolbar = new ToolBar(showProject, floatReadme, returnNotes, returnReadme,
                new Label("Toolbar: no drop destination"));
        Label active = new Label();
        Label hint = new Label("Drag Notes' tab; then keep typing without clicking. Escape cancels a drag.");
        Label count = new Label();
        StatusBar status = new StatusBar();
        status.getLeftItems().add(active);
        status.getRightItems().add(count);
        BorderPane shell = new BorderPane(pane);
        shell.setTop(toolbar);
        hint.setWrapText(true);
        shell.setBottom(new VBox(hint, status));
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double mainWidth = Math.min(920, screen.getWidth() * .60);
        stage.setScene(new Scene(shell, mainWidth, Math.min(560, screen.getHeight() - 120)));
        stage.setX(screen.getMinX() + 24);
        stage.setY(screen.getMinY() + 70);
        stage.setTitle("NeitzelLib — drag tabs between windows");

        DockingOptions options = new DockingOptions();
        options.setFloatingWindowCustomizer(configuration -> configuration.setTitle("NeitzelLib — floating drop destination"));
        DockingManager manager = DockingManager.attach(stage, pane, options);
        Dockable notes = editor("notes", "Notes", "abcdef\nSelect a word, move this tab, then type without an extra click.");
        Dockable readme = editor("readme", "Readme", "This floating editor is a destination.\nDrop Notes onto its tab strip or content area.");
        Dockable project = editor("project", "Project", "Project stays in the main window.\nDragging it outside or into a floating window is rejected.");
        project.setIcon(new Label("▦"));
        project.setMainWindowOnly(true);
        manager.open(project, DockTarget.area("project"));
        manager.open(readme, DockTarget.area("editors"));
        manager.open(notes, DockTarget.area("editors"));
        manager.setGroupCollapsed(new de.neitzel.fx.docking.DockGroupId("project"), true);

        Rectangle2D destination = new Rectangle2D(screen.getMinX() + mainWidth + 48,
                screen.getMinY() + 100, Math.min(500, screen.getWidth() * .34), 400);
        floatReadme.setOnAction(event -> move(manager, readme, DockTarget.floating(destination), hint));
        returnNotes.setOnAction(event -> move(manager, notes, DockTarget.returnToMain(), hint));
        returnReadme.setOnAction(event -> move(manager, readme, DockTarget.returnToMain(), hint));
        showProject.setOnAction(event -> manager.activate(project.getId()));
        Runnable refresh = () -> {
            Dockable current = manager.activeDockable();
            active.setText("Active: " + (current == null ? "none" : current.getTitle()));
            count.setText(manager.layout().windows().size() + " windows");
            floatReadme.setDisable(!(manager.checkMove(readme.getId(), DockTarget.floating(destination)) instanceof MoveCheck.Allowed));
            returnNotes.setDisable(!(manager.checkMove(notes.getId(), DockTarget.returnToMain()) instanceof MoveCheck.Allowed));
            returnReadme.setDisable(!(manager.checkMove(readme.getId(), DockTarget.returnToMain()) instanceof MoveCheck.Allowed));
            showProject.setDisable(manager.findDockable(project.getId()).isEmpty());
        };
        manager.layoutProperty().addListener((observable, previous, current) -> refresh.run());
        manager.activeDockableProperty().addListener((observable, previous, current) -> refresh.run());
        stage.setOnHidden(event -> {
            for (Dockable document : List.of(notes, readme, project)) {
                if (document.getContent().getScene() != null && document.getContent().getScene().getWindow() != stage) {
                    document.getContent().getScene().getWindow().hide();
                }
            }
        });
        stage.show();
        manager.move(readme.getId(), DockTarget.floating(destination));
        manager.activate(notes.getId());
        ((TextArea) notes.getContent()).selectRange(2, 4);
        refresh.run();
    }

    private static Dockable editor(String id, String title, String text) {
        Dockable result = new Dockable(new DockableId(id), new TextArea(text));
        result.setTitle(title);
        return result;
    }

    private static void move(DockingManager manager, Dockable editor, DockTarget destination, Label hint) {
        ActionResult result = manager.move(editor.getId(), destination);
        hint.setText(result instanceof ActionResult.Rejected rejected ? rejected.failure().message()
                : editor.getTitle() + " is ready. Continue typing without another click.");
    }
}
