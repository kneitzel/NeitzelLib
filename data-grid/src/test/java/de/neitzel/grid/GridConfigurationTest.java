package de.neitzel.grid;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

class GridConfigurationTest {
    static GridSetup setup(double width) {
        return new GridSetup(
                2,
                "records",
                1,
                List.of(new GridSetup.ColumnState("type", "text", new ColumnWidth.Explicit(width))),
                List.of(),
                List.of(),
                new PageRequest.Paged(0, 50),
                new GroupingSetup(List.of("type"), List.of()));
    }

    static final GridSetupStore.Key KEY = new GridSetupStore.Key("records", null);

    static final class MemoryStore implements GridSetupStore {
        GridSetup value;
        int revision, writes;
        boolean fail;

        public synchronized Stored load(Key key) {
            return new Stored(value, Integer.toString(revision));
        }

        public synchronized String revision(Key key) {
            return Integer.toString(revision);
        }

        public synchronized String save(Key key, String expected, GridSetup setup)
                throws IOException {
            if (fail) throw new IOException("disk unavailable");
            if (!expected.equals(Integer.toString(revision))) throw new Conflict();
            value = setup;
            writes++;
            return Integer.toString(++revision);
        }
    }

    static GridApplicationConfiguration config(MemoryStore store) {
        return new GridApplicationConfiguration(
                "Example",
                store,
                Duration.ofHours(1),
                Executors.newSingleThreadScheduledExecutor(),
                true);
    }

    static void load(GridApplicationConfiguration config) throws Exception {
        config.adopt(config.loadAsync(KEY).toCompletableFuture().get(5, TimeUnit.SECONDS), -1);
    }

    @Test
    void coalescesWholeSnapshotsAndDoesNotSaveUnchangedLoadsOrClose() throws Exception {
        var store = new MemoryStore();
        var config = config(store);
        load(config);
        assertEquals(0, store.writes);
        config.accept(KEY, setup(100), new Object());
        config.accept(KEY, setup(200), new Object());
        config.flushAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(setup(200), store.value);
        assertEquals(1, store.writes);
        config.closeAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(1, store.writes);
    }

    @Test
    void failedCloseRetainsLatestUnsavedStateAndCanRetry() throws Exception {
        var store = new MemoryStore();
        var config = config(store);
        load(config);
        config.accept(KEY, setup(100), this);
        config.flushAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
        store.fail = true;
        config.accept(KEY, setup(200), this);
        assertThrows(
                ExecutionException.class,
                () -> config.closeAsync().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals(setup(100), store.value);
        assertTrue(config.state(KEY).dirty());
        store.fail = false;
        config.retryAsync(KEY).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(setup(200), store.value);
        config.closeAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test
    void reloadCannotDiscardAnotherViewsNewerGeneration() throws Exception {
        var store = new MemoryStore();
        var config = config(store);
        load(config);
        long old = config.accept(KEY, setup(100), this);
        var read = config.loadAsync(KEY).toCompletableFuture().get(5, TimeUnit.SECONDS);
        config.accept(KEY, setup(200), new Object());
        config.adopt(read, old);
        assertTrue(config.state(KEY).dirty());
        config.flushAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(setup(200), store.value);
        config.closeAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test
    void crossInstanceConflictRequiresExplicitOverwriteAndDiscardCloseDoesNotWrite()
            throws Exception {
        var store = new MemoryStore();
        var first = config(store);
        var second = config(store);
        load(first);
        load(second);
        first.accept(KEY, setup(100), first);
        first.flushAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
        second.accept(KEY, setup(200), second);
        assertThrows(
                ExecutionException.class,
                () -> second.flushAsync().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals(GridApplicationConfiguration.Phase.CONFLICT, second.state(KEY).phase());
        second.overwriteAsync(KEY, setup(200), second)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        assertEquals(setup(200), store.value);
        second.accept(KEY, setup(300), second);
        second.discardAndCloseAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(setup(200), store.value);
        first.closeAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test
    void debounceWaitsForQuietPeriodRatherThanSavingEveryChange() throws Exception {
        var store = new MemoryStore();
        var clock = new Clock();
        var config =
                new GridApplicationConfiguration(
                        "Example", store, Duration.ofMillis(500), clock, false);
        load(config);
        config.accept(KEY, setup(100), this);
        clock.advance(499);
        assertEquals(0, store.writes);
        config.accept(KEY, setup(200), this);
        clock.advance(1);
        assertEquals(0, store.writes);
        clock.advance(499);
        assertEquals(1, store.writes);
        assertEquals(setup(200), store.value);
        config.closeAsync().toCompletableFuture().get();
    }

    @Test
    void discardCloseWaitsForAnAlreadyCommittingWriteOnAParallelScheduler() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var closed = new CountDownLatch(1);
        var backing = new MemoryStore();
        GridSetupStore store =
                new GridSetupStore() {
                    public Stored load(Key key) {
                        return backing.load(key);
                    }

                    public String revision(Key key) {
                        return backing.revision(key);
                    }

                    public String save(Key key, String revision, GridSetup setup)
                            throws IOException {
                        entered.countDown();
                        try {
                            if (!release.await(5, TimeUnit.SECONDS))
                                throw new IOException("Timed out");
                        } catch (InterruptedException failure) {
                            Thread.currentThread().interrupt();
                            throw new IOException(failure);
                        }
                        return backing.save(key, revision, setup);
                    }
                };
        var worker = Executors.newScheduledThreadPool(2);
        var config =
                new GridApplicationConfiguration(
                        "Example", store, Duration.ofHours(1), worker, true);
        load(config);
        config.observe(
                KEY,
                state -> {
                    if (state.phase() == GridApplicationConfiguration.Phase.CLOSED)
                        closed.countDown();
                });
        config.accept(KEY, setup(100), this);
        var write = config.retryAsync(KEY);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        var close = config.discardAndCloseAsync();
        try {
            assertFalse(closed.await(100, TimeUnit.MILLISECONDS));
        } finally {
            release.countDown();
        }
        close.toCompletableFuture().get(5, TimeUnit.SECONDS);
        write.toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(GridApplicationConfiguration.Phase.CLOSED, config.state(KEY).phase());
        assertFalse(config.state(KEY).dirty());
    }

    /** Deterministic scheduled worker: no wall-clock sleeps in debounce assertions. */
    static final class Clock extends java.util.concurrent.AbstractExecutorService
            implements java.util.concurrent.ScheduledExecutorService {
        long now;
        boolean shutdown;
        final List<Job> jobs = new ArrayList<>();

        static final class Job extends java.util.concurrent.FutureTask<Void>
                implements java.util.concurrent.ScheduledFuture<Void> {
            final long due;

            Job(Runnable action, long due) {
                super(action, null);
                this.due = due;
            }

            public long getDelay(java.util.concurrent.TimeUnit unit) {
                return unit.convert(due, java.util.concurrent.TimeUnit.MILLISECONDS);
            }

            public int compareTo(java.util.concurrent.Delayed other) {
                return Long.compare(due, ((Job) other).due);
            }
        }

        void advance(long millis) {
            now += millis;
            var ready = jobs.stream().filter(job -> job.due <= now).toList();
            jobs.removeAll(ready);
            ready.forEach(Job::run);
        }

        public java.util.concurrent.ScheduledFuture<?> schedule(
                Runnable action, long delay, java.util.concurrent.TimeUnit unit) {
            var job = new Job(action, now + unit.toMillis(delay));
            jobs.add(job);
            return job;
        }

        public <V> java.util.concurrent.ScheduledFuture<V> schedule(
                java.util.concurrent.Callable<V> action,
                long delay,
                java.util.concurrent.TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        public java.util.concurrent.ScheduledFuture<?> scheduleAtFixedRate(
                Runnable r, long a, long b, java.util.concurrent.TimeUnit u) {
            throw new UnsupportedOperationException();
        }

        public java.util.concurrent.ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable r, long a, long b, java.util.concurrent.TimeUnit u) {
            throw new UnsupportedOperationException();
        }

        public void execute(Runnable command) {
            command.run();
        }

        public void shutdown() {
            shutdown = true;
        }

        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        public boolean isShutdown() {
            return shutdown;
        }

        public boolean isTerminated() {
            return shutdown;
        }

        public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) {
            return shutdown;
        }
    }
}
