package de.neitzel.fx.view;

import de.neitzel.fx.docking.DockGroupDefinition;
import de.neitzel.fx.docking.DockGroupId;
import de.neitzel.fx.docking.DockLayout;
import de.neitzel.fx.docking.DockLayoutView;
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
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A collapsed tool rail is a destination without opening merely for a preview. */
class CollapsedDockDropTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @ParameterizedTest
    @EnumSource(value = Side.class, names = {"LEFT", "RIGHT"})
    void aPreviewAndEscapeKeepTheRailCollapsedButDroppingOpensItsRememberedWidth(Side side) throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            DockGroupDefinition tools = new DockGroupDefinition("tools");
            tools.setCollapsible(true);
            tools.setTabSide(side);
            tools.setPreferredWidth(240);
            if (side == Side.LEFT) initial.setLeft(tools);
            else initial.setRight(tools);
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 900, 500));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            Dockable notes = new Dockable(new DockableId("notes"), new TextArea("Unsaved notes"));
            notes.setTitle("Notes");
            Dockable project = new Dockable(new DockableId("project"), new TextArea("Project"));
            project.setTitle("Project");
            manager.open(notes, DockTarget.area("editors"));
            manager.open(project, DockTarget.area("tools"));
            stage.show();
            try {
                pane.applyCss();
                pane.layout();
                TabPane target = tabs(pane, project.getId());
                double expandedWidth = target.getWidth();
                manager.setGroupCollapsed(new DockGroupId("tools"), true);
                pane.applyCss();
                pane.layout();
                DockLayoutView collapsed = manager.layout();
                assertTrue(group(manager).collapsed());
                assertTrue(target.getWidth() < expandedWidth / 2);
                Node sourceHeader = header(tabs(pane, notes.getId()), notes.getId());
                Node targetHeader = header(target, project.getId());
                Bounds from = sourceHeader.localToScreen(sourceHeader.getBoundsInLocal());
                Bounds into = targetHeader.localToScreen(targetHeader.getBoundsInLocal());
                mouse(sourceHeader, MouseEvent.MOUSE_PRESSED, from.getCenterX(), from.getCenterY(), true);
                mouse(sourceHeader, MouseEvent.MOUSE_DRAGGED, into.getCenterX(), into.getCenterY(), true);
                assertTrue(pane.lookup(".dock-drop-indicator").isVisible());
                assertEquals(collapsed, manager.layout());
                assertTrue(group(manager).collapsed());
                assertFalse(target.lookup(".tab-content-area").isVisible());
                stage.getScene().getRoot().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                        false, false, false, false));
                assertFalse(pane.lookup(".dock-drop-indicator").isVisible());
                mouse(sourceHeader, MouseEvent.MOUSE_RELEASED, into.getCenterX(), into.getCenterY(), false);
                assertEquals(collapsed, manager.layout());
                assertTrue(group(manager).collapsed());

                mouse(sourceHeader, MouseEvent.MOUSE_PRESSED, from.getCenterX(), from.getCenterY(), true);
                mouse(sourceHeader, MouseEvent.MOUSE_DRAGGED, into.getCenterX(), into.getCenterY(), true);
                mouse(sourceHeader, MouseEvent.MOUSE_RELEASED, into.getCenterX(), into.getCenterY(), false);
                pane.applyCss();
                pane.layout();
                assertFalse(group(manager).collapsed());
                assertEquals(notes.getId(), group(manager).selectedDockableId());
                assertSame(notes, manager.activeDockable());
                assertSame(notes.getContent(), target.getSelectionModel().getSelectedItem().getContent());
                assertEquals(expandedWidth, target.getWidth(), 1);
                assertTrue(target.lookup(".tab-content-area").isVisible());
                assertTrue(notes.getContent().isVisible());
            } finally { stage.hide(); }
        });
    }

    private static DockLayoutView.Group group(DockingManager manager) {
        return manager.layout().windows().getFirst().areas().stream()
                .filter(area -> area.id().value().equals("tools")).findFirst().orElseThrow().groups().getFirst();
    }

    private static TabPane tabs(DockPane pane, DockableId id) {
        return pane.lookupAll(".tab-pane").stream().map(TabPane.class::cast)
                .filter(tabs -> tabs.getTabs().stream().anyMatch(tab -> id.equals(tab.getUserData())))
                .findFirst().orElseThrow();
    }

    private static Node header(TabPane tabs, DockableId id) {
        return tabs.lookup("#" + tabs.getTabs().stream().filter(tab -> id.equals(tab.getUserData()))
                .findFirst().orElseThrow().getId());
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
