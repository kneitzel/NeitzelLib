package de.neitzel.fx.view;

import static org.junit.jupiter.api.Assertions.*;

import de.neitzel.fx.grid.filter.FilterColumn;
import de.neitzel.grid.*;

import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class DataGridSetupTest {
    @BeforeAll
    static void start() throws Exception {
        DataGridTest.start();
        DataGridTest.fx(() -> Platform.setImplicitExit(false));
    }

    static class Source implements GridSource<Integer, String> {
        List<GridQuery> queries = new ArrayList<>();
        List<QueryOptions> options = new ArrayList<>();
        List<CompletableFuture<PageResult<Integer, String>>> pending = new ArrayList<>();

        public SourceCapabilities capabilities() {
            return new SourceCapabilities(true, true, 100);
        }

        public String filterExecutionId() {
            return "local";
        }

        public void validateFilter(FilterCondition filter) {
            if (!(filter instanceof TextFilter))
                throw new UnsupportedOperationException("Only text comparisons supported");
        }

        public QueryExecution<Integer, String> query(GridQuery q) {
            return query(q, QueryOptions.defaults());
        }

        public QueryExecution<Integer, String> query(GridQuery q, QueryOptions o) {
            queries.add(q);
            options.add(o);
            var f = new CompletableFuture<PageResult<Integer, String>>();
            pending.add(f);
            return new QueryExecution<>() {
                public CompletionStage<PageResult<Integer, String>> result() {
                    return f;
                }

                public void cancel() {}
            };
        }

        void success(int index, String model) {
            pending.get(index)
                    .complete(
                            new PageResult<>(
                                    queries.get(index).page(),
                                    List.of(new GridRow<>(9, model)),
                                    OptionalLong.of(1),
                                    false));
        }
    }

    static class Harness {
        Source source = new Source();
        AtomicInteger disposed = new AtomicInteger();
        DataGrid<Integer, String> grid;

        Harness() {
            grid =
                    new DataGrid<>(
                            List.of(
                                    new ResultColumn("a", "A", 100, 50, 200, -1),
                                    new ResultColumn("b", "B", 80, 40, 200, -1)),
                            value -> {
                                if (value.equals("bad"))
                                    throw new IllegalArgumentException("Cannot construct row");
                                var a = new Label(value);
                                var b = new Label(value);
                                return new ResultRow(
                                        new HBox(a, b),
                                        Map.of("a", a, "b", b),
                                        disposed::incrementAndGet);
                            },
                            source);
            grid.configureSetups("items", 1, Map.of("a", "text", "b", "text"), Map.of());
        }

        GridSetup candidate(PageRequest page) {
            return new GridSetup(
                    1,
                    "items",
                    1,
                    List.of(
                            new GridSetup.ColumnState("b", "text", new ColumnWidth.Explicit(999)),
                            new GridSetup.ColumnState("a", "text", new ColumnWidth.Default())),
                    List.of(new SortKey("b", SortKey.Direction.DESCENDING)),
                    List.of(new TextFilter("a", TextFilter.Operator.CONTAINS, "v", false)),
                    page);
        }
    }

    static Harness create() throws Exception {
        var ref = new AtomicReference<Harness>();
        DataGridTest.fx(() -> ref.set(new Harness()));
        return ref.get();
    }

    static void loaded(Harness h) throws Exception {
        DataGridTest.fx(h.grid::load);
        DataGridTest.drain();
        DataGridTest.fx(() -> h.source.success(0, "old"));
        DataGridTest.drain();
    }

    @Test
    void startupRestoreIssuesOneQueryAndPublishesLayoutAndRowsTogether() throws Exception {
        var h = create();
        var original = new AtomicReference<GridSetup>();
        DataGridTest.fx(
                () -> {
                    original.set(h.grid.snapshotSetup());
                    h.grid.select(9);
                    h.grid.restoreSetup(h.candidate(new PageRequest.Paged(0, 25)));
                    assertTrue(h.source.queries.isEmpty());
                    assertEquals(original.get(), h.grid.snapshotSetup());
                });
        DataGridTest.drain();
        DataGridTest.fx(
                () -> {
                    assertEquals(1, h.source.queries.size());
                    assertEquals(new PageRequest.Paged(0, 25), h.source.queries.getFirst().page());
                    h.source.success(0, "value");
                });
        DataGridTest.drain();
        DataGridTest.fx(
                () -> {
                    var saved = h.grid.snapshotSetup();
                    assertEquals(
                            List.of("b", "a"),
                            saved.columns().stream().map(GridSetup.ColumnState::id).toList());
                    assertEquals(new ColumnWidth.Explicit(200), saved.columns().getFirst().width());
                    assertEquals(1, h.grid.restorationAdjustmentsProperty().get().size());
                    assertEquals("value", h.grid.selectedElementProperty().get());
                    h.grid.close();
                });
    }

    @Test
    void decodeValidationQueryAndRowConstructionFailuresPreserveCommittedState() throws Exception {
        var h = create();
        loaded(h);
        var before = new AtomicReference<GridSetup>();
        DataGridTest.fx(
                () -> {
                    before.set(h.grid.snapshotSetup());
                    h.grid.restoreSetup(
                            () -> {
                                throw new IllegalArgumentException("Malformed JSON");
                            });
                    assertEquals(1, h.source.queries.size());
                    assertEquals(before.get(), h.grid.snapshotSetup());
                    var invalid = h.candidate(new PageRequest.Paged(0, 25));
                    h.grid.restoreSetup(
                            new GridSetup(
                                    1,
                                    "wrong",
                                    1,
                                    invalid.columns(),
                                    invalid.sort(),
                                    invalid.filters(),
                                    invalid.page()));
                    assertEquals(1, h.source.queries.size());
                    h.grid.restoreSetup(invalid);
                });
        DataGridTest.drain();
        DataGridTest.fx(
                () ->
                        h.source
                                .pending
                                .getLast()
                                .completeExceptionally(new IllegalStateException("offline")));
        DataGridTest.drain();
        DataGridTest.fx(
                () -> {
                    assertEquals(before.get(), h.grid.snapshotSetup());
                    assertEquals(GridCoordinator.Phase.ERROR, h.grid.stateProperty().get().phase());
                    assertEquals(
                            "old", h.grid.stateProperty().get().result().rows().getFirst().model());
                    h.grid.restoreSetup(h.candidate(new PageRequest.Paged(0, 25)));
                });
        DataGridTest.drain();
        DataGridTest.fx(() -> h.source.success(h.source.queries.size() - 1, "bad"));
        DataGridTest.drain();
        DataGridTest.fx(
                () -> {
                    assertEquals(before.get(), h.grid.snapshotSetup());
                    assertEquals(0, h.disposed.get());
                    assertTrue(
                            h.grid.stateProperty()
                                    .get()
                                    .error()
                                    .getMessage()
                                    .contains("construct"));
                    h.grid.close();
                });
    }

    @Test
    void newerQueryAndLayoutChangesSupersedeRestoreAndLateApproval() throws Exception {
        var h = create();
        loaded(h);
        var guard = new CompletableFuture<Boolean>();
        DataGridTest.fx(
                () -> {
                    h.grid.setEditGuard(q -> guard);
                    h.grid.restoreSetup(h.candidate(new PageRequest.Paged(0, 25)));
                });
        DataGridTest.drain();
        DataGridTest.fx(
                () -> {
                    h.grid.setEditGuard(q -> CompletableFuture.completedFuture(true));
                    h.grid.setFilters(
                            List.of(new TextFilter("a", TextFilter.Operator.EQUALS, "new", true)));
                    guard.complete(true);
                });
        DataGridTest.drain();
        DataGridTest.fx(
                () -> {
                    assertEquals(2, h.source.queries.size());
                    assertEquals(new PageRequest.Paged(0, 50), h.source.queries.getLast().page());
                    h.source.success(1, "new");
                });
        DataGridTest.drain();
        DataGridTest.fx(() -> h.grid.restoreSetup(h.candidate(new PageRequest.Paged(0, 25))));
        DataGridTest.drain();
        DataGridTest.fx(
                () -> {
                    h.grid.setColumnWidth("a", new ColumnWidth.Explicit(150));
                    h.source.success(2, "late");
                });
        DataGridTest.drain();
        DataGridTest.fx(
                () -> {
                    assertEquals(
                            "new", h.grid.stateProperty().get().result().rows().getFirst().model());
                    assertEquals("a", h.grid.snapshotSetup().columns().getFirst().id());
                    assertEquals(
                            new ColumnWidth.Explicit(150),
                            h.grid.snapshotSetup().columns().getFirst().width());
                    h.grid.close();
                });
    }

    @Test
    void allRestoreRequiresFreshConfirmationAndCancelDoesNotInstallLayout() throws Exception {
        var h = create();
        loaded(h);
        var before = new AtomicReference<GridSetup>();
        var confirmation = new CompletableFuture<Boolean>();
        DataGridTest.fx(
                () -> {
                    before.set(h.grid.snapshotSetup());
                    h.grid.setAllConfirmation((q, n) -> confirmation);
                    h.grid.restoreSetup(h.candidate(new PageRequest.All()));
                });
        DataGridTest.drain();
        DataGridTest.fx(
                () ->
                        h.source
                                .pending
                                .getLast()
                                .completeExceptionally(new AllRowsConfirmationRequired(1001)));
        DataGridTest.drain();
        DataGridTest.fx(
                () -> {
                    assertEquals(before.get(), h.grid.snapshotSetup());
                    confirmation.complete(false);
                });
        DataGridTest.drain();
        DataGridTest.fx(
                () -> {
                    assertEquals(before.get(), h.grid.snapshotSetup());
                    h.grid.setAllConfirmation((q, n) -> CompletableFuture.completedFuture(true));
                    h.grid.restoreSetup(h.candidate(new PageRequest.All()));
                });
        DataGridTest.drain();
        DataGridTest.fx(
                () -> {
                    assertFalse(h.source.options.getLast().allApproved());
                    h.source
                            .pending
                            .getLast()
                            .completeExceptionally(new AllRowsConfirmationRequired(1001));
                });
        DataGridTest.drain();
        DataGridTest.fx(
                () -> {
                    assertTrue(h.source.options.getLast().allApproved());
                    h.source.success(h.source.queries.size() - 1, "all");
                });
        DataGridTest.drain();
        DataGridTest.fx(
                () -> {
                    assertInstanceOf(PageRequest.All.class, h.grid.snapshotSetup().page());
                    h.grid.close();
                });
    }

    @Test
    void savingLeavesDraftOpenAndRestoreCancelsItOnlyAfterGuardAllows() throws Exception {
        var h = create();
        loaded(h);
        var stage = new AtomicReference<Stage>();
        var guard = new CompletableFuture<Boolean>();
        var popup = new AtomicReference<Window>();
        DataGridTest.fx(
                () -> {
                    h.grid.configureFilter("a", FilterColumn.text());
                    var window = new Stage();
                    window.setScene(new Scene(h.grid, 600, 400));
                    window.show();
                    stage.set(window);
                    ((Button) h.grid.lookup(".filter-button")).fire();
                    popup.set(
                            Window.getWindows().stream()
                                    .filter(
                                            w ->
                                                    w != window
                                                            && w.getScene()
                                                                            .getRoot()
                                                                            .lookup("#filter-ok")
                                                                    != null)
                                    .findFirst()
                                    .orElseThrow());
                    h.grid.snapshotSetup();
                    assertTrue(popup.get().isShowing());
                    h.grid.setEditGuard(q -> guard);
                    h.grid.restoreSetup(h.candidate(new PageRequest.Paged(0, 25)));
                });
        DataGridTest.drain();
        DataGridTest.fx(
                () -> {
                    assertTrue(popup.get().isShowing());
                    guard.complete(false);
                });
        DataGridTest.drain();
        DataGridTest.fx(
                () -> {
                    assertTrue(popup.get().isShowing());
                    h.grid.setEditGuard(q -> CompletableFuture.completedFuture(true));
                    h.grid.restoreSetup(h.candidate(new PageRequest.Paged(0, 25)));
                });
        DataGridTest.drain();
        DataGridTest.fx(
                () -> {
                    assertFalse(popup.get().isShowing());
                    assertEquals(2, h.source.queries.size());
                    h.grid.close();
                    stage.get().close();
                });
    }
}
