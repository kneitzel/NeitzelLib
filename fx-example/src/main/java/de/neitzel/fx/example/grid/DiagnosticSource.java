package de.neitzel.fx.example.grid;

import de.neitzel.grid.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/**
 * Demo-only controlled completion delays. No sleeps, driver interruption or altered SQL semantics.
 */
public final class DiagnosticSource implements GridSource<Long, Product> {
    private final GridSource<Long, Product> delegate;
    private final ScheduledExecutorService clock;
    private final Consumer<String> log;
    private final Consumer<FilterCondition> domainValidation;
    private final AtomicLong ids = new AtomicLong();
    private volatile long delay;
    private volatile boolean fail, late;

    public DiagnosticSource(
            GridSource<Long, Product> delegate,
            ScheduledExecutorService clock,
            Consumer<String> log,
            Consumer<FilterCondition> domainValidation) {
        this.delegate = delegate;
        this.clock = clock;
        this.log = log;
        this.domainValidation = domainValidation;
    }

    public void next(long milliseconds, boolean failure, boolean deliverAfterCancel) {
        delay = milliseconds;
        fail = failure;
        late = deliverAfterCancel;
    }

    public SourceCapabilities capabilities() {
        return delegate.capabilities();
    }

    public String filterExecutionId() {
        return delegate.filterExecutionId();
    }

    public void validateFilter(FilterCondition condition) {
        domainValidation.accept(condition);
        delegate.validateFilter(condition);
    }

    public QueryExecution<Long, Product> query(GridQuery query) {
        return query(query, QueryOptions.defaults());
    }

    public QueryExecution<Long, Product> query(GridQuery query, QueryOptions options) {
        long id = ids.incrementAndGet(), started = System.nanoTime(), wait = delay;
        boolean failure = fail, ignoreCancel = late;
        next(0, false, false);
        var result = new CompletableFuture<PageResult<Long, Product>>();
        var cancelled = new AtomicBoolean();
        log.accept("Request " + id + " requested " + query.page());
        try {
            query.filters().forEach(this::validateFilter);
        } catch (RuntimeException error) {
            result.completeExceptionally(error);
            return new QueryExecution<>() {
                public CompletionStage<PageResult<Long, Product>> result() {
                    return result;
                }

                public void cancel() {
                    result.cancel(false);
                }
            };
        }
        var execution = delegate.query(query, options);
        execution
                .result()
                .whenComplete(
                        (page, error) -> {
                            try {
                                clock.schedule(
                                        () -> {
                                            log.accept(
                                                    "Request "
                                                            + id
                                                            + " "
                                                            + (cancelled.get()
                                                                    ? "obsolete completion"
                                                                            + " (coordinator must"
                                                                            + " ignore)"
                                                                    : "completion")
                                                            + " "
                                                            + ((System.nanoTime() - started)
                                                                    / 1_000_000)
                                                            + " ms; rows="
                                                            + (page == null
                                                                    ? 0
                                                                    : page.rows().size()));
                                            if (error != null) result.completeExceptionally(error);
                                            else if (failure)
                                                result.completeExceptionally(
                                                        new IllegalStateException(
                                                                "Injected query failure; Retry runs"
                                                                        + " normally"));
                                            else result.complete(page);
                                        },
                                        wait,
                                        TimeUnit.MILLISECONDS);
                            } catch (RejectedExecutionException rejected) {
                                result.completeExceptionally(rejected);
                            }
                        });
        return new QueryExecution<>() {
            public CompletionStage<PageResult<Long, Product>> result() {
                return result;
            }

            public void cancel() {
                cancelled.set(true);
                log.accept("Request " + id + " cancellation requested");
                if (!ignoreCancel) {
                    execution.cancel();
                    result.cancel(false);
                }
            }
        };
    }
}
