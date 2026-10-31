package de.neitzel.fx.view;

import javafx.application.Platform;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Runs a check on the JavaFX application thread.
 */
final class FxThread {

    /**
     * Prevents instantiation.
     */
    private FxThread() {
    }

    /**
     * Starts the JavaFX toolkit once.
     *
     * @throws InterruptedException when the toolkit does not start
     */
    static void start() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        if (!started.await(10, TimeUnit.SECONDS)) {
            fail("JavaFX toolkit did not start");
        }
    }

    /**
     * Runs one check on the JavaFX application thread.
     *
     * @param action the check
     * @throws Exception when the check fails
     */
    static void onFx(FxAction action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable failure) {
                thrown.set(failure);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(10, TimeUnit.SECONDS)) {
            fail("JavaFX thread did not finish");
        }
        Throwable failure = thrown.get();
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure != null) {
            throw new AssertionError(failure);
        }
    }

    /**
     * One check that runs on the JavaFX application thread.
     */
    @FunctionalInterface
    interface FxAction {
        /**
         * Runs the check.
         *
         * @throws Exception when the check fails
         */
        void run() throws Exception;
    }
}
