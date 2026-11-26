package de.neitzel.fx.test;

import de.neitzel.fx.view.LoadedView;
import de.neitzel.fx.view.ViewDependencies;
import de.neitzel.fx.view.ViewLoader;
import de.neitzel.fx.view.ViewModelContext;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.stage.Window;

/** JavaFX test operations scoped to one JUnit test. Obtain the fixture through {@link FxTest}. */
public final class FxFixture {
    private static final long TIMEOUT_SECONDS = 10;

    private final ViewModelContext models = new ViewModelContext();
    private final List<Throwable> callbackFailures = new ArrayList<>();
    private List<Window> previousWindows;
    private Thread.UncaughtExceptionHandler previousHandler;

    /** An FX-thread action that may propagate a checked exception. */
    @FunctionalInterface
    public interface Action {
        /**
         * Performs the action.
         * @throws Exception on failure
         */
        void run() throws Exception;
    }

    FxFixture() {}

    void start() throws Exception {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException alreadyStarted) {
            // A previous test or another JavaFX client has started this JVM's toolkit.
        }
        run(() -> {
            Platform.setImplicitExit(false);
            previousWindows = List.copyOf(Window.getWindows());
            previousHandler = Thread.currentThread().getUncaughtExceptionHandler();
            Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> callbackFailures.add(error));
        });
    }

    /**
     * Runs an action on the FX thread and propagates its failure to the test.
     * @param action test action
     * @throws Exception if the action fails or the wait is interrupted or times out
     */
    public void run(Action action) throws Exception {
        call(() -> {
            action.run();
            return null;
        });
    }

    /**
     * Returns the result of an FX-thread operation, with a bounded wait on the calling thread.
     * @param <T> result type
     * @param action operation to perform
     * @return the value returned by the operation
     * @throws Exception if the operation fails or the wait is interrupted or times out
     */
    public <T> T call(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) return action.call();
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        try {
            return task.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof Exception exception) throw exception;
            if (failure.getCause() instanceof Error error) throw error;
            throw new AssertionError(failure.getCause());
        } catch (TimeoutException | InterruptedException failure) {
            task.cancel(false);
            throw failure;
        }
    }

    /**
     * Waits on the test thread while evaluating the named condition on the FX thread.
     * @param description expected state, included in the timeout message
     * @param condition UI predicate to evaluate
     * @throws Exception if evaluation fails or waiting is interrupted
     * @throws AssertionError if the condition stays false
     */
    public void await(String description, BooleanSupplier condition) throws Exception {
        if (Platform.isFxApplicationThread()) throw new IllegalStateException("await requires the test thread");
        long start = System.nanoTime();
        while (!call(condition::getAsBoolean)) {
            if (System.nanoTime() - start >= TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)) {
                throw new AssertionError("Timed out waiting for " + description);
            }
            Thread.sleep(15);
        }
    }

    /**
     * Loads managed FXML with application-supplied dependencies on the FX thread.
     * @param fxml view resource
     * @param model model for the view
     * @param dependencies adapter for View objects and services
     * @return managed view
     * @throws Exception if the view cannot be loaded or the wait is interrupted or times out
     */
    public LoadedView loadWithDependencies(URL fxml, Object model, ViewDependencies dependencies) throws Exception {
        return call(() -> ViewLoader.loadWithDependencies(fxml, model, dependencies, models));
    }

    void finish() throws Exception {
        run(() -> {
            try {
                for (var window : List.copyOf(Window.getWindows()).reversed()) {
                    if (!previousWindows.contains(window)) window.hide();
                }
            } finally {
                try {
                    models.close();
                } finally {
                    Thread.currentThread().setUncaughtExceptionHandler(previousHandler);
                }
            }
            if (!callbackFailures.isEmpty()) {
                var failure = new AssertionError("Unhandled exception on the JavaFX application thread",
                        callbackFailures.getFirst());
                callbackFailures.stream().skip(1).forEach(failure::addSuppressed);
                throw failure;
            }
        });
    }
}
