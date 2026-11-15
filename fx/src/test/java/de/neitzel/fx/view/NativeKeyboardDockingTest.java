package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

/** Native keyboard delivery through the actual popup; requires the exclusive desktop slot. */
@EnabledIfSystemProperty(named = "neitzellib.nativeKeyboardTest", matches = "true")
class NativeKeyboardDockingTest {
    private static final int[] KEYS = {KeyEvent.VK_DOWN, KeyEvent.VK_ENTER, KeyEvent.VK_1, KeyEvent.VK_2};

    @BeforeAll static void startJavaFx() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @RepeatedTest(3) @Timeout(25)
    void shiftF10FloatAndReturnAllowImmediateTypingAtTheOriginalCaret() throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(25);
        AtomicReference<Fixture> reference = new AtomicReference<>();
        AtomicReference<List<Window>> existing = new AtomicReference<>();
        FxThread.onFx(() -> {
            existing.set(List.copyOf(Window.getWindows()));
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 760, 460));
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            TextArea editor = new TextArea("abcdef");
            Dockable notes = new Dockable(new DockableId("notes"), editor);
            notes.setTitle("Notes");
            manager.open(notes, DockTarget.area("editors"));
            main.show();
            manager.activate(notes.getId());
            editor.selectRange(2, 4);
            reference.set(new Fixture(main, editor, manager, notes, new javafx.scene.robot.Robot()));
        });
        Fixture fixture = reference.get();
        try {
            openMenuByNativeKeyboard(robot, fixture, "Float");
            await(() -> fixture.editor.getScene().getWindow() != fixture.main, "Float was not invoked by the keyboard");
            typeWithoutFocusRepair(robot, fixture, KeyEvent.VK_1, "ab1ef");
            openMenuByNativeKeyboard(robot, fixture, "Return to main");
            await(() -> fixture.editor.getScene().getWindow() == fixture.main, "Return was not invoked by the keyboard");
            typeWithoutFocusRepair(robot, fixture, KeyEvent.VK_2, "ab12ef");
            FxThread.onFx(() -> assertSame(fixture.notes, fixture.manager.activeDockable()));
        } finally {
            for (int key : KEYS) robot.keyRelease(key);
            FxThread.onFx(() -> {
                fixture.keyboard.keyRelease(javafx.scene.input.KeyCode.F10);
                fixture.keyboard.keyRelease(javafx.scene.input.KeyCode.SHIFT);
                for (Window window : List.copyOf(Window.getWindows())) if (!existing.get().contains(window)) window.hide();
            });
        }
    }

    private static void openMenuByNativeKeyboard(Robot robot, Fixture fixture, String action) throws Exception {
        AtomicReference<TabPane> selectedTabs = new AtomicReference<>();
        AtomicReference<ContextMenu> menu = new AtomicReference<>();
        FxThread.onFx(() -> {
            var root = fixture.editor.getScene().getRoot();
            root.applyCss(); root.layout();
            TabPane tabs = root.lookupAll(".dock-group").stream().map(TabPane.class::cast)
                    .filter(candidate -> candidate.getTabs().stream().anyMatch(tab -> tab.getContent() == fixture.editor))
                    .findFirst().orElseThrow();
            // Start the command from its tab strip. Never repair content focus after the command.
            tabs.requestFocus();
            selectedTabs.set(tabs);
            menu.set(tabs.getSelectionModel().getSelectedItem().getContextMenu());
        });
        await(() -> fixture.editor.getScene().getWindow().isFocused()
                && fixture.editor.getScene().getFocusOwner() == selectedTabs.get(), "The tab strip did not receive native focus");
        FxThread.onFx(() -> {
            try {
                fixture.keyboard.keyPress(javafx.scene.input.KeyCode.SHIFT);
                fixture.keyboard.keyPress(javafx.scene.input.KeyCode.F10);
            } finally {
                fixture.keyboard.keyRelease(javafx.scene.input.KeyCode.F10);
                fixture.keyboard.keyRelease(javafx.scene.input.KeyCode.SHIFT);
            }
        });
        await(() -> menu.get().isShowing(), "Native Shift+F10 did not open the tab menu");
        for (int step = 0; step < 7; step++) {
            AtomicReference<String> selected = new AtomicReference<>();
            FxThread.onFx(() -> selected.set(focusedAction(menu.get())));
            if (action.equals(selected.get())) break;
            press(robot, KeyEvent.VK_DOWN);
            await(() -> !java.util.Objects.equals(selected.get(), focusedAction(menu.get())),
                    "The arrow key did not move the menu selection");
        }
        FxThread.onFx(() -> assertEquals(action, focusedAction(menu.get()),
                "Enter must execute the visibly focused intended action"));
        press(robot, KeyEvent.VK_ENTER);
    }

    private static String focusedAction(ContextMenu menu) {
        return menu.getSkin().getNode().lookupAll(".menu-item:focused").stream()
                .map(node -> (String) node.queryAccessibleAttribute(javafx.scene.AccessibleAttribute.TEXT))
                .findFirst().orElse(null);
    }

    private static void typeWithoutFocusRepair(Robot robot, Fixture fixture, int key, String expected) throws Exception {
        await(() -> fixture.editor.getScene().getWindow().isFocused()
                && fixture.editor.getScene().getFocusOwner() == fixture.editor,
                "The menu transfer did not restore content/native window focus");
        press(robot, key);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        AtomicReference<String> actual = new AtomicReference<>();
        do {
            Thread.sleep(25);
            FxThread.onFx(() -> actual.set(fixture.editor.getText()));
        } while (!expected.equals(actual.get()) && System.nanoTime() < deadline);
        assertEquals(expected, actual.get(), "Native typing continues at the transferred editor caret");
    }

    private static void press(Robot robot, int key) {
        try { robot.keyPress(key); } finally { robot.keyRelease(key); }
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

    private record Fixture(Stage main, TextArea editor, DockingManager manager, Dockable notes,
                           javafx.scene.robot.Robot keyboard) { }
}
