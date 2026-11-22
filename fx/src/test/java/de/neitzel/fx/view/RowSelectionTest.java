package de.neitzel.fx.view;

import de.neitzel.grid.GridRow;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RowSelectionTest {
    @BeforeAll static void toolkit() throws Exception { DataGridTest.start(); }

    private static MouseEvent click(boolean shortcut, boolean shift) {
        boolean mac = System.getProperty("os.name").startsWith("Mac");
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, MouseButton.PRIMARY, 1,
                shift, shortcut && !mac, false, shortcut && mac,
                false, false, false, false, false, true, null);
    }

    @Test void mouseModifiersModesAndVisibleHighlight() throws Exception {
        DataGridTest.fx(() -> {
            var roots = new ArrayList<HBox>();
            var table = new Table();
            var renderer = table.<Integer, String>openResults(TableResultsTest.columns(), s -> {
                var row = TableResultsTest.row(s, new AtomicInteger());
                roots.add(row.root());
                return row;
            });
            try {
                renderer.show(List.of(new GridRow<>(1, "A"), new GridRow<>(2, "B"),
                        new GridRow<>(3, "C"), new GridRow<>(4, "D")));
                assertEquals(RowSelectionMode.SINGLE, renderer.getSelectionMode());
                roots.get(0).fireEvent(click(false, false));
                roots.get(2).fireEvent(click(true, false));
                assertEquals(List.of(3), renderer.selectedKeysProperty());
                renderer.setSelectionMode(RowSelectionMode.MULTIPLE);
                roots.get(0).fireEvent(click(false, false));
                roots.get(2).fireEvent(click(true, false));
                assertEquals(List.of(1, 3), renderer.selectedKeysProperty());
                roots.get(3).fireEvent(click(false, true));
                assertEquals(List.of(3, 4), renderer.selectedKeysProperty());
                roots.get(1).fireEvent(click(true, true));
                assertEquals(Set.of(2, 3, 4), new HashSet<>(renderer.selectedKeysProperty()));
                roots.get(2).fireEvent(click(true, false));
                assertEquals(List.of(4, 2), renderer.selectedKeysProperty());
                assertEquals("B", renderer.selectedElementProperty().get());
                assertEquals(List.of("B", "D"), renderer.selectedElementsProperty());
                assertThrows(UnsupportedOperationException.class,
                        () -> renderer.selectedKeysProperty().clear());
                new Scene(table);
                table.applyCss();
                assertNotNull(roots.get(1).getBackground(), "Default selection is visibly styled");
                assertTrue(roots.get(1).getPseudoClassStates().stream()
                        .anyMatch(p -> p.getPseudoClassName().equals("selected")));
                renderer.setSelectionMode(RowSelectionMode.SINGLE);
                assertEquals(List.of(2), renderer.selectedKeysProperty());
                assertThrows(IllegalArgumentException.class, () -> renderer.selectKeys(List.of(1, 2)));
                renderer.setSelectionMode(RowSelectionMode.NONE);
                roots.get(0).fireEvent(click(false, false));
                renderer.select(1);
                assertTrue(renderer.selectedKeysProperty().isEmpty());
                assertNull(renderer.selectedElementProperty().get());
            } finally { renderer.close(); }
        });
    }

    @Test void keyboardAndChildEditorAndRetainedIdentities() throws Exception {
        DataGridTest.fx(() -> {
            var roots = new ArrayList<HBox>();
            var renderer = new Table().<Integer, String>openResults(TableResultsTest.columns(), s -> {
                var row = TableResultsTest.row(s, new AtomicInteger());
                roots.add(row.root());
                return row;
            });
            try {
                renderer.setSelectionMode(RowSelectionMode.MULTIPLE);
                renderer.show(List.of(new GridRow<>(1, "A"), new GridRow<>(2, "B")));
                renderer.select(1);
                roots.getFirst().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN,
                        true, false, false, false));
                assertEquals(List.of(1, 2), renderer.selectedKeysProperty());
                ((Label) roots.getFirst().getChildren().getFirst()).fireEvent(
                        new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                                false, false, false, false));
                assertEquals(List.of(1, 2), renderer.selectedKeysProperty(), "Child keys are left alone");
                renderer.setRowsInteractive(false);
                assertTrue(renderer.selectedElementsProperty().isEmpty());
                assertEquals(List.of(1, 2), renderer.selectedKeysProperty());
                renderer.show(List.of(new GridRow<>(2, "new B")));
                renderer.setRowsInteractive(true);
                assertEquals(List.of("new B"), renderer.selectedElementsProperty());
                renderer.deselect(1);
                assertEquals(List.of(2), renderer.selectedKeysProperty());
                roots.getLast().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                        false, false, false, false));
                assertTrue(renderer.selectedKeysProperty().isEmpty());
            } finally { renderer.close(); }
            assertTrue(renderer.selectedElementsProperty().isEmpty());
        });
    }

    @Test void fxmlSelectionModeAndCoordinatorSelection() throws Exception {
        DataGridTest.fx(() -> {
            try {
                DataGrid<?, ?> declarative = new javafx.fxml.FXMLLoader().load(
                        new java.io.ByteArrayInputStream(("<?import de.neitzel.fx.view.DataGrid?>"
                                + "<DataGrid selectionMode=\"NONE\"/>").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                assertEquals(RowSelectionMode.NONE, declarative.getSelectionMode());
                declarative.close();
            } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        });
        var reference = new java.util.concurrent.atomic.AtomicReference<DataGrid<Long, DataGridTest.Item>>();
        DataGridTest.fx(() -> {
            var grid = DataGridTest.grid(List.of(new DataGridTest.Item(1, "A"),
                    new DataGridTest.Item(2, "B")), new AtomicInteger());
            reference.set(grid);
            grid.setSelectionMode(RowSelectionMode.MULTIPLE);
            grid.refresh();
        });
        DataGridTest.drain();
        DataGridTest.fx(() -> {
            var grid = reference.get();
            try {
                grid.selectKeys(List.of(1L, 2L));
                assertEquals(2, grid.selectedElementsProperty().size());
                grid.confirmedDeleted(1L);
                assertEquals(List.of(2L), grid.selectedKeysProperty());
                grid.setSelectionMode(RowSelectionMode.NONE);
                assertTrue(grid.selectedKeysProperty().isEmpty());
            } finally { grid.close(); }
        });
    }

    @Test void rangesFollowVisibleGroupOrderAndCollapsedRowsAreNotActionable() throws Exception {
        DataGridTest.fx(() -> {
            var roots = new ArrayList<HBox>();
            var renderer = new Table().<Integer, String>openResults(TableResultsTest.columns(), s -> {
                var row = TableResultsTest.row(s, new AtomicInteger());
                roots.add(row.root());
                return row;
            });
            try {
                renderer.setSelectionMode(RowSelectionMode.MULTIPLE);
                renderer.configureGrouping(List.of(de.neitzel.grid.GroupColumn.<String>text(
                        "name", s -> s.substring(0, 1))), true, List.of("name"), false, 100);
                renderer.show(List.of(new GridRow<>(1, "A1"), new GridRow<>(2, "B1"), new GridRow<>(3, "A2")));
                roots.get(0).fireEvent(click(false, false));
                roots.get(1).fireEvent(click(false, true));
                assertEquals(List.of(1, 3, 2), renderer.selectedKeysProperty());
                renderer.setExpanded(new de.neitzel.grid.GroupPath(List.of(
                        new de.neitzel.grid.GroupPath.Segment("name", new de.neitzel.grid.FilterValue.Text("A")))), false);
                assertEquals(List.of("B1"), renderer.selectedElementsProperty());
                assertEquals(3, renderer.selectedKeysProperty().size());
            } finally { renderer.close(); }
        });
    }
}
