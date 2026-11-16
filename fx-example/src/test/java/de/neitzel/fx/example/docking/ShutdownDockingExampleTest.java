package de.neitzel.fx.example.docking;

import de.neitzel.fx.docking.MainWindowCloseMode;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ShutdownDockingExampleTest {
    @Test void quitAndReplaceControlsPreserveEditorsThroughVetoRetryCancelAndFailureThenReattach() throws Exception {
        onFx(() -> {
            Platform.setImplicitExit(false);
            List<Window> existing = List.copyOf(Window.getWindows());
            Stage stage = new Stage();
            try {
                new ShutdownDockingApp().start(stage);
                Parent root = stage.getScene().getRoot(); root.applyCss(); root.layout();
                TextArea notes = (TextArea) root.lookup("#notes-content");
                notes.setText("Keep these unsaved notes");
                Label status = (Label) root.lookup("#shutdown-status");
                TextArea history = (TextArea) root.lookup("#shutdown-history");
                CheckBox veto = (CheckBox) root.lookup("#late-veto");
                veto.setSelected(true);
                button(root, "replace-workspace").fire();
                assertTrue(status.getText().contains("Vetoed"), status.getText());
                for (String id : List.of("notes", "project", "preview")) assertTrue(history.getText().contains("Checked " + id));
                assertFalse(history.getText().contains("Snapshot MANAGER_ONLY"));
                assertEquals("Keep these unsaved notes", notes.getText());
                assertEquals(2, windowsSince(existing).size());

                veto.setSelected(false);
                button(root, "replace-workspace").fire();
                assertTrue(status.getText().contains("Save pending"), status.getText());
                assertTrue(history.getText().contains("Snapshot MANAGER_ONLY: 2 windows"));
                button(root, "retry-save").fire();
                assertTrue(status.getText().contains("Retry failed"), status.getText());
                assertSame(notes, root.lookup("#notes-content"));
                assertEquals(2, windowsSince(existing).size());
                button(root, "cancel-save").fire();
                assertTrue(status.getText().contains("Canceled"), status.getText());

                ((CheckBox) root.lookup("#throw-shutdown")).setSelected(true);
                button(root, "quit-workspace").fire();
                assertTrue(status.getText().contains("SHUTDOWN_CALLBACK_FAILED"), status.getText());
                ((CheckBox) root.lookup("#throw-shutdown")).setSelected(false);
                button(root, "quit-workspace").fire();
                button(root, "fail-save").fire();
                assertTrue(status.getText().contains("SHUTDOWN_CALLBACK_FAILED"), status.getText());
                assertSame(notes, root.lookup("#notes-content"));
                assertEquals("Keep these unsaved notes", notes.getText());

                @SuppressWarnings("unchecked") ComboBox<MainWindowCloseMode> ownership =
                        (ComboBox<MainWindowCloseMode>) root.lookup("#next-ownership");
                ownership.setValue(MainWindowCloseMode.APPLICATION_COORDINATED);
                button(root, "replace-workspace").fire();
                button(root, "proceed-save").fire();
                root.applyCss(); root.layout();
                assertTrue(stage.isShowing());
                assertTrue(status.getText().contains("Workspace 2 attached"), status.getText());
                assertNotSame(notes, root.lookup("#notes-content"));
                assertEquals(2, windowsSince(existing).size());
                assertTrue(history.getText().contains("Released preview"));

                button(root, "quit-workspace").fire();
                button(root, "proceed-save").fire();
                assertTrue(stage.isShowing(), "Application-coordinated completion keeps the caller's Stage open");
                assertEquals(List.of(stage), windowsSince(existing));
                assertFalse(button(root, "close-application-window").isDisabled());
                button(root, "close-application-window").fire();
                assertFalse(stage.isShowing());
            } finally { windowsSince(existing).forEach(Window::hide); stage.hide(); }
        });
    }

    @Test void automaticMainCloseWaitsForASuccessfulRetryThenHidesEveryWindow() throws Exception {
        onFx(() -> {
            Platform.setImplicitExit(false);
            List<Window> existing = List.copyOf(Window.getWindows()); Stage stage = new Stage();
            try {
                new ShutdownDockingApp().start(stage);
                Parent root = stage.getScene().getRoot(); root.applyCss(); root.layout();
                stage.fireEvent(new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
                assertTrue(stage.isShowing());
                assertEquals(2, windowsSince(existing).size());
                button(root, "retry-save").fire();
                assertTrue(((Label) root.lookup("#shutdown-status")).getText().contains("Retry failed"));
                assertTrue(stage.isShowing());
                ((CheckBox) root.lookup("#save-fails")).setSelected(false);
                button(root, "retry-save").fire();
                assertFalse(stage.isShowing());
                assertTrue(windowsSince(existing).isEmpty());
                TextArea history = (TextArea) root.lookup("#shutdown-history");
                assertTrue(history.getText().contains("Saved 2 windows"));
                for (String id : List.of("notes", "project", "preview")) assertTrue(history.getText().contains("Released " + id));
            } finally { windowsSince(existing).forEach(Window::hide); stage.hide(); }
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void quitWaitsForFloatingConsentWithoutRepeatingAVetoedPrompt(boolean allowFloating) throws Exception {
        AtomicReference<Stage> main = new AtomicReference<>();
        AtomicReference<Stage> floating = new AtomicReference<>();
        AtomicReference<TextArea> originalNotes = new AtomicReference<>();
        AtomicReference<List<Window>> initialWindows = new AtomicReference<>();
        try {
            onFx(() -> {
                Platform.setImplicitExit(false);
                List<Window> existing = List.copyOf(Window.getWindows()); initialWindows.set(existing);
                Stage stage = new Stage(); main.set(stage);
                new ShutdownDockingApp().start(stage);
                Parent root = stage.getScene().getRoot(); root.applyCss(); root.layout();
                TextArea history = (TextArea) root.lookup("#shutdown-history");
                Label status = (Label) root.lookup("#shutdown-status");
                TextArea notes = (TextArea) root.lookup("#notes-content"); notes.setText("Still editing while consent waits");
                originalNotes.set(notes);
                Stage preview = (Stage) windowsSince(existing).stream().filter(window -> window != stage).findFirst().orElseThrow();
                floating.set(preview);
                ((CheckBox) root.lookup("#delay-floating-consent")).setSelected(true);
                button(root, "close-preview-window").fire();
                assertTrue(status.getText().contains("Preview consent pending"), status.getText());
                assertEquals(1, history.getText().lines().filter(line -> line.startsWith("Checked preview")).count());
                assertFalse(button(root, "quit-workspace").isDisabled(), "Quit can wait behind a running floating close");
                button(root, "quit-workspace").fire();
                assertTrue(status.getText().contains("Quit waiting"), status.getText());
                assertFalse(history.getText().contains("Snapshot MAIN_WINDOW"));
                assertFalse(history.getText().contains("Checked notes"));
                assertTrue(preview.isShowing()); assertTrue(stage.isShowing());
            });
            onFx(() -> button(main.get().getScene().getRoot(), allowFloating ? "allow-preview" : "veto-preview").fire());
            // A queued manager request starts in a subsequent ordinary FX turn.
            onFx(() -> { });
            onFx(() -> {
                Stage stage = main.get(); Stage preview = floating.get();
                Parent root = stage.getScene().getRoot();
                TextArea history = (TextArea) root.lookup("#shutdown-history");
                Label status = (Label) root.lookup("#shutdown-status");
                TextArea notes = originalNotes.get();
                assertEquals(1, history.getText().lines().filter(line -> line.startsWith("Checked preview")).count(),
                        "The queued Quit does not ask the already handled floating editor again");
                if (allowFloating) {
                    assertFalse(preview.isShowing());
                    assertTrue(status.getText().contains("Save pending"), status.getText());
                    assertTrue(history.getText().contains("Snapshot MAIN_WINDOW: 1 windows"));
                    assertTrue(history.getText().contains("Checked notes"));
                    assertTrue(history.getText().contains("Checked project"));
                } else {
                    assertTrue(preview.isShowing());
                    assertTrue(status.getText().contains("Vetoed"), status.getText());
                    assertFalse(history.getText().contains("Snapshot MAIN_WINDOW"));
                    assertFalse(history.getText().contains("Checked notes"));
                    assertFalse(history.getText().contains("Released "));
                    assertFalse(button(root, "quit-workspace").isDisabled());
                    button(root, "quit-workspace").fire();
                    assertEquals(2, history.getText().lines().filter(line -> line.startsWith("Checked preview")).count(),
                            "A new explicit Quit performs fresh checks after the earlier veto");
                    assertTrue(history.getText().contains("Snapshot MAIN_WINDOW: 2 windows"));
                }
                assertEquals("Still editing while consent waits", notes.getText());
                button(root, "cancel-save").fire();
                assertTrue(stage.isShowing()); assertTrue(status.getText().contains("Canceled"));
                assertSame(notes, root.lookup("#notes-content"));
            });
        } finally {
            if (initialWindows.get() != null) onFx(() -> {
                windowsSince(initialWindows.get()).forEach(Window::hide);
                if (main.get() != null) main.get().hide();
            });
        }
    }

    private static Button button(Parent root, String id) { return (Button) root.lookup("#" + id); }
    private static List<Window> windowsSince(List<Window> existing) {
        return Window.getWindows().stream().filter(window -> !existing.contains(window)).toList();
    }
    private static void onFx(FxAction action) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); } catch (IllegalStateException running) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try { action.run(); } catch (Throwable thrown) { failure.set(thrown); } finally { done.countDown(); }
        });
        assertTrue(done.await(15, TimeUnit.SECONDS));
        if (failure.get() != null) fail("Shutdown application failed", failure.get());
    }
    @FunctionalInterface private interface FxAction { void run() throws Exception; }
}
