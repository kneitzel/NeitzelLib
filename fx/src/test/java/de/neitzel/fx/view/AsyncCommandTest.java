package de.neitzel.fx.view;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static de.neitzel.fx.view.AsyncCommand.Phase.*;
import static org.junit.jupiter.api.Assertions.*;

class AsyncCommandTest {

    @BeforeAll
    static void start() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @Test
    void publishesRunningBeforeSchedulingAndReportsProgressAndOneResult() throws Exception {
        var available = new SimpleBooleanProperty(false);
        var queue = new QueueExecutor();
        var command = new AtomicReference<AsyncCommand<String>>();
        var states = new ArrayList<AsyncCommand.State<String>>();
        var calls = new AtomicInteger();

        FxThread.onFx(() -> {
            var async = new AsyncCommand<>(available, action -> {
                assertEquals(RUNNING, command.get().getState().phase());
                queue.execute(action);
            }, () -> new Task<String>() {
                @Override
                protected String call() {
                    calls.incrementAndGet();
                    updateMessage("Saving");
                    updateProgress(1, 2);
                    return "saved";
                }
            });
            command.set(async);
            async.stateProperty().addListener((source, previous, next) -> {
                assertTrue(Platform.isFxApplicationThread());
                states.add(next);
            });
            async.getCommand().execute();
            assertEquals(IDLE, async.getState().phase());

            available.set(true);
            async.getCommand().execute();
            async.getCommand().execute();
            assertFalse(async.cancel());
            assertFalse(async.getCommand().getCanExecute().get());
            assertEquals(1, queue.size());
        });

        runWorker(queue.remove());
        FxThread.onFx(() -> {
            var async = command.get();
            assertEquals(1, calls.get());
            assertTrue(states.stream().anyMatch(state -> state.running() && state.message().equals("Saving")));
            assertEquals(1, states.stream().filter(state -> state.phase() == SUCCEEDED).count());
            assertEquals(new AsyncCommand.State<>(SUCCEEDED, "saved", null, 0.5, "Saving"), async.getState());
            assertTrue(async.getCommand().getCanExecute().get());
            async.close();
        });
    }

    @Test
    void reportsSynchronousAndWorkerFailuresAndAllowsRetry() throws Exception {
        var queue = new QueueExecutor();
        var command = new AtomicReference<AsyncCommand<String>>();
        var starts = new AtomicInteger();
        var failures = new ArrayList<Throwable>();
        var factoryFailure = new IllegalArgumentException("factory");
        var schedulingFailure = new RejectedExecutionException("executor");
        var operationFailure = new IllegalStateException("worker");
        FxThread.onFx(() -> {
            var async = new AsyncCommand<>(new SimpleBooleanProperty(true), work -> {
                if (starts.get() == 2) throw schedulingFailure;
                queue.execute(work);
            }, () -> switch (starts.incrementAndGet()) {
                case 1 -> throw factoryFailure;
                case 2 -> task(() -> "not scheduled");
                case 3 -> task(() -> { throw operationFailure; });
                default -> task(() -> "retry");
            });
            command.set(async);
            async.stateProperty().addListener((source, previous, next) -> {
                if (next.phase() == FAILED) failures.add(next.error());
            });
            async.getCommand().execute();
            assertSame(factoryFailure, async.getState().error());
            async.getCommand().execute();
            assertSame(schedulingFailure, async.getState().error());
            async.getCommand().execute();
        });
        runWorker(queue.remove());
        FxThread.onFx(() -> {
            assertEquals(List.of(factoryFailure, schedulingFailure, operationFailure), failures);
            assertSame(operationFailure, command.get().getState().error());
            command.get().getCommand().execute();
        });
        runWorker(queue.remove());
        FxThread.onFx(() -> {
            assertEquals("retry", command.get().getState().result());
            command.get().close();
        });
    }

    @Test
    void cancellationKeepsSlotUntilTheWorkerLeavesAndPublishesOneCompletion() throws Exception {
        var queue = new QueueExecutor();
        var command = new AtomicReference<AsyncCommand<String>>();
        var entered = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var cancellations = new AtomicInteger();
        FxThread.onFx(() -> {
            var async = new AsyncCommand<>(new SimpleBooleanProperty(true), queue, () -> task(() -> {
                entered.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException cancellation) {
                    interrupted.countDown();
                    assertTrue(release.await(5, TimeUnit.SECONDS), "waiting to clean up after interrupt");
                }
                return "ignored";
            }), true);
            command.set(async);
            async.stateProperty().addListener((source, previous, next) -> {
                if (next.phase() == CANCELLED) cancellations.incrementAndGet();
            });
            async.getCommand().execute();
        });
        var worker = Thread.ofVirtual().start(queue.remove());
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            FxThread.onFx(() -> assertTrue(command.get().cancel()));
            assertTrue(interrupted.await(5, TimeUnit.SECONDS));
            FxThread.onFx(() -> {
                var async = command.get();
                assertTrue(async.getState().running());
                async.getCommand().execute();
                assertEquals(0, queue.size());
            });
        } finally {
            release.countDown();
            worker.join(5000);
        }
        FxThread.onFx(() -> {
            var async = command.get();
            assertEquals(CANCELLED, async.getState().phase());
            assertEquals(1, cancellations.get());
            assertTrue(async.getCommand().getCanExecute().get());
            async.close();
        });
    }

    @Test
    void oldCancellationCannotOverwriteANewerRunAndCloseDoesNotStopItsWorker() throws Exception {
        var queue = new QueueExecutor();
        var command = new AtomicReference<AsyncCommand<String>>();
        var calls = new AtomicInteger();
        var phases = new ArrayList<AsyncCommand.Phase>();
        FxThread.onFx(() -> {
            var async = new AsyncCommand<>(new SimpleBooleanProperty(true), queue,
                    () -> task(() -> "run " + calls.incrementAndGet()), true);
            command.set(async);
            async.stateProperty().addListener((source, previous, next) -> phases.add(next.phase()));
            async.getCommand().execute();
            assertTrue(async.cancel());
            assertEquals(CANCELLED, async.getState().phase());
            async.getCommand().execute();
        });
        runWorker(queue.remove());
        FxThread.onFx(() -> {
            var async = command.get();
            assertEquals(RUNNING, async.getState().phase());
            assertEquals(0, calls.get());
            async.close();
            async.close();
            async.getCommand().execute();
            assertFalse(async.cancel());
            assertFalse(async.getCommand().getCanExecute().get());
        });
        runWorker(queue.remove());
        FxThread.onFx(() -> {
            assertEquals(1, calls.get());
            assertEquals(List.of(RUNNING, CANCELLED, RUNNING, CLOSED), phases);
            assertEquals(CLOSED, command.get().getState().phase());
        });
    }

    @Test
    void aGeneratedDialogButtonClosesOnlyAfterASuccessfulTask() throws Exception {
        var queue = new QueueExecutor();
        var dialogRef = new AtomicReference<Dialog<Boolean>>();
        var saveButtonRef = new AtomicReference<Button>();
        FxThread.onFx(() -> {
            var dialog = new Dialog<Boolean>();
            dialogRef.set(dialog);
            var save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().setAll(save, ButtonType.CANCEL);
            dialog.setResultConverter(button -> false);
            var async = new AsyncCommand<>(new SimpleBooleanProperty(true), queue, () -> task(() -> "saved"));
            var button = (Button) dialog.getDialogPane().lookupButton(save);
            saveButtonRef.set(button);
            button.disableProperty().bind(Bindings.not(async.getCommand().getCanExecute()));
            button.addEventFilter(ActionEvent.ACTION, event -> {
                event.consume();
                async.getCommand().execute();
            });
            dialog.setOnCloseRequest(event -> {
                if (async.getState().running()) event.consume();
            });
            async.stateProperty().addListener((source, previous, state) -> {
                if (state.phase() == SUCCEEDED) {
                    dialog.setResult(true);
                    dialog.close();
                }
            });
            dialog.setOnHidden(event -> async.close());
            dialog.show();

            button.fire();
            assertTrue(dialog.isShowing());
            assertTrue(button.isDisable());
            assertTrue(async.getState().running());
        });

        runWorker(queue.remove());
        FxThread.onFx(() -> {
            assertFalse(dialogRef.get().isShowing());
            assertEquals(Boolean.TRUE, dialogRef.get().getResult());
            assertTrue(saveButtonRef.get().isDisable());
        });
    }

    private static Task<String> task(Callable<String> work) {
        return new Task<>() {
            @Override
            protected String call() throws Exception {
                return work.call();
            }
        };
    }

    private static void runWorker(Runnable job) throws InterruptedException {
        var worker = Thread.ofVirtual().start(job);
        worker.join(5000);
        assertFalse(worker.isAlive());
    }

    private static final class QueueExecutor implements Executor {
        private final ArrayDeque<Runnable> work = new ArrayDeque<>();

        @Override
        public void execute(Runnable action) {
            work.addLast(action);
        }

        int size() {
            return work.size();
        }

        Runnable remove() {
            return work.removeFirst();
        }
    }
}
