package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Desktop gestures and actual text delivery; no synthesized JavaFX input events. */
@EnabledIfSystemProperty(named = "neitzellib.nativeDragTest", matches = "true")
class NativeDockDragTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @Test
    @Timeout(25)
    void draggingOutsideAndBackPreservesTheEditorAndNativeTypingAtItsSelection() throws Exception {
        Fixture f = fixture();
        Robot robot = new Robot();
        robot.setAutoDelay(15);
        Point original = MouseInfo.getPointerInfo().getLocation();
        var watchdog = Executors.newSingleThreadScheduledExecutor();
        watchdog.schedule(() -> robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK), 20, TimeUnit.SECONDS);
        try {
            NativeFixtureFocus.establish(robot, f.notes);
            await(() -> f.main.isFocused(), "Initial main window must be focused");
            Point outside = onFx(() -> {
                var screen = Screen.getPrimary().getVisualBounds();
                return new Point((int) screen.getMaxX() - 100, (int) (screen.getMinY() + 180));
            });
            var initial = onFx(f.manager::layout);
            drag(robot, onFx(() -> headerPoint(f.notes)), outside, () -> FxThread.onFx(() -> {
                assertTrue(floatingPreviewVisible(), "Outside a managed window the proposed floating bounds must be visible");
                assertSame(initial, f.manager.layout(), "A floating preview cannot allocate a managed window");
            }));
            await(() -> f.notes.getScene().getWindow() != f.main, "Dropping outside must create a floating window");
            Window floating = onFx(() -> f.notes.getScene().getWindow());
            type(robot, f.notes, KeyEvent.VK_X, "abxef");
            Point mainTarget = onFx(() -> center(f.pane));
            drag(robot, onFx(() -> headerPoint(f.notes)), mainTarget);
            await(() -> f.notes.getScene().getWindow() == f.main, "Dropping into the main area must return the editor");
            type(robot, f.notes, KeyEvent.VK_Y, "abxyef");
            FxThread.onFx(() -> {
                assertFalse(floating.isShowing(), "The empty source window disappears");
                assertSame(f.notes, f.manager.findDockable(new DockableId("notes")).orElseThrow().getContent());
                assertEquals(1, f.manager.layout().windows().size());
            });
            var returned = onFx(f.manager::layout);
            drag(robot, onFx(() -> headerPoint(f.notes)), outside, () -> {
                FxThread.onFx(() -> assertTrue(floatingPreviewVisible()));
                try { robot.keyPress(KeyEvent.VK_ESCAPE); }
                finally { robot.keyRelease(KeyEvent.VK_ESCAPE); }
                await(() -> !floatingPreviewVisible(), "Escape must hide the floating preview");
            });
            FxThread.onFx(() -> assertSame(returned, f.manager.layout()));
            type(robot, f.notes, KeyEvent.VK_Z, "abxyzef");
            FxThread.onFx(() -> f.manager.findDockable(new DockableId("notes")).orElseThrow().setMainWindowOnly(true));
            drag(robot, onFx(() -> headerPoint(f.notes)), outside,
                    () -> FxThread.onFx(() -> assertFalse(floatingPreviewVisible(), "Main-window-only content has no floating preview")));
            FxThread.onFx(() -> assertSame(returned, f.manager.layout(), "Main-window-only cannot leave the main window"));
            type(robot, f.notes, KeyEvent.VK_A, "abxyzaef");
        } finally {
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            for (int key : new int[]{KeyEvent.VK_ESCAPE, KeyEvent.VK_X, KeyEvent.VK_Y, KeyEvent.VK_Z, KeyEvent.VK_A}) robot.keyRelease(key);
            robot.mouseMove(original.x, original.y);
            watchdog.shutdownNow();
            FxThread.onFx(f::close);
        }
    }

    private static boolean floatingPreviewVisible() {
        return Window.getWindows().stream().filter(Window::isShowing).anyMatch(window ->
                window.getScene() != null && window.getScene().lookup(".dock-floating-preview") != null);
    }

    private static void drag(Robot robot, Point from, Point to) throws Exception {
        drag(robot, from, to, () -> { });
    }

    @Test
    @Timeout(30)
    void nativeCrossWindowPreviewInvalidDropAndEscapePreserveTypingWithoutExtraClicks() throws Exception {
        Fixture f = fixture();
        Robot robot = new Robot();
        robot.setAutoDelay(15);
        Point original = MouseInfo.getPointerInfo().getLocation();
        AtomicReference<TextArea> readme = new AtomicReference<>();
        AtomicReference<Window> source = new AtomicReference<>();
        AtomicReference<Window> target = new AtomicReference<>();
        FxThread.onFx(() -> {
            var screen = Screen.getPrimary().getVisualBounds();
            TextArea other = new TextArea("untouched");
            Dockable document = new Dockable(new DockableId("readme"), other);
            document.setTitle("Readme");
            f.manager.open(document, DockTarget.floating(new javafx.geometry.Rectangle2D(
                    screen.getMaxX() - 430, screen.getMinY() + 50, 400, 320)));
            f.manager.move(new DockableId("notes"), DockTarget.floating(new javafx.geometry.Rectangle2D(
                    screen.getMinX() + 50, screen.getMinY() + 50, 360, 300)));
            readme.set(other);
            target.set(other.getScene().getWindow());
            source.set(f.notes.getScene().getWindow());
            f.notes.selectRange(2, 4);
        });
        var watchdog = Executors.newSingleThreadScheduledExecutor();
        watchdog.schedule(() -> robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK), 25, TimeUnit.SECONDS);
        try {
            NativeFixtureFocus.establish(robot, f.notes);
            await(() -> source.get().isFocused(), "The initial source window must have focus");
            var before = onFx(f.manager::layout);
            drag(robot, onFx(() -> headerPoint(f.notes)), onFx(() -> center(readme.get())), () -> FxThread.onFx(() -> {
                assertTrue(target.get().getScene().getRoot().lookup(".dock-drop-indicator").isVisible());
                assertSame(before, f.manager.layout(), "A native preview does not commit a move");
                assertSame(source.get(), f.notes.getScene().getWindow());
            }));
            await(() -> f.notes.getScene().getWindow() == target.get(), "Native drop must join the other floating window");
            type(robot, f.notes, KeyEvent.VK_X, "abxef");
            FxThread.onFx(() -> assertFalse(source.get().isShowing()));

            var joined = onFx(f.manager::layout);
            drag(robot, onFx(() -> headerPoint(f.notes)), onFx(() -> center(f.main.getScene().lookup("#invalidTarget"))));
            FxThread.onFx(() -> assertSame(joined, f.manager.layout(), "Invalid managed-window space cannot float or move content"));
            type(robot, f.notes, KeyEvent.VK_Y, "abxyef");

            drag(robot, onFx(() -> headerPoint(f.notes)), onFx(() -> center(f.pane)), () -> {
                FxThread.onFx(() -> assertTrue(f.pane.lookup(".dock-drop-indicator").isVisible()));
                try { robot.keyPress(KeyEvent.VK_ESCAPE); }
                finally { robot.keyRelease(KeyEvent.VK_ESCAPE); }
                await(() -> !f.pane.lookup(".dock-drop-indicator").isVisible(), "Escape must remove the native drag preview");
            });
            FxThread.onFx(() -> assertSame(joined, f.manager.layout(), "Escape must leave the completed layout untouched"));
            type(robot, f.notes, KeyEvent.VK_Z, "abxyzef");
            FxThread.onFx(() -> assertEquals("untouched", readme.get().getText()));
        } finally {
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            for (int key : new int[]{KeyEvent.VK_ESCAPE, KeyEvent.VK_X, KeyEvent.VK_Y, KeyEvent.VK_Z}) robot.keyRelease(key);
            robot.mouseMove(original.x, original.y);
            watchdog.shutdownNow();
            FxThread.onFx(f::close);
        }
    }

    @Test
    @Timeout(25)
    void nativeFloatingDropIntoCollapsedOverlayOpensOnlyAtCommitAndRetainsGroupSize() throws Exception {
        Fixture f = fixture();
        Robot robot = new Robot();
        robot.setAutoDelay(15);
        Point original = MouseInfo.getPointerInfo().getLocation();
        AtomicReference<TextArea> project = new AtomicReference<>();
        AtomicReference<Window> source = new AtomicReference<>();
        FxThread.onFx(() -> {
            TextArea content = new TextArea("Project");
            Dockable tool = new Dockable(new DockableId("project"), content);
            tool.setTitle("Project");
            f.manager.open(tool, DockTarget.area("tools"));
            f.manager.setGroupCollapsed(toolGroup(f.manager).id(), true);
            var screen = Screen.getPrimary().getVisualBounds();
            f.manager.move(new DockableId("notes"), DockTarget.floating(new javafx.geometry.Rectangle2D(
                    screen.getMaxX() - 430, screen.getMinY() + 50, 400, 320)));
            project.set(content);
            source.set(f.notes.getScene().getWindow());
            f.notes.selectRange(2, 4);
        });
        var watchdog = Executors.newSingleThreadScheduledExecutor();
        watchdog.schedule(() -> robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK), 20, TimeUnit.SECONDS);
        try {
            NativeFixtureFocus.establish(robot, f.notes);
            await(() -> source.get().isFocused(), "The initial floating source must be focused");
            var initial = onFx(f.manager::layout);
            drag(robot, onFx(() -> headerPoint(f.notes)), onFx(() -> headerPoint(project.get())), () -> FxThread.onFx(() -> {
                assertTrue(f.pane.lookup(".dock-drop-indicator").isVisible());
                assertTrue(toolGroup(f.manager).collapsed());
                assertSame(initial, f.manager.layout(), "The native preview must not expand an overlay");
            }));
            await(() -> f.notes.getScene().getWindow() == f.main, "The successful drop must move into the main overlay");
            type(robot, f.notes, KeyEvent.VK_X, "abxef");
            FxThread.onFx(() -> {
                assertFalse(source.get().isShowing());
                assertFalse(toolGroup(f.manager).collapsed());
                assertEquals(DockGroupMode.OVERLAY, toolGroup(f.manager).mode());
                assertEquals(new DockableId("notes"), toolGroup(f.manager).selectedDockableId());
                assertEquals(240, owningTabs(f.notes).getWidth(), 1);
                assertSame(f.notes, f.manager.findDockable(new DockableId("notes")).orElseThrow().getContent());
                assertEquals("Project", project.get().getText());
            });
        } finally {
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.keyRelease(KeyEvent.VK_X);
            robot.mouseMove(original.x, original.y);
            watchdog.shutdownNow();
            FxThread.onFx(f::close);
        }
    }

    private static DockLayoutView.Group toolGroup(DockingManager manager) {
        return manager.layout().windows().getFirst().areas().stream().filter(area -> area.id().value().equals("tools"))
                .findFirst().orElseThrow().groups().getFirst();
    }

    private static void drag(Robot robot, Point from, Point to, HeldCheck held) throws Exception {
        robot.mouseMove(from.x, from.y);
        Thread.sleep(100);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        try {
            for (int step = 1; step <= 16; step++) {
                robot.mouseMove(from.x + (to.x - from.x) * step / 16, from.y + (to.y - from.y) * step / 16);
                Thread.sleep(25);
            }
            Thread.sleep(100);
            held.run();
        } finally { robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); }
    }

    private static void type(Robot robot, TextArea input, int key, String expected) throws Exception {
        await(() -> input.getScene().getWindow().isFocused() && input.getScene().getFocusOwner() == input,
                "The drop must focus the transferred editor without another click");
        try { robot.keyPress(key); }
        finally { robot.keyRelease(key); }
        await(() -> expected.equals(input.getText()), "Native typing must use the retained selection/caret: " + expected);
    }

    private static Point headerPoint(TextArea content) {
        TabPane tabs = owningTabs(content);
        var tab = tabs.getTabs().stream().filter(candidate -> candidate.getContent() == content).findFirst().orElseThrow();
        Node header = tabs.lookup("#" + tab.getId());
        assertNotNull(header, "The transferred tab must retain an addressable docking header");
        return center(header);
    }

    private static TabPane owningTabs(TextArea content) {
        return content.getScene().getRoot().lookupAll(".tab-pane").stream()
                .map(TabPane.class::cast).filter(candidate -> candidate.getTabs().stream()
                        .anyMatch(tab -> tab.getContent() == content)).findFirst().orElseThrow();
    }

    private static Point center(Node node) {
        Bounds bounds = node.localToScreen(node.getBoundsInLocal());
        return new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
    }

    private static <T> T onFx(Supplier<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        FxThread.onFx(() -> result.set(action.get()));
        return result.get();
    }

    private static void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        boolean ready;
        do {
            Thread.sleep(25);
            ready = onFx(condition::getAsBoolean);
        } while (!ready && System.nanoTime() < deadline);
        assertTrue(ready, message);
    }

    private static Fixture fixture() throws Exception {
        return onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            DockGroupDefinition tools = new DockGroupDefinition("tools");
            tools.setCollapsible(true);
            tools.setMode(DockGroupMode.OVERLAY);
            tools.setTabSide(javafx.geometry.Side.LEFT);
            tools.setPreferredWidth(240);
            pane.getInitialLayout().setLeft(tools);
            Stage main = new Stage();
            Label status = new Label("An invalid drop target inside the managed window");
            status.setId("invalidTarget");
            status.setMinHeight(30);
            main.setScene(new Scene(new BorderPane(pane, new Label("Workspace"), null, status, null), 600, 440));
            var screen = Screen.getPrimary().getVisualBounds();
            main.setX(screen.getMinX() + 40);
            main.setY(screen.getMinY() + 40);
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            TextArea notes = new TextArea("abcdef");
            Dockable editor = new Dockable(new DockableId("notes"), notes);
            editor.setTitle("Notes");
            manager.open(editor, DockTarget.area("editors"));
            main.show();
            manager.activate(editor.getId());
            notes.selectRange(2, 4);
            return new Fixture(main, pane, manager, notes, existing);
        });
    }

    private record Fixture(Stage main, DockPane pane, DockingManager manager, TextArea notes, List<Window> existing) {
        void close() {
            List.copyOf(Window.getWindows()).stream().filter(window -> !existing.contains(window)).forEach(Window::hide);
        }
    }

    @FunctionalInterface
    private interface HeldCheck { void run() throws Exception; }
}
