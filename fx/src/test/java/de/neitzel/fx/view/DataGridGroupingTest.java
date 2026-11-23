package de.neitzel.fx.view;

import static org.junit.jupiter.api.Assertions.*;

import de.neitzel.grid.*;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class DataGridGroupingTest {
    record Item(long id, String type, String status) {}

    @BeforeAll
    static void toolkit() throws Exception {
        DataGridTest.start();
        Platform.setImplicitExit(false);
    }

    static <V> V fx(Callable<V> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    static DataGrid<Long, Item> grid(
            AtomicInteger reads, AtomicInteger disposed, Map<Long, TextField> fields) {
        var items =
                List.of(
                        new Item(1, "A", "live"),
                        new Item(2, "MX", "live"),
                        new Item(3, "A", "missing"));
        var source =
                new MemoryGridSource<Long, Item>(
                        () -> {
                            reads.incrementAndGet();
                            return items;
                        },
                        Item::id,
                        Long::compare,
                        List.of(
                                new MemoryColumn<>(
                                        "type",
                                        MemoryColumn.Kind.TEXT,
                                        i -> new FilterValue.Text(i.type()))),
                        Runnable::run,
                        250);
        var grid =
                new DataGrid<Long, Item>(
                        List.of(
                                new ResultColumn("type", "Type", 150, 80, 400, -1),
                                new ResultColumn("status", "Status", 150, 80, 400, -1)),
                        i -> {
                            TextField field = new TextField(i.type());
                            fields.put(i.id(), field);
                            Label status = new Label(i.status());
                            return new ResultRow(
                                    new HBox(field, status),
                                    Map.of("type", field, "status", status),
                                    disposed::incrementAndGet);
                        },
                        source);
        grid.configureGrouping(
                List.of(
                        new GroupColumn<>(
                                "type",
                                MemoryColumn.Kind.TEXT,
                                i -> new FilterValue.Text(i.type()),
                                GroupColumn::display),
                        new GroupColumn<>(
                                "status",
                                MemoryColumn.Kind.TEXT,
                                i -> new FilterValue.Text(i.status()),
                                GroupColumn::display)),
                true,
                List.of("type"),
                true,
                1000);
        grid.configureSetups(
                "example", 1, Map.of("type", "text.v1", "status", "text.v1"), Map.of());
        new Scene(new StackPane(grid), 800, 500);
        grid.applyCss();
        grid.layout();
        return grid;
    }

    static GroupPath path(String type) {
        return new GroupPath(List.of(new GroupPath.Segment("type", new FilterValue.Text(type))));
    }

    @Test
    void headerMenusChangeVisibilityWithoutReplacingRowsOrLosingLayout() throws Exception {
        var reads = new AtomicInteger();
        var disposed = new AtomicInteger();
        Map<Long, TextField> fields = new HashMap<>();
        var grid = fx(() -> grid(reads, disposed, fields));
        var stage = fx(Stage::new);
        try {
            fx(grid::loadAsync).toCompletableFuture().get(5, TimeUnit.SECONDS);
            fx(() -> {
                stage.setScene(grid.getScene());
                stage.show();
                grid.setExpanded(path("A"), true);
                grid.setColumnOrder(List.of("status", "type"));
                grid.setColumnWidth("type", new ColumnWidth.Explicit(235));
                var editor = fields.get(1L);
                editor.setText("unfinished draft");
                var row = (HBox) editor.getParent();
                assertSame(editor, row.getChildren().get(1));
                var menu = (MenuButton) grid.lookup(".data-grid-column-menu");
                var header = (HBox) grid.lookup(".column-header-row");
                long visibility = grid.columnVisibilityRevisionProperty().get();
                var context = showColumnContextMenu(header.getChildren().get(1), false);
                context.getItems().getLast().fire();
                assertEquals(++visibility, grid.columnVisibilityRevisionProperty().get());
                grid.setColumnVisible("type", false);
                assertEquals(visibility, grid.columnVisibilityRevisionProperty().get());
                assertFalse(context.isShowing());
                assertFalse(grid.isColumnVisible("type"));
                assertNull(editor.getParent());
                assertEquals(1, header.getChildren().size());
                assertEquals(List.of("type"), grid.groupingColumns());

                menu.show();
                assertTrue(menu.isShowing());
                var status = (CheckMenuItem) menu.getItems().getFirst();
                var type = (CheckMenuItem) menu.getItems().getLast();
                assertEquals("Status", status.getText());
                assertTrue(status.isSelected());
                assertEquals("Type", type.getText());
                assertFalse(type.isSelected());
                status.setSelected(false);
                status.fire();
                menu.hide();
                assertTrue(header.getChildren().isEmpty());
                assertTrue(menu.isVisible());
                menu.show();
                type = (CheckMenuItem) menu.getItems().getLast();
                type.setSelected(true);
                type.fire();
                menu.hide();
                assertEquals(visibility + 2, grid.columnVisibilityRevisionProperty().get());
                assertSame(editor, row.getChildren().getFirst());
                assertEquals(235, editor.getPrefWidth());
                assertEquals("unfinished draft", editor.getText());
                assertEquals(0, disposed.get());
                assertEquals(1, reads.get());
                return null;
            });
            fx(() -> {
                var header = (HBox) grid.lookup(".column-header-row");
                var context = showColumnContextMenu(header.getChildren().getFirst(), true);
                context.getItems().stream()
                        .filter(item -> "Sort descending".equals(item.getText()))
                        .findFirst().orElseThrow().fire();
                context.hide();
                return null;
            });
            DataGridTest.drain();
            fx(() -> {
                assertEquals(List.of(new SortKey("type", SortKey.Direction.DESCENDING)),
                        grid.stateProperty().get().displayed().sort());
                assertFalse(grid.isColumnVisible("status"));
                assertTrue(grid.isColumnVisible("type"));
                assertEquals(1, ((HBox) grid.lookup(".column-header-row")).getChildren().size());
                assertEquals(2, reads.get());
                return null;
            });
        } finally {
            fx(() -> {
                grid.close();
                stage.close();
                return null;
            });
        }
    }

    private static ContextMenu showColumnContextMenu(Node cell, boolean fromKeyboard) {
        var point = cell.localToScreen(0, 0);
        cell.fireEvent(new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                0, 0, point.getX(), point.getY(), fromKeyboard, null));
        return Window.getWindows().stream()
                .filter(ContextMenu.class::isInstance).map(ContextMenu.class::cast)
                .filter(ContextMenu::isShowing).findFirst().orElseThrow();
    }

    @Test
    void selectorStaysFixedAndHeadersAlignAtTheRightScrollEdge() throws Exception {
        var fields = new HashMap<Long, TextField>();
        var grid = fx(() -> grid(new AtomicInteger(), new AtomicInteger(), fields));
        try {
            fx(grid::loadAsync).toCompletableFuture().get(5, TimeUnit.SECONDS);
            fx(() -> {
                grid.setExpanded(path("A"), true);
                grid.setColumnWidth("type", new ColumnWidth.Explicit(400));
                grid.setColumnWidth("status", new ColumnWidth.Explicit(400));
                var root = grid.getScene().getRoot();
                root.resize(640, 500);
                root.layout();
                var menu = grid.lookup(".data-grid-column-menu");
                double menuX = menu.localToScene(0, 0).getX();
                var scroll = (ScrollPane) grid.lookup(".table-scroll");
                var header = (HBox) grid.lookup(".column-header-row");
                scroll.setHvalue(1);
                root.layout();
                assertEquals(menuX, menu.localToScene(0, 0).getX(), .5);
                var type = fields.get(1L);
                assertEquals(type.localToScene(0, 0).getX(),
                        header.getChildren().getFirst().localToScene(0, 0).getX(), 1);
                var last = header.getChildren().getLast();
                assertEquals(menuX, last.localToScene(last.getBoundsInLocal()).getMaxX(), 1);
                return null;
            });
        } finally {
            fx(() -> {
                grid.close();
                return null;
            });
        }
    }

    @Test
    void presentationChangesPreserveComponentsDraftsAndHiddenSelectionWithoutQueries()
            throws Exception {
        var reads = new AtomicInteger();
        var disposed = new AtomicInteger();
        Map<Long, TextField> fields = new HashMap<>();
        var grid = fx(() -> grid(reads, disposed, fields));
        fx(() -> grid.loadAsync()).toCompletableFuture().get(5, TimeUnit.SECONDS);
        try {
            fx(() -> {
                var editor = fields.get(1L);
                editor.setText("unfinished draft");
                grid.select(1L);
                assertNull(grid.selectedElementProperty().get());
                grid.setExpanded(path("A"), true);
                assertEquals(1L, grid.selectedElementProperty().get().id());
                grid.setGroupingColumns(List.of("type", "status"));
                var child = path("A").child("status", new FilterValue.Text("live"));
                grid.setExpanded(child, true);
                grid.setColumnOrder(List.of("status", "type"));
                grid.setColumnWidth("type", new ColumnWidth.Explicit(235));
                grid.setExpanded(path("A"), false);
                assertNull(grid.selectedElementProperty().get());
                assertEquals(1L, grid.selectedKeyProperty().get());
                grid.setExpanded(path("A"), true);
                assertSame(editor, fields.get(1L));
                assertEquals("unfinished draft", editor.getText());
                assertEquals(0, disposed.get());
                assertEquals(1, reads.get());
                assertEquals(1L, grid.selectedElementProperty().get().id());
                return null;
            });
        } finally {
            fx(() -> {
                grid.close();
                return null;
            });
        }
        assertEquals(3, disposed.get());
    }

    @Test
    void oldSetupUsesConfiguredGroupingDefaultsAndRestorePublishesOnlyOnce() throws Exception {
        var reads = new AtomicInteger();
        var grid = fx(() -> grid(reads, new AtomicInteger(), new HashMap<>()));
        try {
            var snapshot = fx(grid::snapshotSetup);
            var old =
                    new GridSetup(
                            1,
                            snapshot.definitionId(),
                            snapshot.definitionVersion(),
                            snapshot.columns(),
                            snapshot.sort(),
                            snapshot.filters(),
                            snapshot.page());
            fx(() -> grid.restoreSetupAsync(old)).toCompletableFuture().get(5, TimeUnit.SECONDS);
            fx(() -> {
                assertEquals(List.of("type"), grid.groupingColumns());
                assertEquals(1, reads.get());
                return null;
            });
        } finally {
            fx(() -> {
                grid.close();
                return null;
            });
        }
    }

    @Test
    void fullDefaultsCanBeConfiguredBeforeAnySourceQuery() throws Exception {
        var reads = new AtomicInteger();
        var grid = fx(() -> grid(reads, new AtomicInteger(), new HashMap<>()));
        try {
            fx(() -> {
                var state = grid.snapshotSetup();
                grid.configureDefaults(
                        new GridSetup(
                                2,
                                state.definitionId(),
                                state.definitionVersion(),
                                state.columns(),
                                List.of(),
                                List.of(
                                        new TextFilter(
                                                "type", TextFilter.Operator.EQUALS, "A", true)),
                                new PageRequest.Paged(0, 25),
                                state.grouping()));
                assertEquals(0, reads.get());
                return null;
            });
            fx(grid::loadAsync).toCompletableFuture().get(5, TimeUnit.SECONDS);
            fx(() -> {
                assertEquals(2, grid.stateProperty().get().result().rows().size());
                assertEquals(new PageRequest.Paged(0, 25), grid.defaultSetup().page());
                return null;
            });
        } finally {
            fx(() -> {
                grid.close();
                return null;
            });
        }
    }

    @Test
    void explicitDataUpdateRecapturesValuesWithoutReplacingRowsOrQuerying() throws Exception {
        var groupValue = new AtomicReference<>("A");
        var reads = new AtomicInteger();
        var fields = new HashMap<Long, TextField>();
        var disposed = new AtomicInteger();
        var grid =
                fx(() -> {
                    var value = grid(reads, disposed, fields);
                    value.configureGrouping(
                            List.of(GroupColumn.<Item>text("type", item -> groupValue.get())),
                            true,
                            List.of("type"),
                            true,
                            1000);
                    return value;
                });
        fx(grid::loadAsync).toCompletableFuture().get(5, TimeUnit.SECONDS);
        try {
            fx(() -> {
                var editor = fields.get(1L);
                groupValue.set("B");
                assertEquals(1, grid.lookupAll(".group-heading").size());
                assertTrue(
                        grid.lookupAll(".group-heading").stream()
                                .map(Button.class::cast)
                                .allMatch(button -> button.getText().contains("Type: A")));
                grid.regroup();
                assertTrue(
                        grid.lookupAll(".group-heading").stream()
                                .map(Button.class::cast)
                                .allMatch(button -> button.getText().contains("Type: B")));
                assertSame(editor, fields.get(1L));
                assertEquals(1, reads.get());
                assertEquals(0, disposed.get());
                return null;
            });
        } finally {
            fx(() -> {
                grid.close();
                return null;
            });
        }
    }
}
