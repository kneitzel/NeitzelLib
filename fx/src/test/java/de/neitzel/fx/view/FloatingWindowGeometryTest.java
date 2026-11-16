package de.neitzel.fx.view;

import de.neitzel.fx.docking.DockGroupDefinition;
import de.neitzel.fx.docking.DockGroupChanges;
import de.neitzel.fx.docking.DockLayoutView;
import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.control.SplitPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Public geometry observations; native pointer movement requires an exclusive desktop opt-in. */
class FloatingWindowGeometryTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @Test
    void theLayoutExposesFloatingBoundsAndLeavesMainWindowGeometryToTheApplication() throws Exception {
        Fixture fixture = fixture();
        try {
            FxThread.onFx(() -> {
                assertNull(fixture.manager.layout().windows().stream().filter(DockLayoutView.Window::main)
                        .findFirst().orElseThrow().bounds());
                assertEquals(actual(fixture.floating), published(fixture.manager));
            });
        } finally { FxThread.onFx(fixture::close); }
    }

    @Test
    @EnabledIfSystemProperty(named = "neitzellib.nativeGeometryTest", matches = "true")
    void nativeTitlebarAndBorderGesturesPublishOnlyAfterReleaseEvenWithAHeldPause() throws Exception {
        Fixture fixture = fixture();
        Robot robot = new Robot();
        robot.setAutoDelay(15);
        Point original = MouseInfo.getPointerInfo().getLocation();
        var watchdog = Executors.newSingleThreadScheduledExecutor();
        watchdog.schedule(() -> {
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseMove(original.x, original.y);
        }, 15, TimeUnit.SECONDS);
        try {
            Thread.sleep(400);
            FxThread.onFx(fixture.committed::clear);
            for (boolean border : List.of(false, true)) {
                String gesture = border ? "right-edge resize" : "titlebar drag";
                AtomicReference<Rectangle2D> before = new AtomicReference<>();
                AtomicReference<Point> start = new AtomicReference<>();
                FxThread.onFx(() -> {
                    before.set(published(fixture.manager));
                    Rectangle2D frame = actual(fixture.floating);
                    double titleHeight = fixture.floating.getScene().getRoot().localToScreen(0, 0).getY() - frame.getMinY();
                    start.set(border ? rightEdgeMidpoint(frame)
                            : new Point((int) (frame.getMinX() + frame.getWidth() / 2),
                                    (int) (frame.getMinY() + titleHeight / 2)));
                });
                robot.mouseMove(start.get().x, start.get().y);
                Thread.sleep(100);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                try {
                    for (int step = 1; step <= 8; step++) {
                        robot.mouseMove(start.get().x + step * 8, start.get().y + (border ? 0 : step * 5));
                        Thread.sleep(35);
                    }
                    Thread.sleep(600);
                    FxThread.onFx(() -> {
                        assertFalse(before.get().equals(actual(fixture.floating)),
                                "Native " + gesture + " must change the real frame; start=" + start.get()
                                        + ", before=" + before.get() + ", actual=" + actual(fixture.floating)
                                        + ", focused=" + fixture.floating.isFocused());
                        assertEquals(before.get(), published(fixture.manager), "A held " + gesture + " pause is not gesture completion");
                    });
                    robot.mouseMove(start.get().x + 88, start.get().y + (border ? 0 : 55));
                    Thread.sleep(100);
                } finally {
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                }
                awaitPublishedFrame(fixture, before.get());
            }
            FxThread.onFx(() -> assertEquals(2, fixture.committed.size(), "One completed publication per native gesture"));
        } finally {
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseMove(original.x, original.y);
            watchdog.shutdownNow();
            FxThread.onFx(fixture::close);
        }
    }

    private static Point rightEdgeMidpoint(Rectangle2D frame) {
        // Avoid the rounded bottom corner, which can fall outside the native resize hit region.
        return new Point((int) frame.getMaxX() - 1, (int) (frame.getMinY() + frame.getHeight() / 2));
    }

    private static void awaitPublishedFrame(Fixture fixture, Rectangle2D previous) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        AtomicReference<Boolean> complete = new AtomicReference<>(false);
        do {
            Thread.sleep(25);
            FxThread.onFx(() -> complete.set(!published(fixture.manager).equals(previous)
                    && published(fixture.manager).equals(actual(fixture.floating))));
        } while (!complete.get() && System.nanoTime() < deadline);
        assertTrue(complete.get(), "Released native geometry must reach the public layout");
    }

    @Test
    @EnabledIfSystemProperty(named = "neitzellib.nativeGeometryTest", matches = "true")
    void anotherPublicationKeepsSplitGeometryCommittedDuringNativeResize() throws Exception {
        Fixture fixture = fixture();
        Robot robot = new Robot();
        robot.setAutoDelay(15);
        Point original = MouseInfo.getPointerInfo().getLocation();
        var watchdog = Executors.newSingleThreadScheduledExecutor();
        watchdog.schedule(() -> {
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseMove(original.x, original.y);
        }, 15, TimeUnit.SECONDS);
        AtomicReference<Rectangle2D> before = new AtomicReference<>();
        AtomicReference<Double> ratio = new AtomicReference<>();
        AtomicReference<Point> start = new AtomicReference<>();
        try {
            FxThread.onFx(() -> fixture.floating.setWidth(760));
            awaitClientWidth(fixture.floating, 740);
            FxThread.onFx(() -> {
                var window = fixture.manager.layout().windows().stream().filter(candidate -> !candidate.main()).findFirst().orElseThrow();
                var group = window.areas().getFirst().groups().getFirst().id();
                Dockable anchor = new Dockable(new DockableId("anchor"), new TextArea("Anchor"));
                fixture.manager.open(anchor, DockTarget.group(group));
                fixture.manager.move(anchor.getId(), DockTarget.split(group, Side.RIGHT));
                // Make this minimum active at the starting width as well: even a short native
                // shrink must change the live ratio before testing committed publication.
                fixture.manager.updateGroup(group, DockGroupChanges.builder().minimumWidth(500).build());
            });
            Thread.sleep(400);
            FxThread.onFx(() -> {
                before.set(published(fixture.manager));
                ratio.set(publishedSplit(fixture.manager).position());
                Rectangle2D frame = actual(fixture.floating);
                start.set(rightEdgeMidpoint(frame));
            });
            robot.mouseMove(start.get().x, start.get().y);
            Thread.sleep(100);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            try {
                for (int step = 1; step <= 10; step++) {
                    robot.mouseMove(start.get().x - step * 34, start.get().y);
                    Thread.sleep(35);
                }
                Thread.sleep(600);
                FxThread.onFx(() -> {
                    SplitPane split = (SplitPane) fixture.floating.getScene().getRoot().lookup(".dock-split");
                    double live = split.getDividers().getFirst().getPosition();
                    assertTrue(Math.abs(live - ratio.get()) > .03,
                            "Unequal minima must change the live split ratio: prior=" + ratio.get() + ", live=" + live
                                    + ", prior frame=" + before.get() + ", actual=" + actual(fixture.floating)
                                    + ", split width=" + split.getWidth() + ", first minimum=" + split.getItems().getFirst().minWidth(-1));
                    assertEquals(before.get(), published(fixture.manager));
                    fixture.manager.activate(new DockableId("notes"));
                    assertEquals(before.get(), published(fixture.manager), "Selection publication must retain committed bounds");
                    assertEquals(ratio.get(), publishedSplit(fixture.manager).position(), 1e-9,
                            "Selection publication must retain committed split geometry during the same native gesture");
                });
            } finally { robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); }
            awaitPublishedFrame(fixture, before.get());
            FxThread.onFx(() -> {
                SplitPane split = (SplitPane) fixture.floating.getScene().getRoot().lookup(".dock-split");
                assertTrue(Math.abs(publishedSplit(fixture.manager).position() - ratio.get()) > .03);
                assertEquals(split.getDividers().getFirst().getPosition(), publishedSplit(fixture.manager).position(), 1e-9);
            });
        } finally {
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseMove(original.x, original.y);
            watchdog.shutdownNow();
            FxThread.onFx(fixture::close);
        }
    }

    private static void awaitClientWidth(Stage stage, double minimum) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        AtomicReference<Boolean> ready = new AtomicReference<>(false);
        do {
            Thread.sleep(25);
            FxThread.onFx(() -> ready.set(stage.getScene().getWidth() >= minimum
                    && stage.getScene().getRoot().getLayoutBounds().getWidth() >= minimum));
        } while (!ready.get() && System.nanoTime() < deadline);
        assertTrue(ready.get(), "The native window's client layout must reach the starting width before splitting");
    }

    private static DockLayoutView.Split publishedSplit(DockingManager manager) {
        var area = manager.layout().windows().stream().filter(window -> !window.main()).findFirst().orElseThrow().areas().getFirst();
        return assertInstanceOf(DockLayoutView.Split.class, area.root());
    }

    private static Fixture fixture() throws Exception {
        AtomicReference<Fixture> result = new AtomicReference<>();
        FxThread.onFx(() -> {
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 800, 500));
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            Dockable notes = new Dockable(new DockableId("notes"), new TextArea("Unsaved notes"));
            manager.open(notes, DockTarget.area("editors"));
            main.show();
            manager.move(notes.getId(), DockTarget.floating(new Rectangle2D(200, 180, 540, 360)));
            Stage floating = (Stage) notes.getContent().getScene().getWindow();
            List<Rectangle2D> committed = new ArrayList<>();
            manager.layoutProperty().addListener((observable, old, current) -> {
                Rectangle2D prior = old.windows().stream().filter(window -> !window.main()).findFirst().orElseThrow().bounds();
                Rectangle2D next = current.windows().stream().filter(window -> !window.main()).findFirst().orElseThrow().bounds();
                if (!java.util.Objects.equals(prior, next)) committed.add(next);
            });
            result.set(new Fixture(main, floating, manager, committed));
        });
        return result.get();
    }

    private static Rectangle2D published(DockingManager manager) {
        return manager.layout().windows().stream().filter(window -> !window.main()).findFirst().orElseThrow().bounds();
    }

    private static Rectangle2D actual(Stage stage) {
        return new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
    }

    private record Fixture(Stage main, Stage floating, DockingManager manager, List<Rectangle2D> committed) {
        void close() { floating.hide(); main.hide(); }
    }
}
