package de.neitzel.grid;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Explicit, typed candidate lookup. A session owns its snapshot and pending lookups. */
public final class FilterCandidates {
    private FilterCandidates() {}

    public enum Scope {
        CURRENT_PAGE,
        SOURCE_WIDE
    }

    /** A null value explicitly represents missing; labels never determine equality. */
    public record Candidate(FilterValue value, String label) {
        public Candidate {
            Objects.requireNonNull(label);
        }
    }

    public record Context(String columnId, Scope scope, List<FilterCondition> otherFilters) {
        public Context {
            Checks.id(columnId);
            Objects.requireNonNull(scope);
            otherFilters = List.copyOf(otherFilters);
            if (otherFilters.stream().anyMatch(f -> f.columnId().equals(columnId)))
                throw new IllegalArgumentException("Candidate context must exclude its own filter");
        }
    }

    public record Request(Context context, String search, int offset, int limit) {
        public Request {
            Objects.requireNonNull(context);
            Objects.requireNonNull(search);
            if (offset < 0 || limit < 1)
                throw new IllegalArgumentException("Invalid candidate batch");
        }
    }

    public record Batch(List<Candidate> candidates, boolean hasMore) {
        public Batch {
            candidates = List.copyOf(candidates);
        }
    }

    public interface Provider {
        Set<Scope> scopes();

        Session open(Context context);
    }

    public interface Session extends AutoCloseable {
        CompletionStage<Batch> load(Request request);

        @Override
        void close();
    }

    /** Captures current-page values at open, never claims source-wide completeness. */
    public static Provider currentPage(Supplier<List<Candidate>> values) {
        return snapshot(
                Set.of(Scope.CURRENT_PAGE),
                c -> {
                    var copy = List.copyOf(values.get());
                    return () -> copy;
                },
                Runnable::run);
    }

    /** Capture on open's thread; evaluate the captured snapshot on the supplied executor. */
    public static Provider snapshot(
            Set<Scope> scopes,
            Function<Context, Supplier<List<Candidate>>> capture,
            Executor executor) {
        Set<Scope> supported = Set.copyOf(scopes);
        Objects.requireNonNull(capture);
        Objects.requireNonNull(executor);
        return new Provider() {
            public Set<Scope> scopes() {
                return supported;
            }

            public Session open(Context context) {
                if (!supported.contains(context.scope()))
                    throw new UnsupportedOperationException(
                            "Candidate scope unavailable: " + context.scope());
                var captured =
                        new java.util.concurrent.atomic.AtomicReference<>(
                                Objects.requireNonNull(capture.apply(context)));
                return new Session() {
                    private boolean closed;
                    private final Set<CompletableFuture<Batch>> pending = new HashSet<>();

                    public synchronized CompletionStage<Batch> load(Request request) {
                        if (closed) throw new IllegalStateException("Candidate session closed");
                        if (!context.equals(request.context()))
                            throw new IllegalArgumentException("Wrong candidate context");
                        var future = new CompletableFuture<Batch>();
                        pending.add(future);
                        try {
                            executor.execute(
                                    () -> {
                                        try {
                                            if (future.isDone()) return;
                                            Map<FilterValue, Candidate> distinct =
                                                    new LinkedHashMap<>();
                                            var work = captured.get();
                                            if (work == null) {
                                                future.cancel(false);
                                                return;
                                            }
                                            for (var candidate : work.get()) {
                                                if (future.isDone()) return;
                                                if (candidate
                                                        .label()
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(
                                                                request.search()
                                                                        .toLowerCase(Locale.ROOT)))
                                                    distinct.putIfAbsent(
                                                            candidate.value(), candidate);
                                            }
                                            var found = new ArrayList<>(distinct.values());
                                            int from = Math.min(request.offset(), found.size());
                                            int to =
                                                    (int)
                                                            Math.min(
                                                                    (long) from + request.limit(),
                                                                    found.size());
                                            future.complete(
                                                    new Batch(
                                                            found.subList(from, to),
                                                            to < found.size()));
                                        } catch (RuntimeException error) {
                                            future.completeExceptionally(error);
                                        } finally {
                                            synchronized (this) {
                                                pending.remove(future);
                                            }
                                        }
                                    });
                        } catch (RuntimeException error) {
                            pending.remove(future);
                            future.completeExceptionally(error);
                        }
                        return future;
                    }

                    public synchronized void close() {
                        closed = true;
                        captured.set(null);
                        for (var future : pending) future.cancel(false);
                        pending.clear();
                    }
                };
            }
        };
    }
}
