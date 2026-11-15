package de.neitzel.fx.view;

import de.neitzel.fx.docking.DockGroupDefinition;
import de.neitzel.fx.docking.DockLayout;
import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TabPane;
import de.neitzel.fx.docking.DockFailure;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** User gestures operate on visible tab headers and the public docking state. */
class MoveTabsByGestureTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @Test
    void draggingAHeaderIntoAnotherGroupsContentAppendsItsLiveEditor() throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            initial.setRight(new DockGroupDefinition("tools"));
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 900, 500));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            TextArea notes = new TextArea("Unsaved notes");
            Dockable editor = new Dockable(new DockableId("notes"), notes);
            editor.setTitle("Notes");
            TextArea project = new TextArea("Project");
            Dockable tools = new Dockable(new DockableId("project"), project);
            tools.setTitle("Project");
            manager.open(editor, DockTarget.area("editors"));
            manager.open(tools, DockTarget.area("tools"));
            stage.show();
            try {
                pane.applyCss();
                pane.layout();
                Node header = header(pane, "Notes");
                Bounds from = header.localToScreen(header.getBoundsInLocal());
                Bounds into = project.localToScreen(project.getBoundsInLocal());
                mouse(header, MouseEvent.MOUSE_PRESSED, from.getCenterX(), from.getCenterY(), true);
                mouse(header, MouseEvent.MOUSE_DRAGGED, into.getCenterX(), into.getCenterY(), true);
                mouse(header, MouseEvent.MOUSE_RELEASED, into.getCenterX(), into.getCenterY(), false);

                assertEquals(List.of(tools.getId(), editor.getId()), manager.layout().windows().getFirst()
                        .areas().get(1).groups().getFirst().dockableIds());
                assertSame(notes, manager.findDockable(editor.getId()).orElseThrow().getContent());
                assertEquals("Unsaved notes", notes.getText());
                assertSame(editor, manager.activeDockable());
            } finally {
                stage.hide();
            }
        });
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    void stripMarkerPreviewsTheInsertionPositionInEveryOrientation(Side side) throws Exception {
        FxThread.onFx(() -> {
            DockGroupDefinition group = new DockGroupDefinition("editors");
            group.setTabSide(side);
            DockLayout initial = new DockLayout();
            initial.setCenter(group);
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 800, 500));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            for (String name : List.of("Alpha", "Beta", "Notes")) {
                Dockable dockable = new Dockable(new DockableId(name), new TextArea(name));
                dockable.setTitle(name);
                manager.open(dockable, DockTarget.area("editors"));
            }
            stage.show();
            try {
                pane.applyCss();
                pane.layout();
                Node notes = header(pane, "Notes");
                Bounds from = notes.localToScreen(notes.getBoundsInLocal());
                Node alpha = header(pane, "Alpha");
                Bounds first = alpha.localToScreen(alpha.getBoundsInLocal());
                Node beta = header(pane, "Beta");
                Bounds second = beta.localToScreen(beta.getBoundsInLocal());
                boolean horizontal = side.isHorizontal();
                double direction = Math.signum(horizontal ? second.getCenterX() - first.getCenterX()
                        : second.getCenterY() - first.getCenterY());
                double x = first.getCenterX() - (horizontal ? direction * first.getWidth() / 3 : 0);
                double y = first.getCenterY() - (horizontal ? 0 : direction * first.getHeight() / 3);
                mouse(notes, MouseEvent.MOUSE_PRESSED, from.getCenterX(), from.getCenterY(), true);
                mouse(notes, MouseEvent.MOUSE_DRAGGED, x, y, true);

                Node marker = pane.lookup(".dock-drop-indicator");
                assertNotNull(marker);
                assertTrue(marker.isVisible());
                assertTrue(marker.isMouseTransparent());
                assertEquals(List.of(new DockableId("Alpha"), new DockableId("Beta"), new DockableId("Notes")),
                        manager.layout().windows().getFirst().areas().getFirst().groups().getFirst().dockableIds());
                mouse(notes, MouseEvent.MOUSE_RELEASED, x, y, false);
                assertEquals(List.of(new DockableId("Notes"), new DockableId("Alpha"), new DockableId("Beta")),
                        manager.layout().windows().getFirst().areas().getFirst().groups().getFirst().dockableIds());
                assertFalse(marker.isVisible());
            } finally {
                stage.hide();
            }
        });
    }

    @Test
    void escapeAndInvalidManagedWindowDropsPreservePlacement() throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            initial.setRight(new DockGroupDefinition("tools"));
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Label global = new Label("Global status outside the docking area");
            BorderPane shell = new BorderPane(pane);
            shell.setTop(global);
            Stage stage = new Stage();
            stage.setScene(new Scene(shell, 900, 500));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            TextArea notes = new TextArea("Keep this draft");
            Dockable editor = new Dockable(new DockableId("notes"), notes);
            editor.setTitle("Notes");
            TextArea project = new TextArea("Project");
            Dockable tools = new Dockable(new DockableId("project"), project);
            tools.setTitle("Project");
            manager.open(editor, DockTarget.area("editors"));
            manager.open(tools, DockTarget.area("tools"));
            stage.show();
            try {
                shell.applyCss();
                shell.layout();
                Node header = header(pane, "Notes");
                Bounds from = header.localToScreen(header.getBoundsInLocal());
                Bounds into = project.localToScreen(project.getBoundsInLocal());
                var original = manager.layout();
                mouse(header, MouseEvent.MOUSE_PRESSED, from.getCenterX(), from.getCenterY(), true);
                mouse(header, MouseEvent.MOUSE_DRAGGED, into.getCenterX(), into.getCenterY(), true);
                javafx.event.Event.fireEvent(stage.getScene(), new KeyEvent(KeyEvent.KEY_PRESSED,
                        "", "", KeyCode.ESCAPE, false, false, false, false));
                mouse(header, MouseEvent.MOUSE_RELEASED, into.getCenterX(), into.getCenterY(), false);
                assertEquals(original, manager.layout());
                assertFalse(pane.lookup(".dock-drop-indicator").isVisible());

                Bounds invalid = global.localToScreen(global.getBoundsInLocal());
                mouse(header, MouseEvent.MOUSE_PRESSED, from.getCenterX(), from.getCenterY(), true);
                mouse(header, MouseEvent.MOUSE_DRAGGED, invalid.getCenterX(), invalid.getCenterY(), true);
                mouse(header, MouseEvent.MOUSE_RELEASED, invalid.getCenterX(), invalid.getCenterY(), false);
                assertEquals(original, manager.layout());
                assertFalse(pane.lookup(".dock-drop-indicator").isVisible());
                assertEquals("Keep this draft", notes.getText());
            } finally {
                stage.hide();
            }
        });
    }

    @Test
    void moveMenuUsesCurrentTitlesAndExplainsRejectedDestinations() throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            initial.setRight(new DockGroupDefinition("tools"));
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 900, 500));
            AtomicBoolean allowed = new AtomicBoolean(true);
            DockingOptions options = new DockingOptions();
            options.setPlacementRule((dockable, target) -> allowed.get() ? Optional.empty()
                    : Optional.of(new DockFailure(DockFailure.Code.PLACEMENT_REJECTED,
                    dockable.getId(), "Tools are locked", null)));
            DockingManager manager = DockingManager.attach(stage, pane, options);
            TextArea notes = new TextArea("Notes");
            Dockable editor = new Dockable(new DockableId("notes"), notes);
            editor.setTitle("Notes");
            Dockable project = new Dockable(new DockableId("project"), new TextArea("Project"));
            project.setTitle("Project");
            manager.open(editor, DockTarget.area("editors"));
            manager.open(project, DockTarget.area("tools"));
            stage.show();
            ContextMenu menu = null;
            try {
                pane.applyCss();
                pane.layout();
                var tab = pane.lookupAll(".tab-pane").stream().map(TabPane.class::cast)
                        .flatMap(tabs -> tabs.getTabs().stream()).filter(item -> item.getContent() == notes)
                        .findFirst().orElseThrow();
                menu = tab.getContextMenu();
                assertNotNull(menu);
                project.setTitle("Changed files");
                allowed.set(false);
                menu.show(stage, stage.getX() + 80, stage.getY() + 80);
                Menu destinations = (Menu) menu.getItems().getFirst();
                MenuItem tools = destinations.getItems().stream()
                        .filter(item -> item.getText().contains("tools")).findFirst().orElseThrow();
                assertTrue(tools.getText().contains("Changed files"));
                assertTrue(tools.getText().contains("Tools are locked"));
                assertTrue(tools.isDisable());
                menu.hide();
                allowed.set(true);
                menu.show(stage, stage.getX() + 80, stage.getY() + 80);
                tools = destinations.getItems().stream()
                        .filter(item -> item.getText().contains("tools")).findFirst().orElseThrow();
                assertFalse(tools.isDisable());
                tools.fire();
                assertEquals(List.of(project.getId(), editor.getId()), manager.layout().windows().getFirst()
                        .areas().get(1).groups().getFirst().dockableIds());
            } finally {
                if (menu != null) menu.hide();
                stage.hide();
            }
        });
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    void aDropBeforeTheOnlyTargetTabUsesVisualLeadingEdge(Side side) throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            DockGroupDefinition tools = new DockGroupDefinition("tools");
            tools.setTabSide(side);
            initial.setRight(tools);
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 900, 500));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            Dockable notes = new Dockable(new DockableId("notes"), new TextArea("Notes"));
            notes.setTitle("Notes");
            Dockable project = new Dockable(new DockableId("project"), new TextArea("Project"));
            project.setTitle("Project");
            manager.open(notes, DockTarget.area("editors"));
            manager.open(project, DockTarget.area("tools"));
            stage.show();
            try {
                pane.applyCss();
                pane.layout();
                Node source = header(pane, "Notes");
                Bounds from = source.localToScreen(source.getBoundsInLocal());
                Node target = header(pane, "Project");
                Bounds into = target.localToScreen(target.getBoundsInLocal());
                double x = into.getCenterX() - (side.isHorizontal() ? into.getWidth() / 3 : 0);
                double y = into.getCenterY() - (side.isHorizontal() ? 0 : into.getHeight() / 3);
                mouse(source, MouseEvent.MOUSE_PRESSED, from.getCenterX(), from.getCenterY(), true);
                mouse(source, MouseEvent.MOUSE_DRAGGED, x, y, true);
                mouse(source, MouseEvent.MOUSE_RELEASED, x, y, false);
                assertEquals(List.of(notes.getId(), project.getId()), manager.layout().windows().getFirst()
                        .areas().get(1).groups().getFirst().dockableIds());
            } finally {
                stage.hide();
            }
        });
    }

    private static Node header(DockPane pane, String title) {
        return pane.lookupAll(".tab").stream().filter(node -> {
            Node label = node.lookup(".tab-label");
            return label instanceof Label text && title.equals(text.getText());
        }).findFirst().orElseThrow();
    }

    private static void mouse(Node source, javafx.event.EventType<MouseEvent> type,
                              double screenX, double screenY, boolean down) {
        var local = source.screenToLocal(screenX, screenY);
        source.fireEvent(new MouseEvent(type, local.getX(), local.getY(), screenX, screenY,
                MouseButton.PRIMARY, 1, false, false, false, false,
                down, false, false, false, false, false,
                new PickResult(source, local.getX(), local.getY())));
    }
}
