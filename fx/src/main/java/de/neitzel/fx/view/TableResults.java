package de.neitzel.fx.view;

import de.neitzel.grid.*;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
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
    private final ObservableList<K> selectedKeys = FXCollections.observableArrayList();
    private final ObservableList<T> selectedElements = FXCollections.observableArrayList();
    private final ReadOnlyListWrapper<K> keysView = new ReadOnlyListWrapper<>(
            FXCollections.unmodifiableObservableList(selectedKeys));
    private final ReadOnlyListWrapper<T> elementsView = new ReadOnlyListWrapper<>(
            FXCollections.unmodifiableObservableList(selectedElements));
    private RowSelectionMode selectionMode = RowSelectionMode.SINGLE;
    private K anchor;
    private final ReadOnlyObjectWrapper<RuntimeException> disposalFailure =
            new ReadOnlyObjectWrapper<>();
    private boolean closed;
    private boolean rowsInteractive = true;
    private boolean columnSeparators;
    private Set<String> filterColumns = Set.of();
    private Set<String> filteredColumns = Set.of();
    private List<SortKey> sortKeys = List.of();
    private java.util.function.BiConsumer<String, Node> filterRequest = (id, node) -> {};
    private java.util.function.Consumer<String> sortRequest = id -> {};

    private List<GroupColumn<T>> groupColumns = List.of();
    private List<String> grouping = List.of();
    private boolean groupingEnabled, defaultCollapsed = true;
    private int inactiveHistoryLimit = 1000;
    private GroupExpansion expansion = new GroupExpansion(true, 1000);
    private GroupProjection.Snapshot<K, T> captured = GroupProjection.capture(List.of(), List.of());
    private List<GroupProjection.Node<K, T>> groupTree = List.of();
    private final Map<GroupPath, Button> disclosure = new HashMap<>();
    private Runnable layoutCommitted = () -> {};
    private java.util.function.Consumer<String> interactionMessage = ignored -> {};
    private final ResultHeaderGestures gestures;
    private final ResultScrollAlignment scrollAlignment;

    private record Entry<K, T>(GridRow<K, T> row, ResultRow view,
            EventHandler<MouseEvent> click, EventHandler<KeyEvent> key) {}

    TableResults(
            HBox header,
            VBox body,
            List<ResultColumn> columns,
            Function<T, ResultRow> factory,
            javafx.scene.control.ScrollPane headerScroll,
            javafx.scene.control.ScrollPane bodyScroll) {
        requireFx();
        this.header = header;
        this.body = body;
        this.factory = Objects.requireNonNull(factory);
        gestures = new ResultHeaderGestures(this, header, body);
        if (columns.isEmpty())
            throw new IllegalArgumentException("At least one column is required");
        for (var c : columns) {
            if (definitions.putIfAbsent(c.id(), c) != null)
                throw new IllegalArgumentException("Duplicate column ID");
            widths.put(c.id(), new ColumnWidth.Default());
        }
        order = List.copyOf(definitions.keySet());
        validateOrder(order);
        scrollAlignment = new ResultScrollAlignment(header, body, headerScroll, bodyScroll);
        drawHeader();
        body.setOnMouseClicked(e -> {
            if (e.getTarget() == body && e.getButton() == MouseButton.PRIMARY) select(null);
        });
    }

    void enableColumnSeparators() {
        checkOpen();
        columnSeparators = true;
        drawHeader();
        entries.forEach(e -> layoutRow(e.view()));
    }

    public ReadOnlyObjectProperty<K> selectedKeyProperty() {
        return selectedKey.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<T> selectedElementProperty() {
        return selectedElement.getReadOnlyProperty();
    }

    public ReadOnlyListProperty<K> selectedKeysProperty() { return keysView.getReadOnlyProperty(); }

    /** Only selected models currently visible and interactive are actionable. */
    public ReadOnlyListProperty<T> selectedElementsProperty() { return elementsView.getReadOnlyProperty(); }

    public RowSelectionMode getSelectionMode() { return selectionMode; }

    public void setSelectionMode(RowSelectionMode mode) {
        checkOpen();
        selectionMode = Objects.requireNonNull(mode);
        if (mode == RowSelectionMode.NONE) select(null);
        else if (mode == RowSelectionMode.SINGLE && selectedKeys.size() > 1) select(selectedKey.get());
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

    public void setSortKeys(List<SortKey> keys) {
        checkOpen();
        if (keys.size() > 1 || keys.stream().anyMatch(k -> !definitions.containsKey(k.columnId())))
            throw new IllegalArgumentException("Unknown sort column");
        sortKeys = List.copyOf(keys);
        for (Node node : header.getChildren()) {
            HBox cell = (HBox) node;
            Label label = (Label) cell.getChildren().getFirst();
            label.setText(headerText((String) cell.getUserData()));
            label.setAccessibleText("Sort " + headerText((String) cell.getUserData()));
        }
    }

    private String headerText(String id) {
        return definitions.get(id).header()
                + sortKeys.stream()
                        .filter(k -> k.columnId().equals(id))
                        .map(k -> k.direction() == SortKey.Direction.ASCENDING ? " ↑" : " ↓")
                        .findFirst()
                        .orElse("");
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
        selectKeys(key == null ? List.of() : List.of(key));
    }

    /** Replaces selection, retaining off-page identities. The last key is the lead. */
    public void selectKeys(Collection<? extends K> keys) {
        checkOpen();
        var next = new LinkedHashSet<K>();
        keys.forEach(key -> next.add(Objects.requireNonNull(key)));
        if (selectionMode == RowSelectionMode.NONE) next.clear();
        if (selectionMode == RowSelectionMode.SINGLE && next.size() > 1)
            throw new IllegalArgumentException("Multiple selection is disabled");
        selectedKeys.setAll(next);
        selectedKey.set(next.isEmpty() ? null : next.getLast());
        anchor = selectedKey.get();
        markSelection();
    }

    public void deselect(K key) {
        checkOpen();
        var remaining = new ArrayList<>(selectedKeys);
        remaining.remove(key);
        selectKeys(remaining);
    }

    private List<K> visibleKeys() {
        var byRoot = new IdentityHashMap<Node, K>();
        entries.forEach(e -> byRoot.put(e.view().root(), e.row().key()));
        return body.getChildren().stream().filter(byRoot::containsKey).map(byRoot::get).toList();
    }

    private void choose(K key, boolean shortcut, boolean shift) {
        if (!rowsInteractive || selectionMode == RowSelectionMode.NONE) return;
        if (selectionMode == RowSelectionMode.SINGLE) { select(key); return; }
        K previousAnchor = anchor;
        var next = new LinkedHashSet<K>();
        if (shortcut) next.addAll(selectedKeys);
        var visible = visibleKeys();
        int start = visible.indexOf(anchor), end = visible.indexOf(key);
        if (shift && start >= 0 && end >= 0) {
            next.addAll(visible.subList(Math.min(start, end), Math.max(start, end) + 1));
            next.remove(key);
            next.add(key);
        } else if (!shortcut || !next.remove(key)) next.add(key);
        selectKeys(next);
        anchor = shift && previousAnchor != null ? previousAnchor : key;
    }

    private void keyPressed(K key, KeyEvent event) {
        // Embedded editors retain their own keyboard behavior.
        if (event.getTarget() != event.getSource() || !rowsInteractive
                || selectionMode == RowSelectionMode.NONE) return;
        var visible = visibleKeys();
        int index = visible.indexOf(key);
        if (event.getCode() == KeyCode.A && event.isShortcutDown()
                && selectionMode == RowSelectionMode.MULTIPLE) selectKeys(visible);
        else if (event.getCode() == KeyCode.ESCAPE) select(null);
        else if (event.getCode() == KeyCode.SPACE) choose(key, event.isShortcutDown(), event.isShiftDown());
        else {
            int next = switch (event.getCode()) {
                case UP -> Math.max(0, index - 1);
                case DOWN -> Math.min(visible.size() - 1, index + 1);
                case HOME -> 0;
                case END -> visible.size() - 1;
                default -> -1;
            };
            if (next < 0 || visible.isEmpty()) return;
            K target = visible.get(next);
            if (!event.isShortcutDown() || event.isShiftDown())
                choose(target, event.isShortcutDown(), event.isShiftDown());
            entries.stream().filter(e -> Objects.equals(e.row().key(), target))
                    .findFirst().ifPresent(e -> e.view().root().requestFocus());
        }
        event.consume();
    }

    /**
     * Stages fresh rows before replacing the visible page; failed construction preserves the old
     * page.
     */
    public void show(List<GridRow<K, T>> result) {
        show(result, order, widths, groupingSetup());
    }

    /** Stages both fresh rows and complete layout, publishing neither on preparation failure. */
    public void show(
            List<GridRow<K, T>> result,
            List<String> nextOrder,
            Map<String, ColumnWidth> nextWidths) {
        show(result, nextOrder, nextWidths, groupingSetup());
    }

    public void show(
            List<GridRow<K, T>> result,
            List<String> nextOrder,
            Map<String, ColumnWidth> nextWidths,
            GroupingSetup nextGrouping) {
        checkOpen();
        validateGrouping(nextGrouping);
        var nextCaptured = GroupProjection.capture(result, groupColumns);
        var nextTree = GroupProjection.project(nextCaptured, nextGrouping.columns());
        var nextExpansion = new GroupExpansion(defaultCollapsed, inactiveHistoryLimit);
        nextExpansion.current(GroupProjection.paths(nextTree));
        nextExpansion.restore(nextGrouping.choices());
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
                            if (e.getButton() == MouseButton.PRIMARY)
                                choose(row.key(), e.isShortcutDown(), e.isShiftDown());
                            if (e.getButton() == MouseButton.PRIMARY
                                    && (e.getTarget() == view.root() || e.getTarget() instanceof Label))
                                view.root().requestFocus();
                        };
                EventHandler<KeyEvent> key = e -> keyPressed(row.key(), e);
                next.add(new Entry<>(row, view, click, key));
                validateRow(view);
                view.root().getStyleClass().add("result-row");
                layoutRow(view, nextOrder, preparedWidths);
                view.root().addEventHandler(MouseEvent.MOUSE_CLICKED, click);
                view.root().setFocusTraversable(true);
                view.root().addEventHandler(KeyEvent.KEY_PRESSED, key);
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
        captured = nextCaptured;
        groupTree = nextTree;
        grouping = nextGrouping.columns();
        expansion = nextExpansion;
        disclosure.clear();
        drawGroups();
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
        resizeGroupHeadings();
        layoutCommitted.run();
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
        applyWidths(widths);
        layoutCommitted.run();
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

    double width(String id) {
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
            Label label = new Label(headerText(id));
            label.getStyleClass().add("column-header");
            HBox cell = new HBox(label);
            cell.getStyleClass().add("column-cell");
            cell.setUserData(id);
            HBox.setHgrow(label, javafx.scene.layout.Priority.ALWAYS);
            label.setMaxWidth(Double.MAX_VALUE);
            label.setFocusTraversable(true);
            label.setAccessibleRole(javafx.scene.AccessibleRole.BUTTON);
            label.setAccessibleText("Sort " + definitions.get(id).header());
            label.setOnKeyPressed(event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ENTER
                        || event.getCode() == javafx.scene.input.KeyCode.SPACE) {
                    sortRequest.accept(id);
                    event.consume();
                }
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
            Region handle = new Region();
            handle.getStyleClass().add("column-resize");
            handle.setMinWidth(9);
            handle.setPrefWidth(9);
            handle.setMaxWidth(9);
            handle.setCursor(javafx.scene.Cursor.E_RESIZE);
            cell.getChildren().add(handle);
            if (columnSeparators && !id.equals(ordering.getLast()))
                cell.getChildren().add(ResultColumnSeparators.forHeader(cell));
            gestures.header(cell, label, handle, id, () -> sortRequest.accept(id));
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
        ResultColumnSeparators separators = null;
        for (Node child : view.root().getChildren()) {
            if (child instanceof ResultColumnSeparators existing) separators = existing;
            else if (!fields.contains(child)) children.add(child);
        }
        if (columnSeparators) {
            if (separators == null)
                separators = new ResultColumnSeparators(view.root(), ordering.size());
            separators.position(ordering.stream().mapToDouble(id -> width(id, sizes)).toArray());
            children.add(separators);
        }
        view.root().setSpacing(0);
        if (!view.root().getChildren().equals(children)) {
            Node focus =
                    view.root().getScene() == null ? null : view.root().getScene().getFocusOwner();
            boolean restore = focus != null && descendant(view.root(), focus);
            view.root().getChildren().setAll(children);
            if (restore) focus.requestFocus();
        }
    }

    private void markSelection() {
        T loaded = null;
        List<T> actionable = new ArrayList<>();
        Set<K> keys = new HashSet<>(selectedKeys);
        for (var e : entries) {
            boolean selected = keys.contains(e.row().key());
            e.view()
                    .root()
                    .pseudoClassStateChanged(
                            javafx.css.PseudoClass.getPseudoClass("selected"), selected);
            e.view().root().getStyleClass().remove("selected");
            if (selected) {
                e.view().root().getStyleClass().add("selected");
                if (rowsInteractive && e.view().root().getParent() == body) {
                    actionable.add(e.row().model());
                    if (Objects.equals(selectedKey.get(), e.row().key())) loaded = e.row().model();
                }
            }
        }
        selectedElement.set(rowsInteractive ? loaded : null);
        selectedElements.setAll(actionable);
    }

    public void setOnLayoutCommitted(Runnable handler) {
        layoutCommitted = Objects.requireNonNull(handler);
    }

    public void setOnInteractionMessage(java.util.function.Consumer<String> handler) {
        interactionMessage = Objects.requireNonNull(handler);
    }

    void message(String text) {
        interactionMessage.accept(text);
    }

    List<ResultColumn> definitions() {
        return List.copyOf(definitions.values());
    }

    ResultHeaderGestures gestures() {
        return gestures;
    }

    boolean canGroup(String id) {
        return groupingEnabled && groupColumns.stream().anyMatch(c -> c.id().equals(id));
    }

    public List<String> groupingColumns() {
        return grouping;
    }

    public boolean groupingEnabled() {
        return groupingEnabled;
    }

    public GroupingSetup groupingSetup() {
        return new GroupingSetup(grouping, expansion.snapshot());
    }

    public void configureGrouping(
            List<GroupColumn<T>> columns,
            boolean enabled,
            List<String> defaults,
            boolean collapsed,
            int historyLimit) {
        checkOpen();
        if (!entries.isEmpty())
            throw new IllegalStateException("Configure grouping before first publication");
        if (historyLimit < 0
                || !definitions
                        .keySet()
                        .containsAll(columns.stream().map(GroupColumn::id).toList()))
            throw new IllegalArgumentException("Unknown group column or invalid history limit");
        var snapshot = GroupProjection.<K, T>capture(List.of(), columns);
        GroupProjection.project(snapshot, defaults);
        groupColumns = List.copyOf(columns);
        captured = snapshot;
        groupingEnabled = enabled;
        grouping = List.copyOf(defaults);
        defaultCollapsed = collapsed;
        inactiveHistoryLimit = historyLimit;
        expansion = new GroupExpansion(collapsed, historyLimit);
        drawGroups();
    }

    public void validateGrouping(GroupingSetup setup) {
        Set<String> ids =
                groupColumns.stream()
                        .map(GroupColumn::id)
                        .collect(java.util.stream.Collectors.toSet());
        if (!ids.containsAll(setup.columns()))
            throw new IllegalArgumentException("Column has no grouping mapping");
        for (var choice : setup.choices())
            for (var part : choice.path().segments()) {
                var column =
                        groupColumns.stream()
                                .filter(c -> c.id().equals(part.columnId()))
                                .findFirst()
                                .orElseThrow(() ->
                                        new IllegalArgumentException(
                                                "Unknown group path column; provide migration"));
                column.validate(part.value());
            }
    }

    public void setGroupingColumns(List<String> ids) {
        checkOpen();
        var setup = new GroupingSetup(ids, expansion.snapshot());
        validateGrouping(setup);
        var next = GroupProjection.project(captured, ids);
        grouping = setup.columns();
        groupTree = next;
        expansion.current(GroupProjection.paths(next));
        drawGroups();
        layoutCommitted.run();
    }

    /** Refresh mapping snapshots only after an explicit application data-update signal. */
    public void regroup() {
        checkOpen();
        var next = GroupProjection.capture(entries.stream().map(Entry::row).toList(), groupColumns);
        var tree = GroupProjection.project(next, grouping);
        captured = next;
        groupTree = tree;
        expansion.current(GroupProjection.paths(tree));
        drawGroups();
    }

    public void setGroupingEnabled(boolean enabled) {
        checkOpen();
        groupingEnabled = enabled;
        drawGroups();
    }

    public void resetExpansion() {
        checkOpen();
        expansion.reset();
        drawGroups();
        layoutCommitted.run();
    }

    public void setExpanded(GroupPath path, boolean expanded) {
        checkOpen();
        if (!GroupProjection.paths(groupTree).contains(path))
            throw new IllegalArgumentException("Group is not on current page");
        Node focus = body.getScene() == null ? null : body.getScene().getFocusOwner();
        boolean hidesFocus = !expanded && focus != null && descendantContainsFocus(path, focus);
        expansion.set(path, expanded);
        drawGroups();
        if (hidesFocus) disclosure.get(path).requestFocus();
        layoutCommitted.run();
    }

    private boolean descendantContainsFocus(GroupPath path, Node focus) {
        for (var row : entries) {
            if (!descendant(row.view().root(), focus)) continue;
            var values =
                    captured.rows().stream()
                            .filter(r -> Objects.equals(r.row().key(), row.row().key()))
                            .findFirst()
                            .orElseThrow()
                            .values();
            return path.segments().stream()
                    .allMatch(p -> Objects.equals(values.get(p.columnId()).key(), p.value()));
        }
        return disclosure.entrySet().stream()
                .anyMatch(e ->
                        descendant(e.getValue(), focus)
                                && e.getKey().segments().size() > path.segments().size()
                                && e.getKey()
                                        .segments()
                                        .subList(0, path.segments().size())
                                        .equals(path.segments()));
    }

    private static boolean descendant(Node ancestor, Node node) {
        for (Node current = node; current != null; current = current.getParent())
            if (current == ancestor) return true;
        return false;
    }

    private void drawGroups() {
        Node focus = body.getScene() == null ? null : body.getScene().getFocusOwner();
        boolean restoreFocus = focus != null && descendant(body, focus);
        GroupPath focusedPath = restoreFocus ? focusPath(focus) : null;
        disclosure.keySet().retainAll(new HashSet<>(GroupProjection.paths(groupTree)));
        List<Node> nodes = new ArrayList<>();
        Map<K, Node> rows = new HashMap<>();
        entries.forEach(e -> rows.put(e.row().key(), e.view().root()));
        if (!groupingEnabled || grouping.isEmpty())
            entries.forEach(e -> nodes.add(e.view().root()));
        else appendGroups(nodes, groupTree, rows);
        if (!body.getChildren().equals(nodes)) body.getChildren().setAll(nodes);
        if (restoreFocus) {
            if (descendant(body, focus)) focus.requestFocus();
            else {
                Button destination = null;
                if (focusedPath != null)
                    for (int level = 1; level <= focusedPath.segments().size(); level++) {
                        var candidate =
                                disclosure.get(
                                        new GroupPath(focusedPath.segments().subList(0, level)));
                        if (candidate != null && candidate.getParent() == body)
                            destination = candidate;
                        if (candidate != null
                                && !expansion.expanded(
                                        new GroupPath(focusedPath.segments().subList(0, level))))
                            break;
                    }
                if (destination != null) destination.requestFocus();
                else if (!header.getChildren().isEmpty())
                    ((HBox) header.getChildren().getFirst())
                            .getChildren()
                            .getFirst()
                            .requestFocus();
            }
        }
        resizeGroupHeadings();
        markSelection();
    }

    private GroupPath focusPath(Node focus) {
        for (var entry : entries)
            if (descendant(entry.view().root(), focus) && !grouping.isEmpty()) {
                var values =
                        captured.rows().stream()
                                .filter(r -> Objects.equals(r.row().key(), entry.row().key()))
                                .findFirst()
                                .orElseThrow()
                                .values();
                return new GroupPath(
                        grouping.stream()
                                .map(id -> new GroupPath.Segment(id, values.get(id).key()))
                                .toList());
            }
        return disclosure.entrySet().stream()
                .filter(e -> descendant(e.getValue(), focus))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private void appendGroups(
            List<Node> nodes, List<GroupProjection.Node<K, T>> groups, Map<K, Node> rows) {
        for (var group : groups) {
            Button button =
                    disclosure.computeIfAbsent(
                            group.path(),
                            path -> {
                                Button control =
                                        new Button() {
                                            @Override
                                            public Object queryAccessibleAttribute(
                                                    javafx.scene.AccessibleAttribute attribute,
                                                    Object... parameters) {
                                                if (attribute
                                                        == javafx.scene.AccessibleAttribute
                                                                .EXPANDED)
                                                    return expansion.expanded(path);
                                                if (attribute
                                                        == javafx.scene.AccessibleAttribute.LEAF)
                                                    return false;
                                                return super.queryAccessibleAttribute(
                                                        attribute, parameters);
                                            }
                                        };
                                control.setAccessibleRole(javafx.scene.AccessibleRole.TREE_ITEM);
                                control.getStyleClass().add("group-heading");
                                control.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                                control.setOnAction(
                                        e -> setExpanded(path, !expansion.expanded(path)));
                                control.setOnKeyPressed(e -> {
                                    if (e.getCode() == javafx.scene.input.KeyCode.LEFT) {
                                        setExpanded(path, false);
                                        e.consume();
                                    }
                                    if (e.getCode() == javafx.scene.input.KeyCode.RIGHT) {
                                        setExpanded(path, true);
                                        e.consume();
                                    }
                                });
                                return control;
                            });
            String id = group.path().segments().getLast().columnId();
            boolean open = expansion.expanded(group.path());
            button.setText(
                    (open ? "▾ " : "▸ ")
                            + definitions.get(id).header()
                            + ": "
                            + group.label()
                            + " — "
                            + group.count()
                            + " records");
            button.setAccessibleText(
                    definitions.get(id).header()
                            + ": "
                            + group.label()
                            + ", "
                            + group.count()
                            + " records, "
                            + (open ? "expanded" : "collapsed"));
            button.setPadding(
                    new javafx.geometry.Insets(
                            6, 8, 6, 8 + 24 * (group.path().segments().size() - 1)));
            button.notifyAccessibleAttributeChanged(javafx.scene.AccessibleAttribute.EXPANDED);
            nodes.add(button);
            if (open) {
                if (group.children().isEmpty())
                    group.rows().forEach(row -> nodes.add(rows.get(row.key())));
                else appendGroups(nodes, group.children(), rows);
            }
        }
    }

    private void resizeGroupHeadings() {
        double total = order.stream().mapToDouble(this::width).sum();
        disclosure.values().forEach(button -> size(button, total));
    }

    void previewWidth(String id, ColumnWidth value) {
        var next = new LinkedHashMap<>(widths);
        next.put(id, value);
        applyWidths(next);
    }

    private void applyWidths(Map<String, ColumnWidth> sizes) {
        for (Node node : header.getChildren())
            size((Region) node, width((String) node.getUserData(), sizes));
        entries.forEach(e -> layoutRow(e.view(), order, sizes));
        double total = order.stream().mapToDouble(id -> width(id, sizes)).sum();
        disclosure.values().forEach(button -> size(button, total));
    }

    private void release(List<Entry<K, T>> retired) {
        RuntimeException aggregate = null;
        for (var e : retired) {
            e.view().root().removeEventHandler(MouseEvent.MOUSE_CLICKED, e.click());
            e.view().root().removeEventHandler(KeyEvent.KEY_PRESSED, e.key());
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
        selectedKeys.clear();
        selectedElements.clear();
        anchor = null;
        captured = GroupProjection.capture(List.of(), List.of());
        groupTree = List.of();
        disclosure.clear();
        expansion.reset();
        gestures.close();
        scrollAlignment.close();
        release(old);
    }
}
