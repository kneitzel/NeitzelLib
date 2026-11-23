package de.neitzel.fx.view;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import lombok.Getter;

/**
 * A Command that runs one JavaFX Task at a time on a caller-provided executor.
 * Create and use it, including its Command and state, on the JavaFX application thread.
 */
public final class AsyncCommand<T> implements AutoCloseable {

    /** The execution phase reported to the View. */
    public enum Phase { IDLE, RUNNING, SUCCEEDED, FAILED, CANCELLED, CLOSED }

    /**
     * One observation of an execution, published on the FX application thread.
     *
     * @param phase execution phase
     * @param result value from a successful execution
     * @param error failure from task creation, scheduling or execution
     * @param progress progress in the range 0 to 1, or -1 for indeterminate work
     * @param message current task message
     * @param <T> result type
     */
    public record State<T>(Phase phase, T result, Throwable error, double progress, String message) {

        /** Returns whether an execution occupies this Command's worker slot. */
        public boolean running() {
            return phase == Phase.RUNNING;
        }

        private static <T> State<T> of(Phase phase) {
            return new State<>(phase, null, null, -1, "");
        }
    }

    /** The Command shared by controls, FXML bindings and keyboard actions. */
    @Getter
    private final Command command;
    private final ReadOnlyObjectWrapper<State<T>> state = new ReadOnlyObjectWrapper<>(State.of(Phase.IDLE));
    private final Executor executor;
    private final Supplier<? extends Task<T>> tasks;
    private final boolean cancellable;
    private Execution current;

    /**
     * Creates a command with no user cancellation.
     *
     * @param available the application's availability rule
     * @param executor the executor that runs tasks outside the FX application thread
     * @param tasks factory invoked on the FX application thread to supply a fresh, ready task
     */
    public AsyncCommand(ObservableBooleanValue available, Executor executor, Supplier<? extends Task<T>> tasks) {
        this(available, executor, tasks, false);
    }

    /**
     * Creates a command with optional cancellation. A cancellable Task must cooperate with
     * interruption and implement any application-specific rollback in its worker.
     *
     * @param available the application's availability rule
     * @param executor the executor that runs tasks outside the FX application thread
     * @param tasks factory invoked on the FX application thread to supply a fresh, ready task
     * @param cancellable whether {@link #cancel()} may cancel a running execution
     */
    public AsyncCommand(ObservableBooleanValue available, Executor executor, Supplier<? extends Task<T>> tasks,
                        boolean cancellable) {
        Objects.requireNonNull(available);
        this.executor = Objects.requireNonNull(executor);
        this.tasks = Objects.requireNonNull(tasks);
        this.cancellable = cancellable;
        var canStart = Bindings.createBooleanBinding(() -> available.get() && !state.get().running()
                && state.get().phase() != Phase.CLOSED, available, state);
        command = new Command(canStart, this::start);
    }

    /** Returns the current observation of the command. */
    public State<T> getState() {
        return state.get();
    }

    /** Returns a read-only property for observing progress and completion together. */
    public ReadOnlyObjectProperty<State<T>> stateProperty() {
        return state.getReadOnlyProperty();
    }

    /**
     * Requests cancellation if this Command allows it. The worker slot stays occupied until
     * the Task's worker has left, including when it needs time to handle interruption.
     *
     * @return whether a cancellation request was accepted
     */
    public boolean cancel() {
        var execution = current;
        if (!cancellable || execution == null) return false;
        if (execution.task != null) return execution.task.cancel();
        finish(execution, State.of(Phase.CANCELLED));
        return true;
    }

    /**
     * Ends observation and disables the Command. A running task retains its own lifetime.
     * If a View owns cancellable work, call {@link #cancel()} explicitly before closing.
     */
    @Override
    public void close() {
        if (getState().phase() == Phase.CLOSED) return;
        var execution = current;
        current = null;
        if (execution != null) execution.detach();
        state.set(State.of(Phase.CLOSED));
    }

    private void start() {
        if (current != null || getState().phase() == Phase.CLOSED) return;
        var execution = new Execution();
        current = execution;
        state.set(State.of(Phase.RUNNING));
        if (current != execution) return;
        try {
            var task = Objects.requireNonNull(tasks.get(), "Task factory returned null");
            if (current != execution) return;
            if (task.getState() != Worker.State.READY) {
                throw new IllegalArgumentException("Task factory must return a fresh Task");
            }
            execution.attach(task);
            executor.execute(execution::run);
        } catch (RuntimeException failure) {
            finish(execution, new State<>(Phase.FAILED, null, failure, -1, ""));
        }
    }

    private void finish(Execution execution, State<T> terminal) {
        if (current != execution) return;
        current = null;
        execution.detach();
        state.set(terminal);
    }

    /** Identifies one execution, with FX-thread observations and a worker-thread completion flag. */
    private final class Execution {

        private Task<T> task;
        private final AtomicBoolean working = new AtomicBoolean();
        private final InvalidationListener progress = ignored -> {
            if (current == this) state.set(observation(Phase.RUNNING, null, null));
        };
        private final ChangeListener<Worker.State> phase = (observable, before, after) -> {
            if (current != this) return;
            switch (after) {
                case SUCCEEDED -> finish(this, observation(Phase.SUCCEEDED, task.getValue(), null));
                case FAILED -> finish(this, observation(Phase.FAILED, null, task.getException()));
                case CANCELLED -> finishCancellation();
                default -> { }
            }
        };

        private void attach(Task<T> started) {
            task = started;
            started.progressProperty().addListener(progress);
            started.messageProperty().addListener(progress);
            started.stateProperty().addListener(phase);
        }

        private void run() {
            working.set(true);
            try {
                task.run();
            } finally {
                working.set(false);
                if (task.isCancelled()) Platform.runLater(this::finishCancellation);
            }
        }

        private void finishCancellation() {
            if (current == this && !working.get()) {
                finish(this, observation(Phase.CANCELLED, null, null));
            }
        }

        private State<T> observation(Phase phase, T result, Throwable error) {
            return new State<>(phase, result, error, task.getProgress(), task.getMessage());
        }

        private void detach() {
            if (task != null) {
                task.progressProperty().removeListener(progress);
                task.messageProperty().removeListener(progress);
                task.stateProperty().removeListener(phase);
            }
        }
    }
}
