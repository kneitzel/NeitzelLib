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
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The public surface follows a boundary drag before publishing its finished layout. */
class OuterDockResizeTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    void sideBoundaryResizesLiveAndPublishesOnceOnRelease(Side side) throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            DockGroupDefinition editors = new DockGroupDefinition("editors");
            editors.setMinimumWidth(180);
            editors.setMinimumHeight(120);
            initial.setCenter(editors);
            DockGroupDefinition tools = new DockGroupDefinition("tools");
            tools.setMinimumWidth(140);
            tools.setMinimumHeight(100);
            switch (side) {
                case LEFT -> initial.setLeft(tools);
                case RIGHT -> initial.setRight(tools);
                case TOP -> initial.setTop(tools);
                case BOTTOM -> initial.setBottom(tools);
            }
            if (side.isHorizontal()) {
                DockGroupDefinition navigation = new DockGroupDefinition("navigation");
                navigation.setMinimumHeight(260);
                initial.setLeft(navigation);
            }
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 900, 650));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            TextArea project = new TextArea("Project");
            TextArea notes = new TextArea("Notes");
            Dockable dockable = new Dockable(new DockableId("project"), project);
            manager.open(new Dockable(new DockableId("notes"), notes), DockTarget.area("editors"));
            manager.open(dockable, DockTarget.area("tools"));
            stage.show();
            try {
                pane.applyCss();
                pane.layout();
                Node divider = tabPane(pane, project).getParent().lookup(".dock-area-divider");
                assertNotNull(divider, "A predefined side area needs a draggable boundary");
                assertEquals(side.isVertical() ? Cursor.H_RESIZE : Cursor.V_RESIZE, divider.getCursor());
                var origin = divider.localToScreen(divider.getBoundsInLocal());
                double oldSize = side.isVertical() ? project.getWidth() : project.getHeight();
                var before = manager.layout();
                AtomicInteger publications = new AtomicInteger();
                manager.layoutProperty().addListener((observable, old, value) -> publications.incrementAndGet());
                double dx = side == Side.LEFT ? 40 : side == Side.RIGHT ? -40 : 0;
                double dy = side == Side.TOP ? 40 : side == Side.BOTTOM ? -40 : 0;
                mouse(divider, MouseEvent.MOUSE_PRESSED, origin.getCenterX(), origin.getCenterY(), true);
                mouse(divider, MouseEvent.MOUSE_DRAGGED, origin.getCenterX() + dx, origin.getCenterY() + dy, true);
                pane.layout();
                assertTrue((side.isVertical() ? project.getWidth() : project.getHeight()) > oldSize + 30);
                assertSame(before, manager.layout());
                assertEquals(0, publications.get());
                mouse(divider, MouseEvent.MOUSE_RELEASED, origin.getCenterX() + dx, origin.getCenterY() + dy, false);
                assertNotSame(before, manager.layout());
                assertEquals(1, publications.get());
                var beforeArea = before.windows().getFirst().areas().stream()
                        .filter(area -> area.id().value().equals("tools")).findFirst().orElseThrow();
                var afterArea = manager.layout().windows().getFirst().areas().stream()
                        .filter(area -> area.id().value().equals("tools")).findFirst().orElseThrow();
                assertEquals(40, side.isVertical()
                        ? afterArea.preferredWidth() - beforeArea.preferredWidth()
                        : afterArea.preferredHeight() - beforeArea.preferredHeight(), .1);
                assertSame(project, manager.findDockable(dockable.getId()).orElseThrow().getContent());
                var consent = new java.util.concurrent.CompletableFuture<de.neitzel.fx.docking.CloseConsent>();
                dockable.setCloseCheck(request -> consent);
                var closing = manager.requestTabClose(dockable.getId());
                var protectedLayout = manager.layout();
                var protectedBoundary = divider.localToScreen(divider.getBoundsInLocal());
                double protectedSize = side.isVertical() ? project.getWidth() : project.getHeight();
                mouse(divider, MouseEvent.MOUSE_PRESSED, protectedBoundary.getCenterX(), protectedBoundary.getCenterY(), true);
                mouse(divider, MouseEvent.MOUSE_DRAGGED, protectedBoundary.getCenterX() + dx,
                        protectedBoundary.getCenterY() + dy, true);
                pane.layout();
                mouse(divider, MouseEvent.MOUSE_RELEASED, protectedBoundary.getCenterX() + dx,
                        protectedBoundary.getCenterY() + dy, false);
                assertEquals(protectedSize, side.isVertical() ? project.getWidth() : project.getHeight(), .1);
                assertSame(protectedLayout, manager.layout());
                consent.complete(de.neitzel.fx.docking.CloseConsent.VETO);
                assertTrue(closing.toCompletableFuture().isDone());
                var centerConsent = new java.util.concurrent.CompletableFuture<de.neitzel.fx.docking.CloseConsent>();
                var centerDockable = manager.findDockable(new DockableId("notes")).orElseThrow();
                centerDockable.setCloseCheck(request -> centerConsent);
                manager.requestTabClose(centerDockable.getId());
                mouse(divider, MouseEvent.MOUSE_PRESSED, protectedBoundary.getCenterX(), protectedBoundary.getCenterY(), true);
                mouse(divider, MouseEvent.MOUSE_DRAGGED, protectedBoundary.getCenterX() + dx,
                        protectedBoundary.getCenterY() + dy, true);
                pane.layout();
                mouse(divider, MouseEvent.MOUSE_RELEASED, protectedBoundary.getCenterX() + dx,
                        protectedBoundary.getCenterY() + dy, false);
                assertEquals(protectedSize, side.isVertical() ? project.getWidth() : project.getHeight(), .1,
                        "A side resize must protect the dependent center area too");
                centerConsent.complete(de.neitzel.fx.docking.CloseConsent.VETO);
                for (int direction : new int[]{1, -1}) {
                    var boundary = divider.localToScreen(divider.getBoundsInLocal());
                    mouse(divider, MouseEvent.MOUSE_PRESSED, boundary.getCenterX(), boundary.getCenterY(), true);
                    mouse(divider, MouseEvent.MOUSE_DRAGGED, boundary.getCenterX() + dx * direction * 100,
                            boundary.getCenterY() + dy * direction * 100, true);
                    pane.layout();
                    var toolsPane = tabPane(pane, project);
                    var editorPane = tabPane(pane, notes);
                    assertTrue((side.isVertical() ? toolsPane.getWidth() : toolsPane.getHeight())
                            >= (side.isVertical() ? tools.getMinimumWidth() : tools.getMinimumHeight()) - .1);
                    assertTrue((side.isVertical() ? editorPane.getWidth() : editorPane.getHeight())
                            >= (side.isVertical() ? editors.getMinimumWidth() : 260) - .1,
                            "Resizing must retain the minimum of the entire middle row");
                    var available = pane.localToScene(pane.getLayoutBounds());
                    var editorBounds = editorPane.localToScene(editorPane.getLayoutBounds());
                    var toolsBounds = toolsPane.localToScene(toolsPane.getLayoutBounds());
                    assertTrue(editorBounds.getMaxX() <= available.getMaxX() + 1
                            && editorBounds.getMaxY() <= available.getMaxY() + 1
                            && toolsBounds.getMaxX() <= available.getMaxX() + 1
                            && toolsBounds.getMaxY() <= available.getMaxY() + 1,
                            "The remaining area must stay inside the docking pane");
                    mouse(divider, MouseEvent.MOUSE_RELEASED, boundary.getCenterX() + dx * direction * 100,
                            boundary.getCenterY() + dy * direction * 100, false);
                    var finished = manager.layout().windows().getFirst().areas().stream()
                            .filter(area -> area.id().value().equals("tools")).findFirst().orElseThrow();
                    assertEquals(side.isVertical() ? toolsPane.getWidth() : toolsPane.getHeight(),
                            side.isVertical() ? finished.preferredWidth() : finished.preferredHeight(), 1,
                            "Published geometry must match the clamped visible size");
                }
            } finally {
                stage.hide();
            }
        });
    }

    private static javafx.scene.control.TabPane tabPane(DockPane pane, Node content) {
        return pane.lookupAll(".tab-pane").stream().map(javafx.scene.control.TabPane.class::cast)
                .filter(tabs -> tabs.getTabs().stream().anyMatch(tab -> tab.getContent() == content))
                .findFirst().orElseThrow();
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
