package de.neitzel.grid;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class GridCoordinatorTest {
    static class Source implements GridSource<Integer, String> {
        List<CompletableFuture<PageResult<Integer, String>>> futures = new ArrayList<>();
        List<GridQuery> queries = new ArrayList<>();
        List<QueryOptions> options = new ArrayList<>();
        int cancellations;

        public SourceCapabilities capabilities() {
            return new SourceCapabilities(true, true, 250);
        }

        public QueryExecution<Integer, String> query(GridQuery q) {
            return query(q, QueryOptions.defaults());
        }

        public QueryExecution<Integer, String> query(GridQuery q, QueryOptions o) {
            queries.add(q);
            options.add(o);
            var f = new CompletableFuture<PageResult<Integer, String>>();
            futures.add(f);
            return new QueryExecution<>() {
                public CompletionStage<PageResult<Integer, String>> result() {
                    return f;
                }

                public void cancel() {
                    cancellations++;
                }
            };
        }
    }

    static GridQuery q(long page) {
        return new GridQuery(List.of(), List.of(), new PageRequest.Paged(page, 1));
    }

    static PageResult<Integer, String> page(long index, int key) {
        return new PageResult<>(
                new PageRequest.Paged(index, 1),
                List.of(new GridRow<>(key, "row" + key)),
                OptionalLong.of(10),
                index < 9);
    }

    static class Harness {
        Source source = new Source();
        ArrayDeque<Runnable> callbacks = new ArrayDeque<>();
        List<PageResult<Integer, String>> published = new ArrayList<>();
        GridCoordinator<Integer, String> c =
                new GridCoordinator<>(source, callbacks::add, s -> {}, published::add);

        void drain() {
            while (!callbacks.isEmpty()) callbacks.remove().run();
        }

        void request(GridQuery q) {
            c.request(q);
            drain();
        }
    }

    @Test
    void staleSuccessFailureAndGuardApprovalCannotPublish() {
        var h = new Harness();
        h.request(q(0));
        h.request(q(1));
        assertEquals(1, h.source.cancellations);
        h.source.futures.get(1).complete(page(1, 2));
        h.drain();
        h.source.futures.get(0).completeExceptionally(new IllegalStateException("old"));
        h.drain();
        assertEquals(1, h.published.size());
        assertEquals(GridCoordinator.Phase.READY, h.c.state().phase());
        CompletableFuture<Boolean> guard = new CompletableFuture<>();
        h.c.setGuard(q -> guard);
        h.c.request(q(2));
        h.c.setGuard(q -> CompletableFuture.completedFuture(true));
        h.request(q(3));
        guard.complete(true);
        h.drain();
        assertEquals(3, h.source.queries.size());
        assertEquals(q(3), h.source.queries.getLast());
        h.c.close();
        h.source.futures.getLast().complete(page(3, 4));
        h.drain();
        assertEquals(1, h.published.size());
    }

    @Test
    void errorsRetainPageAndRetryLatestQuery() {
        var h = new Harness();
        h.request(q(0));
        h.source.futures.getFirst().complete(page(0, 1));
        h.drain();
        h.request(q(1));
        h.source.futures.getLast().completeExceptionally(new IllegalStateException("unavailable"));
        h.drain();
        assertEquals(GridCoordinator.Phase.ERROR, h.c.state().phase());
        assertEquals(q(0), h.c.state().displayed());
        h.c.retry();
        h.drain();
        assertEquals(q(1), h.source.queries.getLast());
        h.source.futures.getLast().complete(page(1, 2));
        h.drain();
        assertEquals(q(1), h.c.state().displayed());
    }

    @Test
    void disappearingPagesHaveOnlyOneCorrection() {
        var h = new Harness();
        h.request(q(9));
        h.source
                .futures
                .getFirst()
                .complete(new PageResult<>(q(9).page(), List.of(), OptionalLong.of(2), false));
        h.drain();
        assertEquals(q(1), h.source.queries.getLast());
        h.source
                .futures
                .getLast()
                .complete(new PageResult<>(q(1).page(), List.of(), OptionalLong.of(1), false));
        h.drain();
        assertEquals(2, h.source.queries.size());
        assertEquals(GridCoordinator.Phase.ERROR, h.c.state().phase());
    }

    @Test
    void allApprovalIsAttemptScopedAndCancellationRestoresPage() {
        var h = new Harness();
        h.request(q(0));
        h.source.futures.getFirst().complete(page(0, 1));
        h.drain();
        var all = new GridQuery(List.of(), List.of(), new PageRequest.All());
        CompletableFuture<Boolean> consent = new CompletableFuture<>();
        h.c.setConfirmation((q, count) -> consent);
        h.request(all);
        h.source.futures.getLast().completeExceptionally(new AllRowsConfirmationRequired(1001));
        h.drain();
        assertEquals(GridCoordinator.Phase.CONFIRMATION, h.c.state().phase());
        consent.complete(false);
        h.drain();
        assertEquals(q(0), h.c.state().requested());
        assertEquals(1, h.published.size());
        h.c.setConfirmation((q, count) -> CompletableFuture.completedFuture(true));
        h.request(all);
        h.source.futures.getLast().completeExceptionally(new AllRowsConfirmationRequired(1001));
        h.drain();
        assertTrue(h.source.options.getLast().allApproved());
        h.request(q(2));
        h.source
                .futures
                .get(h.source.futures.size() - 2)
                .complete(
                        new PageResult<>(
                                new PageRequest.All(), List.of(), OptionalLong.of(0), false));
        h.drain();
        assertEquals(1, h.published.size());
    }

    @Test
    void sourceReplacementIsNotCommittedWhenAllWarningIsCancelled() {
        var h = new Harness();
        h.request(q(0));
        h.source.futures.getFirst().complete(page(0, 1));
        h.drain();
        var replacement = new Source();
        h.c.setConfirmation((q, n) -> CompletableFuture.completedFuture(false));
        h.c.request(replacement, new GridQuery(List.of(), List.of(), new PageRequest.All()));
        h.drain();
        replacement.futures.getFirst().completeExceptionally(new AllRowsConfirmationRequired(1001));
        h.drain();
        assertEquals(0, h.c.state().sourceVersion());
        h.c.refresh();
        h.drain();
        assertEquals(2, h.source.queries.size());
    }

    @Test
    void unknownTotalsRecoverToFirstPageAndInvalidResponsesAreErrors() {
        var source =
                new Source() {
                    public SourceCapabilities capabilities() {
                        return new SourceCapabilities(false, false, 250);
                    }
                };
        var jobs = new ArrayDeque<Runnable>();
        var coordinator =
                new GridCoordinator<Integer, String>(source, jobs::add, state -> {}, result -> {});
        coordinator.request(q(4));
        while (!jobs.isEmpty()) jobs.remove().run();
        source.futures
                .getFirst()
                .complete(new PageResult<>(q(4).page(), List.of(), OptionalLong.empty(), false));
        while (!jobs.isEmpty()) jobs.remove().run();
        assertEquals(q(0), source.queries.getLast());
        source.futures
                .getLast()
                .complete(
                        new PageResult<>(
                                q(0).page(),
                                List.of(new GridRow<>(1, "a")),
                                OptionalLong.empty(),
                                true));
        while (!jobs.isEmpty()) jobs.remove().run();
        assertEquals(GridCoordinator.Phase.READY, coordinator.state().phase());
        coordinator.request(q(1));
        while (!jobs.isEmpty()) jobs.remove().run();
        source.futures.getLast().complete(page(0, 2));
        while (!jobs.isEmpty()) jobs.remove().run();
        assertEquals(GridCoordinator.Phase.ERROR, coordinator.state().phase());
        assertEquals(q(0), coordinator.state().displayed());
        coordinator.close();
        assertNull(coordinator.state().result());
    }

    @Test
    void lateConfirmationAndObserverTriggeredRequestsAreSerialized() {
        var source = new Source();
        var jobs = new ArrayDeque<Runnable>();
        var holder = new AtomicReference<GridCoordinator<Integer, String>>();
        var jumped = new AtomicBoolean();
        var coordinator =
                new GridCoordinator<Integer, String>(
                        source,
                        jobs::add,
                        state -> {
                            if (state.phase() == GridCoordinator.Phase.READY
                                    && jumped.compareAndSet(false, true))
                                holder.get().request(q(1));
                        },
                        result -> {});
        holder.set(coordinator);
        coordinator.request(q(0));
        while (!jobs.isEmpty()) jobs.remove().run();
        source.futures.getFirst().complete(page(0, 1));
        while (!jobs.isEmpty()) jobs.remove().run();
        assertEquals(q(1), coordinator.state().requested());
        assertEquals(GridCoordinator.Phase.LOADING, coordinator.state().phase());
        var consent = new CompletableFuture<Boolean>();
        coordinator.setConfirmation((q, n) -> consent);
        coordinator.request(new GridQuery(List.of(), List.of(), new PageRequest.All()));
        while (!jobs.isEmpty()) jobs.remove().run();
        source.futures.getLast().completeExceptionally(new AllRowsConfirmationRequired(1001));
        while (!jobs.isEmpty()) jobs.remove().run();
        coordinator.request(q(2));
        while (!jobs.isEmpty()) jobs.remove().run();
        int count = source.queries.size();
        consent.complete(true);
        while (!jobs.isEmpty()) jobs.remove().run();
        assertEquals(count, source.queries.size());
        assertEquals(q(2), coordinator.state().requested());
    }

    @Test
    void closeDuringPublicationOrConfirmationDoesNotResurrectCoordinator() {
        for (boolean closeInPublisher : List.of(true, false)) {
            var source = new Source();
            var jobs = new ArrayDeque<Runnable>();
            var holder = new AtomicReference<GridCoordinator<Integer, String>>();
            var coordinator =
                    new GridCoordinator<Integer, String>(
                            source,
                            jobs::add,
                            state -> {
                                if (!closeInPublisher
                                        && state.phase() == GridCoordinator.Phase.CONFIRMATION)
                                    holder.get().close();
                            },
                            result -> holder.get().close());
            holder.set(coordinator);
            coordinator.setConfirmation(
                    (q, n) -> {
                        fail("Closed coordinator must not open confirmation");
                        return CompletableFuture.completedFuture(true);
                    });
            coordinator.request(
                    closeInPublisher
                            ? q(0)
                            : new GridQuery(List.of(), List.of(), new PageRequest.All()));
            while (!jobs.isEmpty()) jobs.remove().run();
            if (closeInPublisher) source.futures.getFirst().complete(page(0, 1));
            else
                source.futures
                        .getFirst()
                        .completeExceptionally(new AllRowsConfirmationRequired(1001));
            while (!jobs.isEmpty()) jobs.remove().run();
            assertEquals(GridCoordinator.Phase.CLOSED, coordinator.state().phase());
            assertNull(coordinator.state().result());
        }
    }
}
