package de.neitzel.fx.view;

import de.neitzel.fx.docking.DockGroupDefinition;
import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real keyboard input after tab-menu transfers; run only with an exclusive desktop slot. */
@EnabledIfSystemProperty(named = "neitzellib.nativeFocusTest", matches = "true")
class FloatingNativeFocusTest {
    private static final int[] DIGITS = {KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3, KeyEvent.VK_4};

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @Test
    @Timeout(20)
    void nativeTypingContinuesWithoutClicksAfterFloatCrossWindowMoveAndReturn() throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(20);
        Fixture fixture = fixture();
        AtomicReference<Stage> firstFloating = new AtomicReference<>();
        try {
            NativeFixtureFocus.establish(robot, fixture.notes);
            typeIntoFocusedNotes(robot, fixture, KeyEvent.VK_1, "1");
            FxThread.onFx(() -> {
                command(fixture.notes, "Float", null);
                firstFloating.set((Stage) fixture.notes.getScene().getWindow());
                assertNotSame(fixture.main, firstFloating.get());
                assertNotSame(fixture.other, firstFloating.get());
            });
            typeIntoFocusedNotes(robot, fixture, KeyEvent.VK_2, "12");

            FxThread.onFx(() -> {
                command(fixture.notes, "Move to", "Readme");
                assertSame(fixture.other, fixture.notes.getScene().getWindow());
                assertFalse(firstFloating.get().isShowing(), "The emptied source window is removed");
            });
            typeIntoFocusedNotes(robot, fixture, KeyEvent.VK_3, "123");

            FxThread.onFx(() -> {
                command(fixture.notes, "Return to main", null);
                assertSame(fixture.main, fixture.notes.getScene().getWindow());
            });
            typeIntoFocusedNotes(robot, fixture, KeyEvent.VK_4, "1234");
            FxThread.onFx(() -> {
                assertEquals("untouched", fixture.readme.getText());
                assertSame(fixture.notes, fixture.manager.findDockable(new DockableId("notes")).orElseThrow().getContent());
                assertEquals(new DockableId("notes"), fixture.manager.activeDockable().getId());
            });
        } finally {
            for (int key : DIGITS) robot.keyRelease(key);
            FxThread.onFx(() -> {
                if (firstFloating.get() != null) firstFloating.get().hide();
                fixture.other.hide();
                fixture.main.hide();
            });
        }
    }

    private static void typeIntoFocusedNotes(Robot robot, Fixture fixture, int key, String expected) throws Exception {
        // Observe focus only. Do not repair it with a click, activate(), or requestFocus().
        await(() -> fixture.notes.getScene().getWindow().isFocused()
                && fixture.notes.getScene().getFocusOwner() == fixture.notes, "Transferred editor did not receive native window focus");
        try { robot.keyPress(key); }
        finally { robot.keyRelease(key); }
        await(() -> expected.equals(fixture.notes.getText()), "Native input did not reach the transferred editor: " + expected);
        FxThread.onFx(() -> {
            assertEquals(expected, fixture.notes.getText());
            assertEquals("untouched", fixture.readme.getText());
            assertSame(fixture.notes, fixture.notes.getScene().getFocusOwner());
        });
    }

    private static void await(BooleanSupplier condition, String failure) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        AtomicReference<Boolean> ready = new AtomicReference<>(false);
        do {
            Thread.sleep(25);
            FxThread.onFx(() -> ready.set(condition.getAsBoolean()));
        } while (!ready.get() && System.nanoTime() < deadline);
        assertTrue(ready.get(), failure);
    }

    private static void command(TextArea content, String label, String childTitle) {
        ContextMenu menu = content.getScene().getRoot().lookupAll(".tab-pane").stream().map(TabPane.class::cast)
                .flatMap(tabs -> tabs.getTabs().stream()).filter(tab -> tab.getContent() == content)
                .findFirst().orElseThrow().getContextMenu();
        Stage owner = (Stage) content.getScene().getWindow();
        menu.show(owner, owner.getX() + 60, owner.getY() + 60);
        try {
            MenuItem action = menu.getItems().stream().filter(item -> item.getText().equals(label)).findFirst().orElseThrow();
            if (childTitle != null) action = ((Menu) action).getItems().stream()
                    .filter(item -> item.getText().contains(childTitle)).findFirst().orElseThrow();
            assertFalse(action.isDisable());
            menu.hide();
            action.fire();
        } finally { menu.hide(); }
    }

    private static Fixture fixture() throws Exception {
        AtomicReference<Fixture> result = new AtomicReference<>();
        FxThread.onFx(() -> {
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 800, 500));
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            TextArea notes = new TextArea();
            TextArea readme = new TextArea("untouched");
            Dockable editor = new Dockable(new DockableId("notes"), notes);
            editor.setTitle("Notes");
            Dockable other = new Dockable(new DockableId("readme"), readme);
            other.setTitle("Readme");
            manager.open(editor, DockTarget.area("editors"));
            manager.open(other, DockTarget.area("editors"));
            main.show();
            manager.move(other.getId(), DockTarget.floating());
            Stage floating = (Stage) readme.getScene().getWindow();
            manager.activate(editor.getId()); // Initial setup only; all later focus must follow the transfer itself.
            result.set(new Fixture(main, floating, manager, notes, readme));
        });
        return result.get();
    }

    private record Fixture(Stage main, Stage other, DockingManager manager, TextArea notes, TextArea readme) { }
}
