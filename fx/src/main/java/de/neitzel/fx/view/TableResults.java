package de.neitzel.fx.view;

import de.neitzel.grid.ColumnWidth;
import de.neitzel.grid.GridRow;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

import java.util.*;
import java.util.function.Function;

/**
 * Opt-in presentation of already queried rows. All operations run on the FX thread. This object
 * never queries, sorts, filters, or rebinds models. The future grid coordinator owns those
 * decisions. Closing releases rows; the Table remains in result mode and cannot be reopened as a
 * legacy Table.
 */
public final class TableResults<K, T> implements AutoCloseable {
    private final HBox header;
    private final VBox body;
    private final Function<T, ResultRow> factory;
    private final Map<String, ResultColumn> definitions = new LinkedHashMap<>();
    private final Map<String, ColumnWidth> widths = new LinkedHashMap<>();
    private List<String> order;
    private List<Entry<K, T>> entries = List.of();
    private final ReadOnlyObjectWrapper<K> selectedKey = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<T> selectedElement = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<RuntimeException> disposalFailure =
            new ReadOnlyObjectWrapper<>();
    private boolean closed;
    private boolean rowsInteractive = true;
    private Set<String> filterColumns = Set.of();
    private Set<String> filteredColumns = Set.of();
    private java.util.function.BiConsumer<String, Node> filterRequest = (id, node) -> {};
    private java.util.function.Consumer<String> sortRequest = id -> {};

    private record Entry<K, T>(GridRow<K, T> row, ResultRow view, EventHandler<MouseEvent> click) {}

    TableResults(
            HBox header, VBox body, List<ResultColumn> columns, Function<T, ResultRow> factory) {
        requireFx();
        this.header = header;
        this.body = body;
        this.factory = Objects.requireNonNull(factory);
        if (columns.isEmpty())
            throw new IllegalArgumentException("At least one column is required");
        for (var c : columns) {
            if (definitions.putIfAbsent(c.id(), c) != null)
                throw new IllegalArgumentException("Duplicate column ID");
            widths.put(c.id(), new ColumnWidth.Default());
        }
        order = List.copyOf(definitions.keySet());
        validateOrder(order);
        drawHeader();
        body.setOnMouseClicked(
                e -> {
                    if (e.getTarget() == body && e.getButton() == MouseButton.PRIMARY) select(null);
                });
    }

    public ReadOnlyObjectProperty<K> selectedKeyProperty() {
        return selectedKey.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<T> selectedElementProperty() {
        return selectedElement.getReadOnlyProperty();
    }

    /** Cleanup failures are exposed separately: they do not roll back an already published page. */
    public ReadOnlyObjectProperty<RuntimeException> disposalFailureProperty() {
        return disposalFailure.getReadOnlyProperty();
    }

    public List<String> columnOrder() {
        return order;
    }

    public Map<String, ColumnWidth> columnWidths() {
        return Map.copyOf(widths);
    }

    /** Marks retained rows unavailable without disabling header/query controls. */
    public void setRowsInteractive(boolean interactive) {
        checkOpen();
        rowsInteractive = interactive;
        body.setDisable(!interactive);
        markSelection();
    }

    /** Header requests are delegated to the coordinator, never applied locally. */
    public void setOnSortRequest(java.util.function.Consumer<String> handler) {
        checkOpen();
        sortRequest = Objects.requireNonNull(handler);
    }

    /** Opt-in filter actions; clicking the icon never issues a sort request. */
    public void setFilterActions(
            Set<String> columns, java.util.function.BiConsumer<String, Node> handler) {
        checkOpen();
        if (!definitions.keySet().containsAll(columns))
            throw new IllegalArgumentException("Unknown filter column");
        filterColumns = Set.copyOf(columns);
        filterRequest = Objects.requireNonNull(handler);
        drawHeader();
    }

    /**
     * Published filter state; updates existing header controls without recreating an open editor.
     */
    public void setFilteredColumns(Set<String> ids) {
        checkOpen();
        if (filteredColumns.equals(ids)) return;
        filteredColumns = Set.copyOf(ids);
        for (Node cell : header.getChildren())
            if (cell instanceof HBox box)
                for (Node child : box.getChildren())
                    if (child instanceof Button button
                            && button.getStyleClass().contains("filter-button"))
                        markFilter(button, (String) cell.getUserData());
    }

    private void markFilter(Button button, String id) {
        boolean active = filteredColumns.contains(id);
        button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("filtered"), active);
        button.setText(active ? "•" : "");
        button.setAccessibleText(
                "Filter " + definitions.get(id).header() + (active ? " (active)" : ""));
        button.setTooltip(
                new Tooltip(
                        "Filter " + definitions.get(id).header() + (active ? " — active" : "")));
    }

    /** Retains an off-page key while exposing no loaded selected element. */
    public void select(K key) {
        checkOpen();
        selectedKey.set(key);
        markSelection();
    }

    /**
     * Stages fresh rows before replacing the visible page; failed construction preserves the old
     * page.
     */
    public void show(List<GridRow<K, T>> result) {
        show(result, order, widths);
    }

    /** Stages both fresh rows and complete layout, publishing neither on preparation failure. */
    public void show(
            List<GridRow<K, T>> result,
            List<String> nextOrder,
            Map<String, ColumnWidth> nextWidths) {
        checkOpen();
        nextOrder = List.copyOf(nextOrder);
        validateOrder(nextOrder);
        nextWidths = Map.copyOf(nextWidths);
        if (!nextWidths.keySet().equals(definitions.keySet()))
            throw new IllegalArgumentException("Width mapping must match columns");
        Map<String, ColumnWidth> preparedWidths = new LinkedHashMap<>();
        for (var c : definitions.values()) {
            var width = nextWidths.get(c.id());
            preparedWidths.put(
                    c.id(),
                    width instanceof ColumnWidth.Explicit e
                            ? new ColumnWidth.Explicit(
                                    Math.clamp(e.value(), c.minimumWidth(), c.maximumWidth()))
                            : width);
        }
        var nextHeader = headerNodes(nextOrder, preparedWidths);
        result = List.copyOf(result);
        Set<K> keys = new HashSet<>();
        for (var row : result)
            if (!keys.add(row.key())) throw new IllegalArgumentException("Duplicate row key");
        List<Entry<K, T>> next = new ArrayList<>();
        Set<Node> roots = Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            for (var row : result) {
                ResultRow view =
                        Objects.requireNonNull(
                                factory.apply(row.model()), "Row factory returned null");
                // Never dispose a live row accidentally returned by a faulty factory.
                if (view.root().getParent() != null || !roots.add(view.root()))
                    throw new IllegalArgumentException(
                            "Row factory must create detached fresh rows");
                EventHandler<MouseEvent> click =
                        e -> {
                            if (e.getButton() == MouseButton.PRIMARY) select(row.key());
                        };
                next.add(new Entry<>(row, view, click));
                validateRow(view);
                view.root().getStyleClass().add("result-row");
                layoutRow(view, nextOrder, preparedWidths);
                view.root().addEventHandler(MouseEvent.MOUSE_CLICKED, click);
            }
        } catch (RuntimeException failure) {
            release(next);
            throw failure;
        }
        var old = entries;
        entries = List.copyOf(next);
        order = nextOrder;
        widths.clear();
        widths.putAll(preparedWidths);
        header.getChildren().setAll(nextHeader);
        body.getChildren().setAll(entries.stream().map(e -> e.view().root()).toList());
        markSelection();
        release(old);
    }

    /** Changes header and mapped row order atomically after validating fixed positions. */
    public void setColumnOrder(List<String> ids) {
        checkOpen();
        ids = List.copyOf(ids);
        validateOrder(ids);
        order = ids;
        drawHeader();
        entries.forEach(e -> layoutRow(e.view()));
    }

    /**
     * Returns the applied width; explicit widths are clamped to current limits, preserving
     * default-vs-explicit intent.
     */
    public double setColumnWidth(String id, ColumnWidth width) {
        checkOpen();
        ResultColumn c = Objects.requireNonNull(definitions.get(id), "Unknown column");
        Objects.requireNonNull(width);
        double applied =
                width instanceof ColumnWidth.Explicit e
                        ? Math.clamp(e.value(), c.minimumWidth(), c.maximumWidth())
                        : c.defaultWidth();
        widths.put(
                id,
                width instanceof ColumnWidth.Explicit ? new ColumnWidth.Explicit(applied) : width);
        drawHeader();
        entries.forEach(e -> layoutRow(e.view()));
        return applied;
    }

    private void validateOrder(List<String> ids) {
        if (ids.size() != definitions.size() || !new HashSet<>(ids).equals(definitions.keySet()))
            throw new IllegalArgumentException("Order must contain each column exactly once");
        for (int i = 0; i < ids.size(); i++) {
            int fixed = definitions.get(ids.get(i)).fixedPosition();
            if (fixed >= 0 && fixed != i)
                throw new IllegalArgumentException("Fixed column displaced");
        }
    }

    private void validateRow(ResultRow view) {
        if (!view.root().getPadding().equals(javafx.geometry.Insets.EMPTY))
            throw new IllegalArgumentException(
                    "Result row padding must be zero; put padding inside mapped fields");
        if (!view.fields().keySet().equals(definitions.keySet()))
            throw new IllegalArgumentException("Row mapping must match columns");
        Set<Node> nodes = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var field : view.fields().values())
            if (!nodes.add(field) || field.getParent() != view.root())
                throw new IllegalArgumentException(
                        "Mapped fields must be distinct direct children");
    }

    private double width(String id) {
        return width(id, widths);
    }

    private double width(String id, Map<String, ColumnWidth> sizes) {
        return sizes.get(id) instanceof ColumnWidth.Explicit e
                ? e.value()
                : definitions.get(id).defaultWidth();
    }

    private void drawHeader() {
        header.getChildren().setAll(headerNodes(order, widths));
    }

    private List<Node> headerNodes(List<String> ordering, Map<String, ColumnWidth> sizes) {
        List<Node> nodes = new ArrayList<>();
        for (String id : ordering) {
            Label label = new Label(definitions.get(id).header());
            label.getStyleClass().add("column-header");
            HBox cell = new HBox(label);
            cell.getStyleClass().add("column-cell");
            cell.setUserData(id);
            cell.setOnMouseClicked(
                    event -> {
                        if (event.getButton() == MouseButton.PRIMARY) sortRequest.accept(id);
                    });
            if (filterColumns.contains(id)) {
                SVGPath icon = new SVGPath();
                icon.setContent("M0 0 H14 L9 6 V12 L5 14 V6 Z");
                icon.getStyleClass().add("filter-icon");
                Button button = new Button("", icon);
                button.getStyleClass().add("filter-button");
                button.setAccessibleText("Filter " + definitions.get(id).header());
                button.setTooltip(new Tooltip("Filter " + definitions.get(id).header()));
                markFilter(button, id);
                button.setOnAction(e -> filterRequest.accept(id, button));
                button.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
                cell.getChildren().add(button);
            }
            size(cell, width(id, sizes));
            nodes.add(cell);
        }
        return nodes;
    }

    private static void size(Region region, double width) {
        region.setMinWidth(width);
        region.setPrefWidth(width);
        region.setMaxWidth(width);
    }

    private void layoutRow(ResultRow view) {
        layoutRow(view, order, widths);
    }

    private void layoutRow(ResultRow view, List<String> ordering, Map<String, ColumnWidth> sizes) {
        Set<Region> fields = Collections.newSetFromMap(new IdentityHashMap<>());
        fields.addAll(view.fields().values());
        List<Node> children = new ArrayList<>();
        for (String id : ordering) {
            Region field = view.fields().get(id);
            size(field, width(id, sizes));
            children.add(field);
        }
        for (Node child : view.root().getChildren())
            if (!fields.contains(child)) children.add(child);
        view.root().setSpacing(0);
        view.root().getChildren().setAll(children);
    }

    private void markSelection() {
        T loaded = null;
        for (var e : entries) {
            boolean selected = Objects.equals(selectedKey.get(), e.row().key());
            e.view()
                    .root()
                    .pseudoClassStateChanged(
                            javafx.css.PseudoClass.getPseudoClass("selected"), selected);
            e.view().root().getStyleClass().remove("selected");
            if (selected) {
                e.view().root().getStyleClass().add("selected");
                loaded = e.row().model();
            }
        }
        selectedElement.set(rowsInteractive ? loaded : null);
    }

    private void release(List<Entry<K, T>> retired) {
        RuntimeException aggregate = null;
        for (var e : retired) {
            e.view().root().removeEventHandler(MouseEvent.MOUSE_CLICKED, e.click());
            try {
                e.view().dispose().run();
            } catch (RuntimeException failure) {
                if (aggregate == null) aggregate = new IllegalStateException("Row disposal failed");
                aggregate.addSuppressed(failure);
            }
        }
        if (aggregate != null) disposalFailure.set(aggregate);
    }

    private static void requireFx() {
        if (!Platform.isFxApplicationThread())
            throw new IllegalStateException("Use the JavaFX application thread");
    }

    private void checkOpen() {
        requireFx();
        if (closed) throw new IllegalStateException("Result renderer is closed");
    }

    @Override
    public void close() {
        requireFx();
        if (closed) return;
        closed = true;
        var old = entries;
        entries = List.of();
        body.getChildren().clear();
        header.getChildren().clear();
        body.setOnMouseClicked(null);
        selectedKey.set(null);
        selectedElement.set(null);
        release(old);
    }
}
