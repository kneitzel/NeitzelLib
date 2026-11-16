package de.neitzel.fx.example.docking;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the visible reset and rule controls of the actual runnable application. */
class ResetDockingExampleTest {
    @Test
    void resetButtonReportsRejectionThenReturnsTheSameUnsavedEditor() throws Exception {
        onFx(() -> {
            Platform.setImplicitExit(false);
            List<Window> existing = List.copyOf(Window.getWindows());
            Stage stage = new Stage();
            try {
                new ResetDockingApp().start(stage);
                Parent root = stage.getScene().getRoot();
                root.applyCss();
                root.layout();
                TextArea notes = root.lookupAll(".tab-pane").stream().map(TabPane.class::cast)
                        .flatMap(tabs -> tabs.getTabs().stream()).filter(tab -> tab.getText().equals("Notes"))
                        .map(tab -> (TextArea) tab.getContent()).findFirst().orElseThrow();
                notes.setText("Still editing; nothing saved");
                notes.selectRange(0, 5);
                button(root, "Rearrange").fire();
                Window floating = notes.getScene().getWindow();
                assertNotSame(stage, floating);
                CheckBox allowProject = (CheckBox) root.lookup(".check-box");
                allowProject.setSelected(false);
                button(root, "Reset layout").fire();
                assertSame(floating, notes.getScene().getWindow());
                assertTrue(floating.isShowing());
                assertTrue(root.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast)
                        .anyMatch(label -> label.getText().startsWith("Layout unchanged:")));

                allowProject.setSelected(true);
                button(root, "Reset layout").fire();
                assertSame(stage, notes.getScene().getWindow());
                assertFalse(floating.isShowing());
                assertEquals("Still editing; nothing saved", notes.getText());
                assertEquals("Still", notes.getSelectedText());
                assertTrue(root.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast)
                        .anyMatch(label -> label.getText().startsWith("Starting layout restored")));
            } finally {
                List.copyOf(Window.getWindows()).stream().filter(window -> !existing.contains(window)).forEach(Window::hide);
            }
        });
    }

    private static Button button(Parent root, String text) {
        return root.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getText().equals(text)).findFirst().orElseThrow();
    }

    private static void onFx(FxAction action) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); }
        catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try { action.run(); }
            catch (Throwable thrown) { failure.set(thrown); }
            finally { done.countDown(); }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) fail("Reset docking example failed", failure.get());
    }

    @FunctionalInterface
    private interface FxAction { void run() throws Exception; }
}
