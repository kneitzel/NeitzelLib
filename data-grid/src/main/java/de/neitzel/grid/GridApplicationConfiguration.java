package de.neitzel.grid;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Application-owned, toolkit-independent save coordination. Accept immutable snapshots on the owner
 * thread; this service serializes blocking store work on its scheduled worker. No I/O runs in
 * construction. A detached view does not own or discard already accepted saves.
 */
public final class GridApplicationConfiguration {
    public enum Phase {
        IDLE,
        LOADING,
        PENDING,
        SAVING,
        SAVED,
        ERROR,
        CONFLICT,
        CLOSED
    }

    public record State(Phase phase, long generation, boolean dirty, Throwable error) {}

    public record Loaded(
            GridSetupStore.Key key, GridSetupStore.Stored stored, long storageVersion) {}

    private record Pending(long generation, GridSetup setup, boolean overwrite) {}

    private static final class Slot {
        final java.util.concurrent.locks.ReentrantLock io =
                new java.util.concurrent.locks.ReentrantLock();
        String revision;
        long generation, storageVersion;
        Pending pending;
        ScheduledFuture<?> delay;
        State state = new State(Phase.IDLE, 0, false, null);
        final List<Consumer<State>> listeners = new ArrayList<>();
    }

    @lombok.Getter
    @lombok.experimental.Accessors(fluent = true)
    private final String applicationName;

    private final GridSetupStore store;
    private final ScheduledExecutorService worker;
    private final boolean ownsWorker;
    private final long debounceMillis;
    private final Map<GridSetupStore.Key, Slot> slots = new LinkedHashMap<>();
    private boolean closing, closed;

    public GridApplicationConfiguration(String applicationName, GridSetupStore store) {
        this(
                applicationName,
                store,
                Duration.ofMillis(500),
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread thread = new Thread(r, "grid-setup-storage");
                    thread.setDaemon(true);
                    return thread;
                }),
                true);
    }

    public GridApplicationConfiguration(
            String applicationName,
            GridSetupStore store,
            Duration debounce,
            ScheduledExecutorService worker,
            boolean ownsWorker) {
        if (applicationName == null
                || applicationName.isBlank()
                || applicationName.equals(".")
                || applicationName.equals("..")
                || applicationName.contains("/")
                || applicationName.contains("\\")
                || applicationName.indexOf(0) >= 0)
            throw new IllegalArgumentException("Application name must be a directory name");
        this.applicationName = applicationName;
        this.store = Objects.requireNonNull(store);
        debounceMillis = Objects.requireNonNull(debounce).toMillis();
        if (debounceMillis < 0) throw new IllegalArgumentException("Negative debounce");
        this.worker = Objects.requireNonNull(worker);
        this.ownsWorker = ownsWorker;
    }

    private Slot slot(GridSetupStore.Key key) {
        return slots.computeIfAbsent(key, k -> new Slot());
    }

    private void requireOpen() {
        if (closed || closing) throw new IllegalStateException("Configuration is closing/closed");
    }

    public synchronized State state(GridSetupStore.Key key) {
        return slot(key).state;
    }

    /** Listener callbacks may run on the storage thread; an FX adapter must dispatch them. */
    public synchronized AutoCloseable observe(GridSetupStore.Key key, Consumer<State> listener) {
        requireOpen();
        var s = slot(key);
        s.listeners.add(listener);
        listener.accept(s.state);
        return () -> {
            synchronized (this) {
                s.listeners.remove(listener);
            }
        };
    }

    private void emit(Slot slot, Phase phase, Throwable error) {
        slot.state = new State(phase, slot.generation, slot.pending != null, error);
        for (var listener : List.copyOf(slot.listeners)) listener.accept(slot.state);
    }

    public CompletionStage<Loaded> loadAsync(GridSetupStore.Key key) {
        synchronized (this) {
            requireOpen();
            emit(slot(key), Phase.LOADING, null);
        }
        return submit(() -> {
            Slot target;
            synchronized (this) {
                target = slot(key);
            }
            target.io.lock();
            try {
                GridSetupStore.Stored loaded = store.load(key);
                synchronized (this) {
                    return new Loaded(key, loaded, target.storageVersion);
                }
            } catch (Exception failure) {
                synchronized (this) {
                    emit(target, Phase.ERROR, failure);
                }
                throw failure;
            } finally {
                target.io.unlock();
            }
        });
    }

    /** Called only after the grid has successfully validated/published this loaded setup. */
    public synchronized void adopt(Loaded loaded, long discardedOwnGeneration) {
        requireOpen();
        Slot s = slot(loaded.key());
        if (s.storageVersion == loaded.storageVersion()) s.revision = loaded.stored().revision();
        if (s.pending != null && s.pending.generation() == discardedOwnGeneration) {
            s.pending = null;
            if (s.delay != null) s.delay.cancel(false);
        }
        emit(s, s.pending == null ? Phase.SAVED : Phase.PENDING, null);
    }

    public synchronized long accept(GridSetupStore.Key key, GridSetup setup, Object owner) {
        return accept(key, setup, owner, false);
    }

    public synchronized long acceptOverwrite(
            GridSetupStore.Key key, GridSetup setup, Object owner) {
        return accept(key, setup, owner, true);
    }

    private long accept(GridSetupStore.Key key, GridSetup setup, Object owner, boolean overwrite) {
        requireOpen();
        Slot s = slot(key);
        // Owner is an attribution token, never retained: it may be a closed UI view.
        Objects.requireNonNull(owner);
        s.pending = new Pending(++s.generation, Objects.requireNonNull(setup), overwrite);
        if (s.delay != null) s.delay.cancel(false);
        emit(s, Phase.PENDING, null);
        long acceptedGeneration = s.generation;
        s.delay =
                worker.schedule(
                        () -> {
                            try {
                                write(key, acceptedGeneration);
                            } catch (Exception ignored) {
                                /* retained and exposed through state */
                            }
                        },
                        debounceMillis,
                        TimeUnit.MILLISECONDS);
        return s.generation;
    }

    /** Explicit user recovery: still performs a conditional write against a fresh revision. */
    public CompletionStage<Void> overwriteAsync(
            GridSetupStore.Key key, GridSetup setup, Object owner) {
        synchronized (this) {
            accept(key, setup, owner, true);
        }
        return retryAsync(key);
    }

    public CompletionStage<Void> retryAsync(GridSetupStore.Key key) {
        synchronized (this) {
            requireOpen();
            Slot s = slot(key);
            if (s.delay != null) s.delay.cancel(false);
        }
        return submit(() -> {
            write(key);
            return null;
        });
    }

    private void write(GridSetupStore.Key key) throws Exception {
        write(key, -1);
    }

    private void write(GridSetupStore.Key key, long scheduledGeneration) throws Exception {
        Slot target;
        synchronized (this) {
            target = slot(key);
        }
        target.io.lock();
        try {
            writeLocked(key, scheduledGeneration);
        } finally {
            target.io.unlock();
        }
    }

    private void writeLocked(GridSetupStore.Key key, long scheduledGeneration) throws Exception {
        Pending pending;
        String expected;
        synchronized (this) {
            Slot s = slot(key);
            pending = s.pending;
            if (pending == null
                    || closed
                    || (scheduledGeneration >= 0 && pending.generation() != scheduledGeneration))
                return;
            expected = s.revision;
            emit(s, Phase.SAVING, null);
        }
        try {
            if (pending.overwrite()) expected = store.revision(key);
            if (expected == null)
                throw new IllegalStateException(
                        "Load or explicitly reset the saved setup before writing");
            String revision = store.save(key, expected, pending.setup());
            synchronized (this) {
                Slot s = slot(key);
                s.revision = revision;
                s.storageVersion++;
                if (s.pending != null && s.pending.generation() == pending.generation())
                    s.pending = null;
                emit(s, s.pending == null ? Phase.SAVED : Phase.PENDING, null);
            }
        } catch (Exception failure) {
            synchronized (this) {
                emit(
                        slot(key),
                        failure instanceof GridSetupStore.Conflict ? Phase.CONFLICT : Phase.ERROR,
                        failure);
            }
            throw failure;
        }
    }

    public CompletionStage<Void> flushAsync() {
        synchronized (this) {
            if (closed) return CompletableFuture.completedFuture(null);
            cancelDelays();
        }
        return submit(() -> {
            flush();
            return null;
        });
    }

    private void cancelDelays() {
        slots.values()
                .forEach(s -> {
                    if (s.delay != null) s.delay.cancel(false);
                });
    }

    private void flush() throws Exception {
        while (true) {
            List<GridSetupStore.Key> pending;
            synchronized (this) {
                pending =
                        slots.entrySet().stream()
                                .filter(e -> e.getValue().pending != null)
                                .map(Map.Entry::getKey)
                                .toList();
            }
            if (pending.isEmpty()) return;
            for (var key : pending) write(key);
        }
    }

    public CompletionStage<Void> closeAsync() {
        synchronized (this) {
            if (closed) return CompletableFuture.completedFuture(null);
            if (closing) throw new IllegalStateException("Close already pending");
            closing = true;
            cancelDelays();
        }
        return submit(() -> {
            try {
                flush();
                finishClose();
                return null;
            } catch (Exception failure) {
                synchronized (this) {
                    closing = false;
                }
                throw failure;
            }
        });
    }

    /** Explicit quit-without-saving path. It cannot undo a write already committed. */
    public CompletionStage<Void> discardAndCloseAsync() {
        synchronized (this) {
            if (closed) return CompletableFuture.completedFuture(null);
            closing = true;
            cancelDelays();
            slots.values().forEach(s -> s.pending = null);
        }
        return submit(() -> {
            finishClose();
            return null;
        });
    }

    private void finishClose() {
        // A supplied scheduler may have multiple workers. Drain all in-flight slot I/O
        // before publishing CLOSED, including an already-committing discarded write.
        List<Slot> targets;
        synchronized (this) {
            targets = List.copyOf(slots.values());
        }
        targets.forEach(s -> s.io.lock());
        try {
            finishCloseLocked();
        } finally {
            for (int index = targets.size() - 1; index >= 0; index--)
                targets.get(index).io.unlock();
        }
    }

    private void finishCloseLocked() {
        synchronized (this) {
            closed = true;
            closing = false;
            slots.values()
                    .forEach(s -> {
                        emit(s, Phase.CLOSED, null);
                        s.listeners.clear();
                    });
        }
        if (ownsWorker) worker.shutdown();
    }

    private <V> CompletionStage<V> submit(Callable<V> action) {
        CompletableFuture<V> future = new CompletableFuture<>();
        try {
            worker.execute(() -> {
                try {
                    future.complete(action.call());
                } catch (Exception failure) {
                    future.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException failure) {
            future.completeExceptionally(failure);
        }
        return future;
    }
}
