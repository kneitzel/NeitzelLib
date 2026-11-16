package de.neitzel.fx.example.bars;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Both standalone construction styles keep the editor and its bars connected. */
class StandaloneBarsExampleTest {
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @Test
    void javaAndFxmlScreensKeepActionsAndStatusConnected() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                for (boolean fxml : new boolean[]{false, true}) {
                    var root = BarsApp.create(fxml);
                    new Scene(root, 640, 360);
                    root.applyCss();
                    root.layout();
                    TextArea editor = (TextArea) root.lookup("#editor");
                    Button append = (Button) root.lookup("#append");
                    Button clear = (Button) root.lookup("#clear");
                    Label status = (Label) root.lookup("#status");
                    Label cursor = (Label) root.lookup("#cursor");
                    ProgressBar progress = (ProgressBar) root.lookup("#progress");

                    append.fire();
                    assertEquals("Hello", editor.getText());
                    assertEquals("Edited", status.getText());
                    assertEquals("Offset 5", cursor.getText());
                    assertEquals(0.05, progress.getProgress(), 0.001);
                    clear.fire();
                    assertEquals("", editor.getText());
                    assertEquals("Ready", status.getText());
                    assertEquals("Offset 0", cursor.getText());
                    assertEquals(0, progress.getProgress());
                }
            } catch (Throwable thrown) {
                failure.set(thrown);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) {
            fail("Standalone bars example failed", failure.get());
        }
    }
}
