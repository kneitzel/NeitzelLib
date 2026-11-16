package de.neitzel.grid;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

class MemoryGridSourceTest {
    record Product(long id, String name, Long quantity, BigDecimal price) {}

    static MemoryGridSource<Long, Product> source(List<Product> rows, Executor executor) {
        return new MemoryGridSource<>(
                () -> rows,
                Product::id,
                Long::compare,
                List.of(
                        new MemoryColumn<>(
                                "name",
                                MemoryColumn.Kind.TEXT,
                                p -> p.name() == null ? null : new FilterValue.Text(p.name())),
                        new MemoryColumn<>(
                                "quantity",
                                MemoryColumn.Kind.INTEGER,
                                p ->
                                        p.quantity() == null
                                                ? null
                                                : new FilterValue.IntegerValue(p.quantity())),
                        new MemoryColumn<>(
                                "price",
                                MemoryColumn.Kind.DECIMAL,
                                p ->
                                        p.price() == null
                                                ? null
                                                : new FilterValue.Decimal(p.price()))),
                executor,
                250);
    }

    static GridQuery query(List<FilterCondition> f, PageRequest p) {
        return new GridQuery(f, List.of(new SortKey("name", SortKey.Direction.DESCENDING)), p);
    }

    @Test
    void filteringSortingAndPagingUseWholeSnapshotAndStableKeys() {
        var rows =
                new ArrayList<>(
                        List.of(
                                new Product(3, "alpha", 3L, new BigDecimal("1.00")),
                                new Product(1, "Alpha", 1L, new BigDecimal("1.0")),
                                new Product(2, null, 2L, null),
                                new Product(4, "Zulu", 4L, new BigDecimal("2"))));
        var source = source(rows, Runnable::run);
        var page =
                source.query(query(List.of(), new PageRequest.Paged(1, 2)))
                        .result()
                        .toCompletableFuture()
                        .join();
        assertEquals(List.of(3L, 2L), page.rows().stream().map(GridRow::key).toList());
        assertEquals(4, page.total().orElseThrow());
        assertFalse(page.hasNext());
        var filtered =
                source.query(
                                query(
                                        List.of(
                                                new NumericFilter(
                                                        "price",
                                                        NumericFilter.Operator.EQUALS,
                                                        new BigDecimal("1"),
                                                        null)),
                                        new PageRequest.Paged(0, 1)))
                        .result()
                        .toCompletableFuture()
                        .join();
        assertEquals(1L, filtered.rows().getFirst().key());
        assertEquals(2, filtered.total().orElseThrow());
        assertTrue(filtered.hasNext());
        assertEquals(List.of(3L, 1L, 2L, 4L), rows.stream().map(Product::id).toList());
    }

    @Test
    void snapshotIsCapturedBeforeExecutorAndCancellationDoesNotInterrupt() {
        var jobs = new ArrayDeque<Runnable>();
        var rows = new ArrayList<>(List.of(new Product(1, "original", 1L, null)));
        var source = source(rows, jobs::add);
        var request = source.query(query(List.of(), new PageRequest.Paged(0, 25)));
        rows.clear();
        jobs.remove().run();
        assertEquals(1, request.result().toCompletableFuture().join().rows().size());
        var cancelled = source.query(query(List.of(), new PageRequest.Paged(0, 25)));
        cancelled.cancel();
        jobs.remove().run();
        assertThrows(
                CancellationException.class, () -> cancelled.result().toCompletableFuture().join());
    }

    @Test
    void allChecksFilteredCountBeforeReturningRowsAndRechecksOnApproval() {
        var rows =
                new ArrayList<>(
                        List.of(new Product(1, "a", 1L, null), new Product(2, "b", 2L, null)));
        var source = source(rows, Runnable::run);
        var q = query(List.of(), new PageRequest.All());
        var error =
                assertThrows(
                        CompletionException.class,
                        () ->
                                source.query(q, new QueryOptions(1, false))
                                        .result()
                                        .toCompletableFuture()
                                        .join());
        assertInstanceOf(AllRowsConfirmationRequired.class, error.getCause());
        rows.add(new Product(3, "c", 3L, null));
        assertEquals(
                3,
                source.query(q, new QueryOptions(1, true))
                        .result()
                        .toCompletableFuture()
                        .join()
                        .rows()
                        .size());
        assertEquals(
                3,
                source.query(q, new QueryOptions(3, false))
                        .result()
                        .toCompletableFuture()
                        .join()
                        .rows()
                        .size());
    }

    @Test
    void typesUnsupportedConditionsAndDuplicateKeysFailVisibly() {
        var source = source(List.of(new Product(1, "a", 1L, null)), Runnable::run);
        assertThrows(
                CompletionException.class,
                () ->
                        source.query(
                                        query(
                                                List.of(
                                                        new NumericFilter(
                                                                "quantity",
                                                                NumericFilter.Operator.EQUALS,
                                                                new BigDecimal("1.5"),
                                                                null)),
                                                new PageRequest.Paged(0, 25)))
                                .result()
                                .toCompletableFuture()
                                .join());
        FilterCondition custom =
                new FilterCondition() {
                    public String columnId() {
                        return "name";
                    }

                    public String typeId() {
                        return "unsupported";
                    }
                };
        assertInstanceOf(
                UnsupportedOperationException.class,
                assertThrows(
                                CompletionException.class,
                                () ->
                                        source.query(
                                                        query(
                                                                List.of(custom),
                                                                new PageRequest.Paged(0, 25)))
                                                .result()
                                                .toCompletableFuture()
                                                .join())
                        .getCause());
        var dup =
                source(
                        List.of(new Product(1, "a", 1L, null), new Product(1, "b", 2L, null)),
                        Runnable::run);
        assertThrows(
                CompletionException.class,
                () ->
                        dup.query(query(List.of(), new PageRequest.Paged(0, 25)))
                                .result()
                                .toCompletableFuture()
                                .join());
    }

    private record LongName(String columnId) implements FilterCondition {
        public String typeId() {
            return "demo.long-name";
        }
    }

    @Test
    void registeredCustomEvaluationRunsOnApplicationExecutor() throws Exception {
        Thread caller = Thread.currentThread();
        var evaluatedOn = new java.util.concurrent.atomic.AtomicReference<Thread>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            MemoryColumn<Product> name =
                    new MemoryColumn<>(
                            "name",
                            MemoryColumn.Kind.TEXT,
                            p -> {
                                assertSame(caller, Thread.currentThread());
                                return new FilterValue.Text(p.name());
                            },
                            Map.of(
                                    "demo.long-name",
                                    condition -> {
                                        if (!(condition instanceof LongName))
                                            throw new IllegalArgumentException(
                                                    "Unsupported custom condition");
                                        return value -> {
                                            evaluatedOn.set(Thread.currentThread());
                                            return value instanceof FilterValue.Text text
                                                    && text.value().length() > 3;
                                        };
                                    }));
            var source =
                    new MemoryGridSource<Long, Product>(
                            () ->
                                    List.of(
                                            new Product(1, "Alpha", 1L, null),
                                            new Product(2, "Bee", 2L, null)),
                            Product::id,
                            Long::compare,
                            List.of(name),
                            executor,
                            100);
            var result =
                    source.query(
                                    new GridQuery(
                                            List.of(new LongName("name")),
                                            List.of(),
                                            new PageRequest.Paged(0, 25)))
                            .result()
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS);
            assertEquals(List.of(1L), result.rows().stream().map(GridRow::key).toList());
            assertNotSame(caller, evaluatedOn.get());
            assertFalse(executor.isShutdown());
        } finally {
            executor.shutdownNow();
        }
    }
}
