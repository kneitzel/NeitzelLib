package de.neitzel.grid;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

class FilterCandidatesTest {
    @Test
    void currentPageCapturesAtOpenAndSeparatesLabelsFromValues() throws Exception {
        var values =
                new ArrayList<>(
                        List.of(
                                new FilterCandidates.Candidate(new FilterValue.Text("a"), "Same"),
                                new FilterCandidates.Candidate(new FilterValue.Text("b"), "Same"),
                                new FilterCandidates.Candidate(null, "Missing")));
        var provider = FilterCandidates.currentPage(() -> values);
        var context =
                new FilterCandidates.Context("x", FilterCandidates.Scope.CURRENT_PAGE, List.of());
        try (var session = provider.open(context)) {
            values.clear();
            var first =
                    session.load(new FilterCandidates.Request(context, "same", 0, 1))
                            .toCompletableFuture()
                            .get();
            assertTrue(first.hasMore());
            assertEquals(new FilterValue.Text("a"), first.candidates().getFirst().value());
            var next =
                    session.load(new FilterCandidates.Request(context, "same", 1, 1))
                            .toCompletableFuture()
                            .get();
            assertFalse(next.hasMore());
            assertEquals(new FilterValue.Text("b"), next.candidates().getFirst().value());
        }
        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        provider.open(
                                new FilterCandidates.Context(
                                        "x", FilterCandidates.Scope.SOURCE_WIDE, List.of())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FilterCandidates.Context(
                                "x",
                                FilterCandidates.Scope.SOURCE_WIDE,
                                List.of(new MissingFilter("x", true))));
    }

    @Test
    void sourceWideRespectsOtherFiltersAndClosingCancelsPendingWork() throws Exception {
        record Item(long id, String value, long group) {}
        var jobs = new ArrayDeque<Runnable>();
        var data =
                new ArrayList<>(
                        List.of(new Item(1, "a", 1), new Item(2, "b", 2), new Item(3, "a", 1)));
        var source =
                new MemoryGridSource<Long, Item>(
                        () -> data,
                        Item::id,
                        Long::compare,
                        List.of(
                                new MemoryColumn<>(
                                        "x",
                                        MemoryColumn.Kind.TEXT,
                                        i -> new FilterValue.Text(i.value())),
                                new MemoryColumn<>(
                                        "group",
                                        MemoryColumn.Kind.INTEGER,
                                        i -> new FilterValue.IntegerValue(i.group()))),
                        jobs::add,
                        50);
        var context =
                new FilterCandidates.Context(
                        "x",
                        FilterCandidates.Scope.SOURCE_WIDE,
                        List.of(
                                new ValueFilter(
                                        "group", Set.of(new FilterValue.IntegerValue(1)), false)));
        var session = source.candidateProvider(v -> ((FilterValue.Text) v).value()).open(context);
        data.clear();
        var first =
                session.load(new FilterCandidates.Request(context, "", 0, 10))
                        .toCompletableFuture();
        jobs.remove().run();
        assertEquals(
                List.of(new FilterCandidates.Candidate(new FilterValue.Text("a"), "a")),
                first.get().candidates());
        var pending =
                session.load(new FilterCandidates.Request(context, "", 0, 10))
                        .toCompletableFuture();
        session.close();
        jobs.remove().run();
        assertTrue(pending.isCancelled());
        assertThrows(
                IllegalStateException.class,
                () -> session.load(new FilterCandidates.Request(context, "", 0, 10)));
    }
}
