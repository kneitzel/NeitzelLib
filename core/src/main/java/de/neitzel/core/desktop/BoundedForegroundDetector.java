package de.neitzel.core.desktop;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** One daemon worker bounds caller wait and prevents overlapping or accumulating native queries. */
final class BoundedForegroundDetector implements ForegroundDetector {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "desktop-observation"); thread.setDaemon(true); return thread;
    });
    private final Supplier<ForegroundDetector> factory;
    private ForegroundDetector delegate;
    private Future<?> active;
    private boolean closed;

    BoundedForegroundDetector(Supplier<ForegroundDetector> factory) { this.factory = factory; }

    public synchronized Snapshot snapshot() {
        return query(() -> detector().snapshot(), new Snapshot(null, null, false, limitation()));
    }

    public synchronized Inventory inventory() {
        return query(() -> detector().inventory(), new Inventory(List.of(), List.of(), false, limitation()));
    }

    private String limitation() {
        return closed ? "Foreground detection is closed." : "Desktop observation is unavailable or timed out; use Manual mode.";
    }

    private ForegroundDetector detector() {
        if (delegate == null) delegate = factory.get();
        return delegate;
    }

    private <T> T query(Callable<T> operation, T unavailable) {
        if (closed || (active != null && !active.isDone())) return unavailable;
        Future<T> next = worker.submit(operation); active = next;
        try { return next.get(1500, TimeUnit.MILLISECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return unavailable; }
        catch (ExecutionException | TimeoutException failed) { return unavailable; }
    }

    public synchronized void close() {
        if (closed) return;
        closed = true;
        // Native queries own/release their per-call resources. A stuck native call cannot queue more work.
        worker.shutdownNow();
    }
}
