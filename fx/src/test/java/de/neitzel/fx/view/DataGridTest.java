package de.neitzel.fx.view;

import static org.junit.jupiter.api.Assertions.*;

import de.neitzel.grid.*;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class DataGridTest {
    record Item(long id, String text) {}

    @BeforeAll
    static void start() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException running) {
            ready.countDown();
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    static void fx(Runnable code) throws Exception {
        FutureTask<Void> task = new FutureTask<>(code, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }

    static void drain() throws Exception {
        for (int i = 0; i < 4; i++) fx(() -> {});
    }

    static DataGrid<Long, Item> grid(List<Item> items, AtomicInteger disposed) {
        var source =
                new MemoryGridSource<Long, Item>(
                        () -> items,
                        Item::id,
                        Long::compare,
                        List.of(
                                new MemoryColumn<>(
                                        "text",
                                        MemoryColumn.Kind.TEXT,
                                        i -> new FilterValue.Text(i.text()))),
                        Runnable::run,
                        250);
        return new DataGrid<>(
                List.of(new ResultColumn("text", "Text", 120, 50, 300, -1)),
                i -> {
                    Label label = new Label(i.text());
                    return new ResultRow(
                            new HBox(label), Map.of("text", label), disposed::incrementAndGet);
                },
                source);
    }

    @Test
    void pagingFilteringSelectionAndAllUseCoordinatorWithoutChangingLegacyTable() throws Exception {
        var ref = new AtomicReference<DataGrid<Long, Item>>();
        AtomicInteger disposed = new AtomicInteger();
        var items =
                new ArrayList<>(
                        List.of(new Item(1, "Zulu"), new Item(2, "Alpha"), new Item(3, "Beta")));
        fx(
                () -> {
                    ref.set(grid(items, disposed));
                    assertEquals(
                            GridCoordinator.Phase.IDLE, ref.get().stateProperty().get().phase());
                    ref.get().setPageSize(1);
                });
        drain();
        fx(
                () -> {
                    var g = ref.get();
                    assertEquals(1, g.stateProperty().get().result().rows().size());
                    g.select(1L);
                    assertEquals("Zulu", g.selectedElementProperty().get().text());
                    g.next();
                    assertNull(g.selectedElementProperty().get());
                });
        drain();
        fx(
                () -> {
                    var g = ref.get();
                    assertEquals(1L, g.selectedKeyProperty().get());
                    assertNull(g.selectedElementProperty().get());
                    g.setSort(new SortKey("text", SortKey.Direction.ASCENDING));
                });
        drain();
        fx(
                () -> {
                    var g = ref.get();
                    assertEquals(
                            "Alpha",
                            g.stateProperty().get().result().rows().getFirst().model().text());
                    assertEquals(
                            0,
                            ((PageRequest.Paged) g.stateProperty().get().requested().page())
                                    .pageIndex());
                    g.setAllWarningThreshold(1);
                    g.setAllConfirmation((q, count) -> CompletableFuture.completedFuture(false));
                    g.showAll();
                });
        drain();
        fx(
                () -> {
                    var g = ref.get();
                    assertInstanceOf(
                            PageRequest.Paged.class, g.stateProperty().get().requested().page());
                    g.setAllConfirmation((q, count) -> CompletableFuture.completedFuture(true));
                    g.showAll();
                });
        drain();
        fx(
                () -> {
                    var g = ref.get();
                    assertEquals(3, g.stateProperty().get().result().rows().size());
                    assertEquals("Zulu", g.selectedElementProperty().get().text());
                    g.setFilters(
                            List.of(
                                    new TextFilter(
                                            "text", TextFilter.Operator.EQUALS, "alpha", false)));
                });
        drain();
        fx(
                () -> {
                    var g = ref.get();
                    assertEquals(1, g.stateProperty().get().result().rows().size());
                    assertNull(g.selectedElementProperty().get());
                    g.close();
                    assertEquals(GridCoordinator.Phase.CLOSED, g.stateProperty().get().phase());
                    assertTrue(disposed.get() > 0);
                    assertThrows(IllegalStateException.class, g::load);
                });
    }

    @Test
    void pendingGuardDoesNotPublishAfterGridCloses() throws Exception {
        var ref = new AtomicReference<DataGrid<Long, Item>>();
        var guard = new CompletableFuture<Boolean>();
        fx(
                () -> {
                    ref.set(grid(List.of(new Item(1, "a")), new AtomicInteger()));
                    ref.get().setEditGuard(q -> guard);
                    ref.get().load();
                    ref.get().close();
                });
        guard.complete(true);
        drain();
        fx(
                () ->
                        assertEquals(
                                GridCoordinator.Phase.CLOSED,
                                ref.get().stateProperty().get().phase()));
    }

    @Test
    void successfulSourceReplacementClearsSelectionAndPublishesOnFx() throws Exception {
        var ref = new AtomicReference<DataGrid<Long, Item>>();
        fx(
                () -> {
                    ref.set(grid(List.of(new Item(1, "old")), new AtomicInteger()));
                    ref.get().load();
                });
        drain();
        fx(
                () -> {
                    var replacement =
                            new MemoryGridSource<Long, Item>(
                                    () -> List.of(new Item(1, "new")),
                                    Item::id,
                                    Long::compare,
                                    List.of(
                                            new MemoryColumn<>(
                                                    "text",
                                                    MemoryColumn.Kind.TEXT,
                                                    i -> new FilterValue.Text(i.text()))),
                                    Runnable::run,
                                    250);
                    ref.get().select(1L);
                    ref.get()
                            .stateProperty()
                            .addListener(
                                    (obs, old, value) ->
                                            assertTrue(Platform.isFxApplicationThread()));
                    ref.get().setSource(replacement);
                });
        drain();
        fx(
                () -> {
                    assertNull(ref.get().selectedKeyProperty().get());
                    assertNull(ref.get().selectedElementProperty().get());
                    assertEquals(
                            "new",
                            ref.get()
                                    .stateProperty()
                                    .get()
                                    .result()
                                    .rows()
                                    .getFirst()
                                    .model()
                                    .text());
                    ref.get().close();
                });
    }
}
