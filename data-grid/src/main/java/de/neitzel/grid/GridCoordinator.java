package de.neitzel.grid;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/**
 * Serial, owner-thread coordinator. Supply an executor that dispatches callbacks to the creating
 * thread, always enqueueing rather than invoking inline (for JavaFX, Platform::runLater). It never
 * owns/shuts down sources or executors.
 */
public final class GridCoordinator<K, T> implements AutoCloseable {
    public enum Phase {
        IDLE,
        GUARD,
        LOADING,
        CONFIRMATION,
        READY,
        ERROR,
        CLOSED
    }

    public record State<K, T>(
            long generation,
            GridQuery requested,
            GridQuery displayed,
            PageResult<K, T> result,
            Phase phase,
            Throwable error,
            long sourceVersion) {}

    private final Thread owner = Thread.currentThread();
    private final Executor dispatch;
    private final Consumer<State<K, T>> observer;
    private final Consumer<PageResult<K, T>> publisher;
    private GridSource<K, T> source;
    private GridSource<K, T> requestedSource;
    private GridSource.QueryExecution<K, T> execution;
    private State<K, T> state = new State<>(0, null, null, null, Phase.IDLE, null, 0);
    private long generation, sourceVersion;
    private long threshold = 1000;
    private boolean closed;
    private boolean notifying;
    private boolean restoring;
    private State<K, T> restoreBefore;
    private Consumer<PageResult<K, T>> requestPublisher;
    private Function<GridQuery, CompletionStage<Boolean>> guard =
            q -> CompletableFuture.completedFuture(true);
    private BiFunction<GridQuery, Long, CompletionStage<Boolean>> confirmation =
            (q, n) ->
                    CompletableFuture.failedFuture(
                            new IllegalStateException("No All confirmation handler configured"));

    public GridCoordinator(
            GridSource<K, T> source,
            Executor dispatch,
            Consumer<State<K, T>> observer,
            Consumer<PageResult<K, T>> publisher) {
        this.source = Objects.requireNonNull(source);
        this.requestedSource = source;
        this.dispatch = Objects.requireNonNull(dispatch);
        this.observer = Objects.requireNonNull(observer);
        this.publisher = Objects.requireNonNull(publisher);
    }

    public State<K, T> state() {
        return state;
    }

    public SourceCapabilities capabilities() {
        check();
        return requestedSource.capabilities();
    }

    public String filterExecutionId() {
        check();
        return requestedSource.filterExecutionId();
    }

    public void validateFilters(List<FilterCondition> filters) {
        check();
        filters.forEach(requestedSource::validateFilter);
    }

    public void setGuard(Function<GridQuery, CompletionStage<Boolean>> guard) {
        check();
        this.guard = Objects.requireNonNull(guard);
    }

    public void setConfirmation(
            BiFunction<GridQuery, Long, CompletionStage<Boolean>> confirmation) {
        check();
        this.confirmation = Objects.requireNonNull(confirmation);
    }

    public void setAllWarningThreshold(long value) {
        check();
        if (value < 0) throw new IllegalArgumentException("Negative warning threshold");
        threshold = value;
    }

    public void request(GridQuery query) {
        request(requestedSource, query);
    }

    /** A replacement source becomes committed only after its guarded query succeeds. */
    public void request(GridSource<K, T> replacement, GridQuery query) {
        request(replacement, query, () -> {}, publisher, false);
    }

    /** A newer layout-only operation supersedes pending restoration without querying. */
    public void cancelRestore() {
        check();
        if (!restoring) return;
        if (notifying) {
            dispatch.execute(
                    () -> {
                        if (!closed) cancelRestore();
                    });
            return;
        }
        ++generation;
        cancelExecution();
        var before = restoreBefore;
        restore(before);
    }

    public boolean isRestoring() {
        return restoring;
    }

    /**
     * Validated restore: one guard, then one initial query, then atomic application publication.
     */
    public void restoreSetup(
            GridQuery query, Runnable afterGuard, Consumer<PageResult<K, T>> publish) {
        request(
                requestedSource,
                query,
                Objects.requireNonNull(afterGuard),
                Objects.requireNonNull(publish),
                true);
    }

    /** Report decode/preparation failure without querying or discarding displayed data. */
    public void rejectSetup(Throwable failure) {
        check();
        if (notifying) {
            dispatch.execute(
                    () -> {
                        if (!closed) rejectSetup(failure);
                    });
            return;
        }
        ++generation;
        cancelExecution();
        restoring = false;
        restoreBefore = null;
        requestPublisher = null;
        requestedSource = source;
        update(state.displayed(), Phase.ERROR, Objects.requireNonNull(failure));
    }

    private void request(
            GridSource<K, T> replacement,
            GridQuery query,
            Runnable afterGuard,
            Consumer<PageResult<K, T>> publish,
            boolean transactional) {
        check();
        Objects.requireNonNull(replacement);
        Objects.requireNonNull(query);
        if (notifying) {
            dispatch.execute(
                    () -> {
                        if (!closed)
                            request(replacement, query, afterGuard, publish, transactional);
                    });
            return;
        }
        long token = ++generation;
        cancelExecution();
        requestedSource = replacement;
        var before =
                new State<K, T>(
                        token,
                        state.displayed() != null ? state.displayed() : state.requested(),
                        state.displayed(),
                        state.result(),
                        state.result() == null ? Phase.IDLE : Phase.READY,
                        null,
                        sourceVersion);
        restoring = transactional;
        restoreBefore = transactional ? before : null;
        requestPublisher = publish;
        update(query, Phase.GUARD, null);
        if (!current(token)) return;
        try {
            replacement.capabilities().validate(query.page());
            await(
                    Objects.requireNonNull(guard.apply(query)),
                    token,
                    (allowed, failure) -> {
                        if (failure != null) {
                            fail(query, failure);
                            return;
                        }
                        if (!Boolean.TRUE.equals(allowed)) {
                            restore(before);
                            return;
                        }
                        try {
                            afterGuard.run();
                            if (current(token))
                                execute(replacement, query, token, false, false, before, threshold);
                        } catch (RuntimeException error) {
                            if (current(token)) fail(query, error);
                        }
                    });
        } catch (RuntimeException failure) {
            fail(query, failure);
        }
    }

    public void retry() {
        check();
        if (state.requested() != null) request(state.requested());
    }

    public void refresh() {
        retry();
    }

    private void execute(
            GridSource<K, T> target,
            GridQuery query,
            long token,
            boolean approved,
            boolean corrected,
            State<K, T> before,
            long attemptThreshold) {
        if (!current(token)) return;
        update(query, Phase.LOADING, null);
        if (!current(token)) return;
        try {
            execution =
                    Objects.requireNonNull(
                            target.query(query, new QueryOptions(attemptThreshold, approved)),
                            "Missing execution");
            await(
                    Objects.requireNonNull(execution.result()),
                    token,
                    (result, failure) -> {
                        execution = null;
                        if (failure != null) {
                            Throwable cause = unwrap(failure);
                            if (cause instanceof AllRowsConfirmationRequired required
                                    && query.page() instanceof PageRequest.All
                                    && !approved) {
                                update(query, Phase.CONFIRMATION, null);
                                if (!current(token)) return;
                                try {
                                    await(
                                            Objects.requireNonNull(
                                                    confirmation.apply(query, required.count())),
                                            token,
                                            (allowed, error) -> {
                                                if (error != null) fail(query, error);
                                                else if (Boolean.TRUE.equals(allowed))
                                                    execute(
                                                            target,
                                                            query,
                                                            token,
                                                            true,
                                                            corrected,
                                                            before,
                                                            attemptThreshold);
                                                else restore(before);
                                            });
                                } catch (RuntimeException error) {
                                    fail(query, error);
                                }
                            } else fail(query, cause);
                            return;
                        }
                        try {
                            Objects.requireNonNull(result, "Missing page result");
                            if (!query.page().equals(result.request()))
                                throw new IllegalArgumentException(
                                        "Source returned a different page request");
                            if (target.capabilities().exactTotal() && result.total().isEmpty())
                                throw new IllegalArgumentException(
                                        "Source promised an exact count");
                            if (query.page() instanceof PageRequest.Paged page
                                    && page.pageIndex() > 0
                                    && result.rows().isEmpty()
                                    && (result.total().isEmpty()
                                            || result.total().getAsLong() > 0)) {
                                if (corrected)
                                    throw new IllegalStateException(
                                            "Page changed again during recovery; retry");
                                long last =
                                        result.total().isPresent()
                                                ? (result.total().getAsLong() - 1) / page.pageSize()
                                                : 0;
                                execute(
                                        target,
                                        new GridQuery(
                                                query.filters(),
                                                query.sort(),
                                                new PageRequest.Paged(last, page.pageSize())),
                                        token,
                                        approved,
                                        true,
                                        before,
                                        attemptThreshold);
                                return;
                            }
                            notifying = true;
                            try {
                                requestPublisher.accept(result);
                            } finally {
                                notifying = false;
                            }
                            if (!current(token)) return;
                            if (source != target) {
                                source = target;
                                sourceVersion++;
                            }
                            restoring = false;
                            restoreBefore = null;
                            requestPublisher = null;
                            state =
                                    new State<>(
                                            generation,
                                            query,
                                            query,
                                            result,
                                            Phase.READY,
                                            null,
                                            sourceVersion);
                            notifyState();
                        } catch (RuntimeException invalid) {
                            fail(query, invalid);
                        }
                    });
        } catch (RuntimeException failure) {
            execution = null;
            fail(query, failure);
        }
    }

    private <V> void await(
            CompletionStage<V> stage, long token, BiConsumer<V, Throwable> callback) {
        stage.whenComplete(
                (value, failure) ->
                        dispatch.execute(
                                () -> {
                                    checkThread();
                                    if (current(token)) callback.accept(value, failure);
                                }));
    }

    private void restore(State<K, T> before) {
        restoring = false;
        restoreBefore = null;
        requestPublisher = null;
        requestedSource = source;
        state =
                new State<>(
                        generation,
                        before.requested(),
                        before.displayed(),
                        before.result(),
                        before.phase(),
                        null,
                        sourceVersion);
        notifyState();
    }

    private void update(GridQuery query, Phase phase, Throwable error) {
        state =
                new State<>(
                        generation,
                        query,
                        state.displayed(),
                        state.result(),
                        phase,
                        error,
                        sourceVersion);
        notifyState();
    }

    private void notifyState() {
        notifying = true;
        try {
            observer.accept(state);
        } finally {
            notifying = false;
        }
    }

    private void fail(GridQuery query, Throwable error) {
        if (closed) return;
        if (restoring) {
            query = restoreBefore.requested();
            requestedSource = source;
            restoring = false;
            restoreBefore = null;
        }
        requestPublisher = null;
        update(query, Phase.ERROR, unwrap(error));
    }

    private static Throwable unwrap(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException)
                && failure.getCause() != null) failure = failure.getCause();
        return failure;
    }

    private boolean current(long token) {
        return !closed && generation == token;
    }

    private void cancelExecution() {
        if (execution != null) {
            var old = execution;
            execution = null;
            try {
                old.cancel();
            } catch (RuntimeException ignored) {
                /* cancellation is advisory; identity still rejects stale completion */
            }
        }
    }

    private void checkThread() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Use the coordinator owner thread");
    }

    private void check() {
        checkThread();
        if (closed) throw new IllegalStateException("Coordinator is closed");
    }

    public void close() {
        checkThread();
        if (closed) return;
        closed = true;
        restoring = false;
        restoreBefore = null;
        requestPublisher = null;
        generation++;
        cancelExecution();
        state =
                new State<>(
                        generation,
                        state.requested(),
                        null,
                        null,
                        Phase.CLOSED,
                        null,
                        sourceVersion);
        source = null;
        requestedSource = null;
        notifyState();
    }
}
