package de.neitzel.fx.view;

import de.neitzel.fx.docking.DockGroupDefinition;
import de.neitzel.fx.docking.DockGroupMode;
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

/** An overlay rail previews a drop without changing which overlay is open. */
class OverlayDockDropTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    void onlyACompletedDropOpensTheTargetOverlayAndCollapsesThePriorOverlay(Side side) throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            DockGroupDefinition tools = new DockGroupDefinition("tools");
            tools.setCollapsible(true);
            tools.setMode(DockGroupMode.OVERLAY);
            tools.setTabSide(side);
            tools.setPreferredWidth(240);
            tools.setPreferredHeight(190);
            DockGroupDefinition prior = new DockGroupDefinition("prior");
            prior.setCollapsible(true);
            prior.setMode(DockGroupMode.OVERLAY);
            Side opposite = switch (side) {
                case LEFT -> Side.RIGHT;
                case RIGHT -> Side.LEFT;
                case TOP -> Side.BOTTOM;
                case BOTTOM -> Side.TOP;
            };
            prior.setTabSide(opposite);
            prior.setPreferredWidth(210);
            prior.setPreferredHeight(170);
            edge(initial, side, tools);
            edge(initial, opposite, prior);
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 900, 600));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            Dockable notes = new Dockable(new DockableId("notes"), new TextArea("Unsaved notes"));
            notes.setTitle("Notes");
            Dockable project = new Dockable(new DockableId("project"), new TextArea("Project"));
            project.setTitle("Project");
            Dockable spare = new Dockable(new DockableId("spare"), new TextArea("Spare"));
            spare.setTitle("Spare");
            manager.open(new Dockable(new DockableId("editor"), new TextArea("Editor")), DockTarget.area("editors"));
            manager.open(project, DockTarget.area("tools"));
            stage.show();
            try {
                pane.applyCss();
                pane.layout();
                TabPane target = tabs(pane, project.getId());
                double expandedExtent = extent(target, side);
                assertEquals(side.isVertical() ? 240 : 190, expandedExtent, 1);
                manager.open(spare, DockTarget.area("prior"));
                manager.open(notes, DockTarget.area("prior"));
                pane.applyCss();
                pane.layout();
                DockLayoutView collapsed = manager.layout();
                assertTrue(group(manager).collapsed());
                assertFalse(group(manager, "prior").collapsed());
                assertEquals(DockGroupMode.OVERLAY, group(manager).mode());
                assertTrue(extent(target, side) < expandedExtent / 2);
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
                sourceHeader.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
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
                assertEquals(expandedExtent, extent(target, side), 1);
                assertEquals(DockGroupMode.OVERLAY, group(manager).mode());
                assertEquals(DockGroupMode.OVERLAY, group(manager, "prior").mode());
                assertTrue(group(manager, "prior").collapsed());
                assertEquals(java.util.List.of(spare.getId()), group(manager, "prior").dockableIds());
                assertFalse(tabs(pane, spare.getId()).lookup(".tab-content-area").isVisible());
                assertTrue(target.lookup(".tab-content-area").isVisible());
                assertTrue(notes.getContent().isVisible());
            } finally { stage.hide(); }
        });
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    void expandedOverlayContentReceivesDropsAboveTheUnderlyingEditor(Side side) throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            DockGroupDefinition tools = new DockGroupDefinition("tools");
            tools.setCollapsible(true);
            tools.setMode(DockGroupMode.OVERLAY);
            tools.setTabSide(side);
            tools.setPreferredWidth(240);
            tools.setPreferredHeight(190);
            tools.setSplitDirections(de.neitzel.fx.docking.SplitDirections.NONE);
            edge(initial, side, tools);
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 900, 600));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            Dockable editor = new Dockable(new DockableId("editor"), new TextArea("Editor"));
            Dockable first = new Dockable(new DockableId("first"), new TextArea("Unsaved first"));
            first.setTitle("First");
            Dockable second = new Dockable(new DockableId("second"), new TextArea("Second"));
            second.setTitle("Second");
            manager.open(editor, DockTarget.area("editors"));
            manager.open(first, DockTarget.area("tools"));
            manager.open(second, DockTarget.area("tools"));
            stage.show();
            try {
                pane.applyCss();
                pane.layout();
                TabPane overlay = tabs(pane, first.getId());
                Bounds into = second.getContent().localToScreen(second.getContent().getBoundsInLocal());
                double x = into.getCenterX();
                double y = into.getCenterY();
                Bounds underneath = tabs(pane, editor.getId()).localToScreen(
                        tabs(pane, editor.getId()).getBoundsInLocal());
                assertTrue(underneath.contains(x, y), "The visible overlay content covers the editor drop surface");
                assertFalse(group(manager).collapsed());
                Node sourceHeader = header(overlay, first.getId());
                Bounds from = sourceHeader.localToScreen(sourceHeader.getBoundsInLocal());
                mouse(sourceHeader, MouseEvent.MOUSE_PRESSED, from.getCenterX(), from.getCenterY(), true);
                mouse(sourceHeader, MouseEvent.MOUSE_DRAGGED, x, y, true);
                assertTrue(pane.lookup(".dock-drop-indicator").isVisible());
                mouse(sourceHeader, MouseEvent.MOUSE_RELEASED, x, y, false);
                assertEquals(java.util.List.of(second.getId(), first.getId()), group(manager).dockableIds(),
                        "A drop on overlay content appends within that visible group");
                assertEquals(java.util.List.of(editor.getId()), group(manager, "editors").dockableIds());
                assertSame(first, manager.activeDockable());
                assertSame(first.getContent(), overlay.getSelectionModel().getSelectedItem().getContent());
                assertFalse(group(manager).collapsed());
            } finally { stage.hide(); }
        });
    }

    private static void edge(DockLayout layout, Side side, DockGroupDefinition group) {
        switch (side) {
            case LEFT -> layout.setLeft(group);
            case RIGHT -> layout.setRight(group);
            case TOP -> layout.setTop(group);
            case BOTTOM -> layout.setBottom(group);
        }
    }

    private static double extent(TabPane tabs, Side side) {
        return side.isVertical() ? tabs.getWidth() : tabs.getHeight();
    }

    private static DockLayoutView.Group group(DockingManager manager) { return group(manager, "tools"); }

    private static DockLayoutView.Group group(DockingManager manager, String name) {
        return manager.layout().windows().getFirst().areas().stream()
                .filter(area -> area.id().value().equals(name)).findFirst().orElseThrow().groups().getFirst();
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
