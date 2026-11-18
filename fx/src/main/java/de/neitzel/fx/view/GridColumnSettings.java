package de.neitzel.fx.view;

import de.neitzel.grid.ColumnWidth;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.util.*;

/** Keyboard-operable equivalents of header resizing, reordering and grouping. */
final class GridColumnSettings<K, T> {
    private final Dialog<Void> dialog = new Dialog<>();

    GridColumnSettings(DataGrid<K, T> grid, TableResults<K, T> renderer) {
        dialog.setTitle("Column settings");
        if (grid.getScene() != null) dialog.initOwner(grid.getScene().getWindow());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        ComboBox<ResultColumn> column = new ComboBox<>();
        column.getItems().setAll(renderer.definitions());
        column.setConverter(
                new StringConverter<>() {
                    public String toString(ResultColumn c) {
                        return c == null ? "" : c.header();
                    }

                    public ResultColumn fromString(String text) {
                        throw new UnsupportedOperationException();
                    }
                });
        column.setAccessibleText("Column");
        column.setId("gridSettingsColumn");
        Spinner<Double> width = new Spinner<>();
        width.setEditable(true);
        width.setAccessibleText("Column width");
        Button apply = new Button("Apply width"),
                left = new Button("Move left"),
                right = new Button("Move right"),
                group = new Button("Group by column"),
                earlier = new Button("Earlier grouping level"),
                later = new Button("Later grouping level"),
                remove = new Button("Remove grouping");
        Label description = new Label(), message = new Label();
        message.setWrapText(true);
        Runnable update =
                () -> {
                    var c = column.getValue();
                    if (c == null) return;
                    width.setValueFactory(
                            new SpinnerValueFactory.DoubleSpinnerValueFactory(
                                    c.minimumWidth(),
                                    c.maximumWidth(),
                                    renderer.width(c.id()),
                                    10));
                    int index = renderer.columnOrder().indexOf(c.id()),
                            level = renderer.groupingColumns().indexOf(c.id());
                    description.setText(
                            "Position "
                                    + (index + 1)
                                    + (c.fixedPosition() >= 0 ? " (fixed)" : "")
                                    + " · "
                                    + c.minimumWidth()
                                    + "–"
                                    + c.maximumWidth()
                                    + " px");
                    left.setDisable(!canMove(renderer, c.id(), index - 1));
                    right.setDisable(!canMove(renderer, c.id(), index + 1));
                    group.setDisable(!renderer.canGroup(c.id()) || level >= 0);
                    earlier.setDisable(!renderer.groupingEnabled() || level <= 0);
                    later.setDisable(
                            !renderer.groupingEnabled()
                                    || level < 0
                                    || level == renderer.groupingColumns().size() - 1);
                    remove.setDisable(!renderer.groupingEnabled() || level < 0);
                };
        java.util.function.Consumer<Runnable> perform =
                action -> {
                    try {
                        action.run();
                        message.setText("Applied.");
                    } catch (RuntimeException failure) {
                        message.setText(failure.getMessage());
                    }
                    update.run();
                };
        left.setOnAction(
                e -> perform.accept(() -> move(grid, renderer, column.getValue().id(), -1)));
        right.setOnAction(
                e -> perform.accept(() -> move(grid, renderer, column.getValue().id(), 1)));
        apply.setOnAction(e ->
                perform.accept(() ->
                        grid.setColumnWidth(
                                column.getValue().id(),
                                new ColumnWidth.Explicit(
                                        Double.parseDouble(width.getEditor().getText())))));
        group.setOnAction(e ->
                perform.accept(() -> {
                    var ids = new ArrayList<>(renderer.groupingColumns());
                    ids.add(column.getValue().id());
                    grid.setGroupingColumns(ids);
                }));
        remove.setOnAction(e ->
                perform.accept(() ->
                        grid.setGroupingColumns(
                                renderer.groupingColumns().stream()
                                        .filter(id -> !id.equals(column.getValue().id()))
                                        .toList())));
        earlier.setOnAction(
                e -> perform.accept(() -> moveGroup(grid, renderer, column.getValue().id(), -1)));
        later.setOnAction(
                e -> perform.accept(() -> moveGroup(grid, renderer, column.getValue().id(), 1)));
        VBox content =
                new VBox(
                        10,
                        new Label("Column"),
                        column,
                        description,
                        new HBox(8, left, right),
                        new Label("Width in pixels"),
                        new HBox(8, width, apply),
                        new HBox(8, group, remove),
                        new HBox(8, earlier, later),
                        message);
        dialog.getDialogPane().setContent(content);
        column.valueProperty().addListener((o, b, a) -> update.run());
        column.getSelectionModel().selectFirst();
    }

    void show() {
        dialog.show();
    }

    void close() {
        dialog.close();
    }

    private static boolean canMove(TableResults<?, ?> renderer, String id, int index) {
        var order = new ArrayList<>(renderer.columnOrder());
        if (index < 0 || index >= order.size()) return false;
        order.remove(id);
        order.add(index, id);
        return renderer.definitions().stream()
                .allMatch(
                        c -> c.fixedPosition() < 0 || order.get(c.fixedPosition()).equals(c.id()));
    }

    private static <K, T> void move(
            DataGrid<K, T> grid, TableResults<K, T> renderer, String id, int delta) {
        var order = new ArrayList<>(renderer.columnOrder());
        int index = order.indexOf(id);
        order.remove(id);
        order.add(index + delta, id);
        grid.setColumnOrder(order);
    }

    private static <K, T> void moveGroup(
            DataGrid<K, T> grid, TableResults<K, T> renderer, String id, int delta) {
        var order = new ArrayList<>(renderer.groupingColumns());
        int index = order.indexOf(id);
        order.remove(id);
        order.add(index + delta, id);
        grid.setGroupingColumns(order);
    }
}
