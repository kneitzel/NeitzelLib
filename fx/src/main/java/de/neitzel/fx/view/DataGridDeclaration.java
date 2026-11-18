package de.neitzel.fx.view;

import de.neitzel.fx.grid.filter.FilterColumn;
import de.neitzel.grid.*;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** FXML construction and owned local-source lifetime, separate from query coordination. */
final class DataGridDeclaration<K, T> implements AutoCloseable {
    private final DataGrid<K, T> grid;
    private final ObservableList<DataGridColumn> columns = FXCollections.observableArrayList();
    private final ObjectProperty<ObservableList<T>> items = new SimpleObjectProperty<>();
    private final ObjectProperty<GridSource<K, T>> source = new SimpleObjectProperty<>();
    private final HBox preview = new HBox();
    private final ListChangeListener<T> itemsChanged = change -> changedItems();
    private ExecutorService executor;
    private boolean external;
    private boolean assigningInitialSource;
    Function<T, ResultRow> rowFactory;
    Function<T, K> keyFactory;
    Comparator<K> keyComparator;
    String keyAttribute;
    String setupId;
    int setupVersion = 1;
    int pageSize = 50;
    int maximumPageSize = 250;
    boolean groupingEnabled;
    String groupBy = "";
    boolean groupsCollapsed;
    int inactiveGroupHistoryLimit = 1000;

    DataGridDeclaration(DataGrid<K, T> grid) {
        this.grid = grid;
        preview.getStyleClass().add("data-grid-preview-header");
        columns.addListener(
                (ListChangeListener<DataGridColumn>)
                        change -> {
                            beforeOpen();
                            preview.getChildren().clear();
                            for (var column : columns) {
                                var label = new Label(column.getHeader());
                                label.setPrefWidth(column.getWidth());
                                label.getStyleClass().add("column-header");
                                preview.getChildren().add(label);
                            }
                        });
        items.addListener((observable, previous, current) -> {
            requireFx();
            if (grid.isClosed()) throw new IllegalStateException("Grid is closed");
            if (external && grid.isInitialized())
                throw new IllegalStateException("External sources cannot use items");
            if (previous != null) previous.removeListener(itemsChanged);
            if (current != null) current.addListener(itemsChanged);
            changedItems();
        });
        source.addListener((observable, previous, current) -> {
            requireFx();
            if (assigningInitialSource) return;
            if (grid.isClosed()) throw new IllegalStateException("Grid is closed");
            Objects.requireNonNull(current, "Missing source");
            if (items.get() != null) throw new IllegalStateException("Choose items or source");
            if (grid.isInitialized()) {
                external = true;
                grid.replaceSource(current);
            }
        });
    }

    void setInitialSource(GridSource<K, T> value) {
        assigningInitialSource = true;
        try {
            source.set(Objects.requireNonNull(value));
            external = true;
        } finally {
            assigningInitialSource = false;
        }
    }

    ObservableList<DataGridColumn> columns() {
        return columns;
    }

    ObjectProperty<ObservableList<T>> itemsProperty() {
        return items;
    }

    ObjectProperty<GridSource<K, T>> sourceProperty() {
        return source;
    }

    HBox preview() {
        return preview;
    }

    void beforeOpen() {
        requireFx();
        if (grid.isClosed() || grid.isInitialized())
            throw new IllegalStateException("Configure before initializing the grid");
    }

    private static void requireFx() {
        if (!Platform.isFxApplicationThread())
            throw new IllegalStateException("Use the JavaFX application thread");
    }

    private void changedItems() {
        requireFx();
        grid.refreshItems();
    }

    @SuppressWarnings("unchecked")
    void open() {
        var definitions = columns.stream().map(DataGridColumn::definition).toList();
        if (definitions.isEmpty())
            throw new IllegalArgumentException("Declare DataGrid columns before loading");
        if (definitions.stream().map(ResultColumn::id).distinct().count() != definitions.size())
            throw new IllegalArgumentException("Duplicate DataGrid column ID");
        GridSource<K, T> configured = source.get();
        MemoryGridSource<K, T> memory = null;
        if (configured == null) {
            if (keyFactory == null && (keyAttribute == null || keyAttribute.isBlank()))
                throw new IllegalArgumentException(
                        "Local DataGrid requires keyAttribute or keyFactory");
            Function<T, K> key =
                    keyFactory != null
                            ? keyFactory
                            : row -> (K) DataGridColumn.read(row, keyAttribute);
            Comparator<K> order =
                    keyComparator != null
                            ? keyComparator
                            : (a, b) -> {
                                if (!(a instanceof Comparable<?>))
                                    throw new IllegalArgumentException(
                                            "Row keys require a comparator");
                                return ((Comparable<Object>) a).compareTo(b);
                            };
            executor = Executors.newVirtualThreadPerTaskExecutor();
            memory =
                    new MemoryGridSource<>(
                            () -> items.get() == null ? List.of() : items.get(),
                            key,
                            order,
                            columns.stream()
                                    .map(c -> new MemoryColumn<T>(c.getId(), c.getKind(), c::value))
                                    .toList(),
                            executor,
                            maximumPageSize);
            configured = memory;
        } else {
            if (items.get() != null) throw new IllegalArgumentException("Choose items or source");
            external = true;
        }
        configured.capabilities().validate(new PageRequest.Paged(0, pageSize));
        grid.initialize(definitions, rowFactory == null ? this::render : rowFactory, configured);
        grid.setInitialPageSize(pageSize);
        for (var column : columns) {
            if (!column.isFilterable()) continue;
            FilterColumn filter =
                    switch (column.getKind()) {
                        case TEXT -> FilterColumn.text();
                        case INTEGER, DECIMAL ->
                                FilterColumn.number(
                                        Locale.getDefault(),
                                        column.getKind() == MemoryColumn.Kind.INTEGER,
                                        null,
                                        null);
                        case BOOLEAN -> null;
                    };
            if (filter == null) {
                if (column.getChecklist() != FilterColumn.Checklist.DISABLED)
                    throw new IllegalArgumentException(
                            "Boolean filter editor is not available; set filterable=false");
                continue;
            }
            var candidates = column.getCandidates();
            if (candidates == null && memory != null)
                candidates = memory.candidateProvider(GroupColumn::display);
            grid.configureFilter(
                    column.getId(), filter.withChecklist(column.getChecklist(), candidates));
        }
        grid.configureGrouping(
                columns.stream()
                        .map(c ->
                                new GroupColumn<T>(
                                        c.getId(), c.getKind(), c::groupValue, c::groupLabel))
                        .toList(),
                groupingEnabled,
                groupBy.isBlank()
                        ? List.of()
                        : Arrays.stream(groupBy.split(",")).map(String::trim).toList(),
                groupsCollapsed,
                inactiveGroupHistoryLimit);
        if (setupId != null && !setupId.isBlank()) {
            Map<String, String> types = new LinkedHashMap<>();
            columns.forEach(c -> types.put(c.getId(), c.schemaType()));
            grid.configureSetups(setupId, setupVersion, types, Map.of());
        }
    }

    private ResultRow render(T row) {
        HBox root = new HBox();
        root.setUserData(row);
        Map<String, Region> cells = new LinkedHashMap<>();
        for (var column : columns) {
            Label label = new Label(Objects.toString(column.getValue(row), ""));
            root.getChildren().add(label);
            cells.put(column.getId(), label);
        }
        return new ResultRow(root, cells, () -> {});
    }

    @Override
    public void close() {
        if (items.get() != null) items.get().removeListener(itemsChanged);
        items.unbind();
        source.unbind();
        if (executor != null) executor.shutdownNow();
    }
}
