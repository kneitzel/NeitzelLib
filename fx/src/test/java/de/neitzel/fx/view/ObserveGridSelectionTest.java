package de.neitzel.fx.view;

import de.neitzel.fx.view.DataGridTest.Item;
import de.neitzel.grid.TextFilter;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObserveGridSelectionTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxThread.start();
    }

    @Test
    void publishesOneCoherentChangeToTheViewAndStopsAfterClose() throws Exception {
        var first = new Item(1, "Alpha");
        var second = new Item(2, "Beta");
        var grid = new AtomicReference<DataGrid<Long, Item>>();
        FxThread.onFx(() -> {
            grid.set(DataGridTest.grid(List.of(first, second), new AtomicInteger()));
            grid.get().refresh();
        });
        try {
            DataGridTest.drain();
            FxThread.onFx(() -> {
                var control = grid.get();
                control.select(1L);
                var document = getClass().getResource("grid-selection-summary.fxml");
                var context = new TestViews();
                try (var selection = control.observeSelection();
                     var models = new ViewModelContext();
                     var view = ViewLoader.loadWithDependencies(document, selection, context, models)) {
                    var initial = selection.getSelection();
                    var summary = (Label) view.getRoot().lookup("#summary");
                    assertEquals(List.of(1L), initial.selectedKeys());
                    assertEquals(List.of(first), initial.selectedElements());
                    assertEquals("[1] · 1 visible", summary.getText());
                    assertThrows(UnsupportedOperationException.class, () -> initial.selectedKeys().clear());
                    assertThrows(UnsupportedOperationException.class, () -> initial.selectedElements().clear());

                    var changes = new ArrayList<GridSelection<Long, Item>>();
                    selection.selectionProperty().addListener((observable, before, after) -> {
                        changes.add(after);
                        selection.selectKeys(after.selectedKeys());
                    });

                    var liveKeys = control.selectedKeysProperty().get();
                    selection.select(2L);
                    assertSame(liveKeys, control.selectedKeysProperty().get());
                    assertEquals(List.of(new GridSelection<>(List.of(2L), List.of(second), 2L, second)), changes);
                    assertEquals("[2] · 1 visible", summary.getText());
                    assertEquals(List.of(first), initial.selectedElements());

                    selection.close();
                    selection.close();
                    control.select(1L);
                    assertEquals("[2] · 1 visible", summary.getText());
                    assertEquals(1, changes.size());
                    assertEquals(List.of(1L), control.selectedKeysProperty());
                    assertThrows(IllegalStateException.class, () -> selection.select(2L));
                }
            });
        } finally {
            FxThread.onFx(() -> grid.get().close());
        }
    }

    @Test
    void keepsUnloadedKeysThroughPagingFiltersDeletionAndRestoration() throws Exception {
        var first = new Item(1, "Alpha");
        var second = new Item(2, "Beta");
        var items = new ArrayList<>(List.of(first, second));
        var grid = new AtomicReference<DataGrid<Long, Item>>();
        var selected = new AtomicReference<DataGridSelection<Long, Item>>();
        FxThread.onFx(() -> {
            var control = DataGridTest.grid(items, new AtomicInteger());
            grid.set(control);
            control.setSelectionMode(RowSelectionMode.MULTIPLE);
            selected.set(control.observeSelection());
            assertEquals(GridSelection.empty(), selected.get().getSelection());
            control.setPageSize(1);
        });
        try {
            DataGridTest.drain();
            FxThread.onFx(() -> {
                var adapter = selected.get();
                adapter.selectKeys(List.of(1L, 2L));
                var current = adapter.getSelection();
                assertEquals(List.of(1L, 2L), current.selectedKeys());
                assertEquals(List.of(first), current.selectedElements());
                assertEquals(2L, current.leadKey());
                assertNull(current.leadElement());
                grid.get().next();
                assertEquals(List.of(), adapter.getSelection().selectedElements());
            });
            DataGridTest.drain();
            FxThread.onFx(() -> {
                var current = selected.get().getSelection();
                assertEquals(List.of(1L, 2L), current.selectedKeys());
                assertEquals(List.of(second), current.selectedElements());
                assertSame(second, current.leadElement());
                grid.get().setFilters(List.of(new TextFilter("text", TextFilter.Operator.EQUALS, "Alpha", false)));
            });
            DataGridTest.drain();
            FxThread.onFx(() -> {
                assertEquals(List.of(1L, 2L), selected.get().getSelection().selectedKeys());
                assertEquals(List.of(first), selected.get().getSelection().selectedElements());
                items.remove(first);
                grid.get().confirmedDeleted(1L);
                assertEquals(List.of(2L), selected.get().getSelection().selectedKeys());
                grid.get().setFilters(List.of());
            });
            DataGridTest.drain();
            FxThread.onFx(() -> {
                var adapter = selected.get();
                assertEquals(List.of(second), adapter.getSelection().selectedElements());
                adapter.select(null);
                assertEquals(GridSelection.empty(), adapter.getSelection());
                adapter.selectKeys(List.of(2L));
                assertSame(second, adapter.getSelection().leadElement());
                grid.get().close();
                assertEquals(GridSelection.empty(), adapter.getSelection());
            });
        } finally {
            FxThread.onFx(() -> {
                selected.get().close();
                grid.get().close();
            });
        }
    }
}
