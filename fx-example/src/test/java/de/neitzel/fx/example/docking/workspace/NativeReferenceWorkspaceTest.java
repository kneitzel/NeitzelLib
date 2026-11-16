package de.neitzel.fx.example.docking.workspace;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import de.neitzel.fx.docking.DockableId;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Rectangle;
import javax.imageio.ImageIO;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static de.neitzel.fx.example.docking.workspace.ReferenceWorkspaceTest.onFx;

/** Actual application controls plus OS input; requires an exclusive interactive desktop. */
@EnabledIfSystemProperty(named = "neitzellib.nativeWorkspaceTest", matches = "true")
class NativeReferenceWorkspaceTest {
    @TempDir Path directory;
    private static final int[] KEYS = {KeyEvent.VK_DOWN, KeyEvent.VK_ENTER, KeyEvent.VK_1, KeyEvent.VK_2};

    @Test @Timeout(35)
    void actualDragTypingAndKeyboardReturnKeepTheEditorUntilEveryCloseAnswer() throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(20);
        Point pointer = MouseInfo.getPointerInfo().getLocation();
        AtomicReference<Fixture> fixture = new AtomicReference<>();
        AtomicReference<List<Window>> existingWindows = new AtomicReference<>();
        AtomicReference<ReferenceWorkspace> application = new AtomicReference<>();
        var watchdog = Executors.newSingleThreadScheduledExecutor();
        watchdog.schedule(() -> robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK), 30, TimeUnit.SECONDS);
        try {
            onFx(() -> {
                javafx.application.Platform.setImplicitExit(false);
                List<Window> existing = List.copyOf(Window.getWindows()); existingWindows.set(existing);
                Stage main = new Stage();
                ReferenceWorkspace app = new ReferenceWorkspace(main, true, false,
                        new WorkspaceStore(directory.resolve("workspace.json")), Optional.empty());
                application.set(app);
                var screen = Screen.getPrimary().getVisualBounds();
                main.setX(screen.getMinX() + 40); main.setY(screen.getMinY() + 40);
                main.setWidth(720); main.setHeight(560);
                Parent root = main.getScene().getRoot(); root.applyCss(); root.layout();
                TextArea notes = (TextArea) root.lookup("#notesText"); assertNotNull(notes);
                notes.setText("abcdef"); notes.selectRange(2, 4);
                fixture.set(new Fixture(main, notes, new javafx.scene.robot.Robot()));
            });
            Fixture f = fixture.get();
            await(() -> f.main.isFocused(), "The application main window must have native focus before the gesture");
            Point outside = value(() -> {
                var bounds = Screen.getPrimary().getVisualBounds();
                Point target = new Point((int) bounds.getMaxX() - 80, (int) bounds.getMinY() + 160);
                assertTrue(target.x > f.main.getX() + f.main.getWidth(), "Fixture must leave desktop space for floating");
                return target;
            });
            drag(robot, value(() -> headerPoint(f.notes)), outside);
            await(() -> f.notes.getScene().getWindow() != f.main, "Native drop outside must float Notes");
            type(robot, f.notes, KeyEvent.VK_1, "ab1ef");
            captureRequestedScreenshot(robot, f);
            command(robot, f, "Return to main");
            await(() -> f.notes.getScene().getWindow() == f.main, "The native keyboard menu must return Notes");
            type(robot, f.notes, KeyEvent.VK_2, "ab12ef");
            onFx(() -> assertSame(f.notes, f.main.getScene().lookup("#notesText")));
            Parent root = value(() -> f.main.getScene().getRoot());
            TextArea events = value(() -> (TextArea) root.lookup("#workspaceEvents"));
            onFx(() -> {
                ((TitledPane) root.lookup("#workspaceScenarios")).setExpanded(true);
                root.applyCss(); root.layout();
                ((CheckBox) root.lookup("#vetoReadme")).fire();
                ((Button) root.lookup("#quitWorkspace")).fire();
            });
            await(() -> events.getText().contains("Checked changes"), "Quit must ask every Dockable despite Readme's veto");
            onFx(() -> {
                assertAllCheckedBeforeAnyClosed(events.getText());
                assertFalse(events.getText().contains("Closed "));
                assertTrue(f.main.isShowing());
                assertSame(f.notes, root.lookup("#notesText"));
                assertNotNull(root.lookup("#readmeText"));
                ((CheckBox) root.lookup("#vetoReadme")).fire();
                ((CheckBox) root.lookup("#saveFailure")).fire();
                ((Button) root.lookup("#quitWorkspace")).fire();
            });
            await(() -> events.getText().contains("All consent obtained; save pending"), "Unanimous consent must reach the application's save phase");
            onFx(() -> {
                assertEquals(8, events.getText().lines().filter(line -> line.startsWith("Checked ")).count(),
                        "The vetoed attempt and the fresh attempt must each ask all four Dockables");
                assertTrue(f.main.isShowing()); assertSame(f.notes, root.lookup("#notesText"));
                assertFalse(events.getText().contains("Closed "));
                ((Button) root.lookup("#retrySave")).fire();
                assertTrue(((Label) root.lookup("#workspaceStatus")).getText().startsWith("Save failed"));
                assertFalse(events.getText().contains("Closed "), "Save failure must keep every Dockable open");
                ((CheckBox) root.lookup("#saveFailure")).fire();
                ((Button) root.lookup("#retrySave")).fire();
            });
            await(() -> !f.main.isShowing(), "Successful save must complete Quit");
            onFx(() -> {
                String log = events.getText();
                assertAllCheckedBeforeAnyClosed(log);
                assertEquals(4, log.lines().filter(line -> line.startsWith("Closed ")).count());
                assertTrue(log.lastIndexOf("Checked ") < log.indexOf("All consent obtained; save pending"));
                assertTrue(log.indexOf("Saved ") < log.indexOf("Closed "), "Persist before releasing editor resources");
            });
            StoredWorkspace saved = new WorkspaceStore(directory.resolve("workspace.json")).read().orElseThrow();
            assertEquals("ab12ef", saved.documents().get(new DockableId("notes")).text());
        } finally {
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            for (int key : KEYS) robot.keyRelease(key);
            robot.mouseMove(pointer.x, pointer.y);
            watchdog.shutdownNow();
            if (existingWindows.get() != null) onFx(() -> {
                if (fixture.get() != null) {
                    fixture.get().keyboard.keyRelease(javafx.scene.input.KeyCode.F10);
                    fixture.get().keyboard.keyRelease(javafx.scene.input.KeyCode.SHIFT);
                }
                try { if (application.get() != null) application.get().close(); }
                finally { List.copyOf(Window.getWindows()).stream().filter(w -> !existingWindows.get().contains(w)).forEach(Window::hide); }
            });
        }
    }

    private static void captureRequestedScreenshot(Robot robot, Fixture fixture) throws Exception {
        String output = System.getProperty("neitzellib.workspaceScreenshot");
        if (output == null || output.isBlank()) return;
        Rectangle frame = value(() -> {
            Window floating = fixture.notes.getScene().getWindow();
            double x = Math.min(fixture.main.getX(), floating.getX());
            double y = Math.min(fixture.main.getY(), floating.getY());
            double right = Math.max(fixture.main.getX() + fixture.main.getWidth(), floating.getX() + floating.getWidth());
            double bottom = Math.max(fixture.main.getY() + fixture.main.getHeight(), floating.getY() + floating.getHeight());
            return new Rectangle((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(right - x), (int) Math.ceil(bottom - y));
        });
        ImageIO.write(robot.createScreenCapture(frame), "png", Path.of(output).toFile());
    }

    private static void assertAllCheckedBeforeAnyClosed(String log) {
        for (String id : List.of("notes", "readme", "project", "changes")) {
            int checked = log.indexOf("Checked " + id + " — MAIN_WINDOW");
            assertTrue(checked >= 0, "Missing application consent for " + id);
            assertTrue(log.indexOf("Closed ") < 0 || checked < log.indexOf("Closed "));
        }
    }

    private static void command(Robot robot, Fixture f, String action) throws Exception {
        AtomicReference<TabPane> strip = new AtomicReference<>();
        AtomicReference<ContextMenu> menu = new AtomicReference<>();
        onFx(() -> {
            var root = f.notes.getScene().getRoot(); root.applyCss(); root.layout();
            TabPane tabs = owningTabs(f.notes); tabs.requestFocus(); strip.set(tabs);
            menu.set(tabs.getSelectionModel().getSelectedItem().getContextMenu());
        });
        await(() -> f.notes.getScene().getWindow().isFocused() && f.notes.getScene().getFocusOwner() == strip.get(),
                "The header must have native focus before Shift+F10");
        onFx(() -> {
            try { f.keyboard.keyPress(javafx.scene.input.KeyCode.SHIFT); f.keyboard.keyPress(javafx.scene.input.KeyCode.F10); }
            finally { f.keyboard.keyRelease(javafx.scene.input.KeyCode.F10); f.keyboard.keyRelease(javafx.scene.input.KeyCode.SHIFT); }
        });
        await(() -> menu.get().isShowing(), "Native Shift+F10 must open the menu");
        for (int step = 0; step < 8; step++) {
            String current = value(() -> focusedAction(menu.get()));
            if (action.equals(current)) break;
            press(robot, KeyEvent.VK_DOWN);
            await(() -> !java.util.Objects.equals(current, focusedAction(menu.get())), "Arrow must change the focused action");
        }
        onFx(() -> assertEquals(action, focusedAction(menu.get())));
        press(robot, KeyEvent.VK_ENTER);
    }

    private static String focusedAction(ContextMenu menu) {
        return menu.getSkin().getNode().lookupAll(".menu-item:focused").stream()
                .map(node -> (String) node.queryAccessibleAttribute(javafx.scene.AccessibleAttribute.TEXT))
                .findFirst().orElse(null);
    }
    private static TabPane owningTabs(Node content) {
        Node current = content;
        while (current != null && !(current instanceof TabPane)) current = current.getParent();
        return assertInstanceOf(TabPane.class, current);
    }
    private static Point headerPoint(TextArea notes) {
        TabPane tabs = owningTabs(notes);
        Node header = tabs.lookup("#" + tabs.getSelectionModel().getSelectedItem().getId());
        assertNotNull(header);
        Bounds bounds = header.localToScreen(header.getBoundsInLocal());
        return new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
    }
    private static void drag(Robot robot, Point from, Point to) throws Exception {
        robot.mouseMove(from.x, from.y); Thread.sleep(100);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        try {
            for (int step = 1; step <= 16; step++) {
                robot.mouseMove(from.x + (to.x - from.x) * step / 16, from.y + (to.y - from.y) * step / 16);
                Thread.sleep(25);
            }
        } finally { robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); }
    }
    private static void type(Robot robot, TextArea notes, int key, String expected) throws Exception {
        await(() -> notes.getScene().getWindow().isFocused() && notes.getScene().getFocusOwner() == notes,
                "Transfer must restore native/content focus without a click");
        press(robot, key);
        await(() -> expected.equals(notes.getText()), "Actual typing must preserve the selection/caret: " + expected);
    }
    private static void press(Robot robot, int key) {
        try { robot.keyPress(key); } finally { robot.keyRelease(key); }
    }
    private static <T> T value(Supplier<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>(); onFx(() -> result.set(action.get())); return result.get();
    }
    private static void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        boolean ready;
        do { Thread.sleep(25); ready = value(condition::getAsBoolean); } while (!ready && System.nanoTime() < deadline);
        assertTrue(ready, message);
    }
    private record Fixture(Stage main, TextArea notes, javafx.scene.robot.Robot keyboard) { }
}
