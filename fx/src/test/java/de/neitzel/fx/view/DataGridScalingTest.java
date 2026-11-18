package de.neitzel.fx.view;

import static org.junit.jupiter.api.Assertions.*;

import de.neitzel.grid.*;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class DataGridScalingTest {
    record Item(long id) {}

    @BeforeAll
    static void toolkit() throws Exception {
        DataGridGroupingTest.toolkit();
    }

    @Test
    void pageCyclesBoundRowsAndCaptureWorkIncludingExplicitlyApprovedAll() throws Exception {
        var queries = new AtomicInteger();
        var mappings = new AtomicInteger();
        var created = new AtomicInteger();
        var disposed = new AtomicInteger();
        var approvals = new AtomicInteger();
        var grid =
                DataGridGroupingTest.fx(() -> {
                    List<Item> items =
                            java.util.stream.LongStream.range(0, 2000).mapToObj(Item::new).toList();
                    var source =
                            new MemoryGridSource<Long, Item>(
                                    () -> {
                                        queries.incrementAndGet();
                                        return items;
                                    },
                                    Item::id,
                                    Long::compare,
                                    List.of(
                                            new MemoryColumn<>(
                                                    "id",
                                                    MemoryColumn.Kind.INTEGER,
                                                    i -> new FilterValue.IntegerValue(i.id()))),
                                    Runnable::run,
                                    250);
                    var value =
                            new DataGrid<Long, Item>(
                                    List.of(
                                            new ResultColumn("id", "Identifier", 150, 80, 400, -1),
                                            new ResultColumn("type", "Type", 150, 80, 400, -1),
                                            new ResultColumn("status", "Status", 150, 80, 400, -1)),
                                    item -> {
                                        created.incrementAndGet();
                                        Label label = new Label(Long.toString(item.id()));
                                        Label type = new Label("T" + item.id() % 4),
                                                status = new Label("S" + item.id() % 3);
                                        return new ResultRow(
                                                new HBox(label, type, status),
                                                Map.of("id", label, "type", type, "status", status),
                                                disposed::incrementAndGet);
                                    },
                                    source);
                    value.configureGrouping(
                            List.of(
                                    GroupColumn.<Item>integer(
                                            "id",
                                            item -> {
                                                mappings.incrementAndGet();
                                                return item.id();
                                            }),
                                    GroupColumn.<Item>text(
                                            "type",
                                            item -> {
                                                mappings.incrementAndGet();
                                                return "T" + item.id() % 4;
                                            }),
                                    GroupColumn.<Item>text(
                                            "status",
                                            item -> {
                                                mappings.incrementAndGet();
                                                return "S" + item.id() % 3;
                                            })),
                            true,
                            List.of("id"),
                            true,
                            1000);
                    value.configureSetups(
                            "scaling",
                            1,
                            Map.of("id", "integer.v1", "type", "text.v1", "status", "text.v1"),
                            Map.of());
                    value.setAllConfirmation((query, count) -> {
                        approvals.incrementAndGet();
                        assertEquals(2000, count);
                        return CompletableFuture.completedFuture(true);
                    });
                    return value;
                });
        try {
            int cycle = 0;
            for (int size : List.of(50, 250, 50, 250, 2000, 50)) {
                int levels = cycle >= 2 && cycle <= 4 ? 3 : 1;
                cycle++;
                var before = DataGridGroupingTest.fx(grid::snapshotSetup);
                var setup =
                        new GridSetup(
                                2,
                                before.definitionId(),
                                before.definitionVersion(),
                                before.columns(),
                                before.sort(),
                                before.filters(),
                                size == 2000
                                        ? new PageRequest.All()
                                        : new PageRequest.Paged(0, size),
                                new GroupingSetup(
                                        levels == 3
                                                ? List.of("type", "status", "id")
                                                : List.of("id"),
                                        before.grouping().choices()));
                int captured = mappings.get();
                long started = System.nanoTime();
                DataGridGroupingTest.fx(() -> grid.restoreSetupAsync(setup))
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);
                double publicationMs = (System.nanoTime() - started) / 1_000_000.0;
                assertEquals(size, created.get() - disposed.get());
                assertEquals(size * 3, mappings.get() - captured);
                int queried = queries.get(), retired = disposed.get();
                long regroupStarted = System.nanoTime();
                DataGridGroupingTest.fx(() -> {
                    grid.regroup();
                    grid.resetExpansion();
                    grid.setColumnWidth("id", new ColumnWidth.Explicit(200));
                    return null;
                });
                double localMs = (System.nanoTime() - regroupStarted) / 1_000_000.0;
                assertEquals(queried, queries.get());
                assertEquals(retired, disposed.get());
                assertEquals(size * 6, mappings.get() - captured);
                System.out.printf(
                        Locale.ROOT,
                        "SCALING rows=%d levels=%d publicationMs=%.3f regroupResetResizeMs=%.3f"
                            + " mappings=%d retained=%d created=%d disposed=%d%n",
                        size,
                        levels,
                        publicationMs,
                        localMs,
                        mappings.get() - captured,
                        created.get() - disposed.get(),
                        created.get(),
                        disposed.get());
            }
            assertEquals(1, approvals.get());
        } finally {
            DataGridGroupingTest.fx(() -> {
                grid.close();
                return null;
            });
        }
        assertEquals(created.get(), disposed.get());
    }
}
