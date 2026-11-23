package de.neitzel.fx.view;

import de.neitzel.fx.grid.filter.*;
import de.neitzel.grid.*;

import javafx.application.Platform;
import javafx.beans.DefaultProperty;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/**
 * Local/external data grid coordinator and paging presentation. Construct and use on the FX thread.
 * No query runs until load/refresh or an explicit query change, allowing configuration before first
 * load. The application owns the source and executor. Closing releases only this grid's requests
 * and rows.
 */
@DefaultProperty("columns")
public final class DataGrid<K, T> extends VBox implements AutoCloseable {
    private final Table table = new Table();
    private TableResults<K, T> renderer;
    private GridCoordinator<K, T> coordinator;
    private final ReadOnlyObjectWrapper<GridCoordinator.State<K, T>> state =
            new ReadOnlyObjectWrapper<>();
    private final Label status = new Label("Not loaded");
    private final Button first = pagingButton("First page", "M0 0 H2 V14 H0 Z M4 7 L16 0 V14 Z"),
            previous = pagingButton("Previous page", "M2 7 L14 0 V14 Z"),
            next = pagingButton("Next page", "M2 0 L14 7 L2 14 Z"),
            last = pagingButton("Last page", "M0 0 L12 7 L0 14 Z M14 0 H16 V14 H14 Z"),
            retry = actionButton("Retry", "M2 8 A6 6 0 1 1 5 13 M2 3 V8 H7"),
            refresh = actionButton("Refresh", "M14 7 A6 6 0 1 0 13 11 M14 2 V7 H9");
    private final ComboBox<String> pageSize = new ComboBox<>();
    private GridQuery initial;
    private List<ResultColumn> columnDefinitions;
    private GridDefinition setupDefinition;
    private Map<Integer, java.util.function.UnaryOperator<GridSetup>> setupMigrations = Map.of();
    private final ReadOnlyObjectWrapper<List<String>> restorationAdjustments =
            new ReadOnlyObjectWrapper<>(List.of());
    private final Map<String, FilterColumn> filterColumns = new LinkedHashMap<>();
    private FilterEditors filterEditors = FilterEditors.builtIns();
    private FilterColumn.Checklist defaultChecklist = FilterColumn.Checklist.DISABLED;
    private FilterPopup.Options filterPopupOptions = FilterPopup.Options.defaults();
    private FilterPopup filterPopup;
    private final ReadOnlyObjectWrapper<RuntimeException> filterDisposalFailure =
            new ReadOnlyObjectWrapper<>();
    private boolean updating, closed;
    private long visibleSourceVersion;
    private Alert pendingAlert;
    private long alertGeneration;

    private final HBox groupingBar = new HBox(8);
    private final HBox filterSummary = new HBox(8);
    private final Label activeFilters = new Label();
    private final Label interactionStatus = new Label();
    private final MenuButton setupMenu = new MenuButton("Grid setup");
    private final MenuButton columnMenu = new MenuButton();
    private final ReadOnlyLongWrapper columnVisibilityRevision = new ReadOnlyLongWrapper();
    private final ReadOnlyLongWrapper committedRevision = new ReadOnlyLongWrapper();
    private List<String> defaultGrouping = List.of();
    private GridSetup developerDefaults;
    private boolean hasPublished, restoredPublication;
    private GridQuery lastPublishedQuery;
    private long restoreSerial, lastRestoredSerial;
    private GridPersistenceAttachment<K, T> attachment;
    private GridColumnSettings<K, T> columnSettings;

    private static Button actionButton(String description, String path) {
        var button = new Button();
        de.neitzel.fx.control.ActionIcon.install(button, description, path);
        return button;
    }

    private static Button pagingButton(String name, String path) {
        var button = new Button();
        var icon = new SVGPath();
        icon.setContent(path);
        icon.fillProperty().bind(button.textFillProperty());
        var graphic = new StackPane(icon);
        graphic.setMinSize(18, 16);
        graphic.setPrefSize(18, 16);
        graphic.setMaxSize(18, 16);
        graphic.setMouseTransparent(true);
        button.setGraphic(graphic);
        button.setMinSize(36, 30);
        button.setAccessibleText(name);
        button.setTooltip(new Tooltip(name));
        return button;
    }

    private final DataGridDeclaration<K, T> declaration = new DataGridDeclaration<>(this);
    private final ReadOnlyObjectWrapper<K> selectedKey = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<T> selectedElement = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyListWrapper<K> selectedKeys = new ReadOnlyListWrapper<>(
            FXCollections.emptyObservableList());
    private final ReadOnlyListWrapper<T> selectedElements = new ReadOnlyListWrapper<>(
            FXCollections.emptyObservableList());
    private final ObjectProperty<RowSelectionMode> selectionMode =
            new SimpleObjectProperty<>(this, "selectionMode", RowSelectionMode.SINGLE) {
                @Override public void set(RowSelectionMode value) {
                    requireFx();
                    if (closed) throw new IllegalStateException("Grid is closed");
                    super.set(Objects.requireNonNull(value));
                }
            };

    /** Creates a query-free FXML control. Columns are visible before a source is attached. */
    public DataGrid() {
        requireFx();
        getStyleClass().add("data-grid");
        selectionMode.addListener((property, old, mode) -> {
            if (renderer != null) renderer.setSelectionMode(mode);
        });
        setSpacing(8);
        getChildren().addAll(declaration.preview(), status);
    }

    public ObservableList<DataGridColumn> getColumns() {
        return declaration.columns();
    }

    public ObjectProperty<ObservableList<T>> itemsProperty() {
        return declaration.itemsProperty();
    }

    public ObservableList<T> getItems() {
        return itemsProperty().get();
    }

    public void setItems(ObservableList<T> items) {
        requireFx();
        if (closed) throw new IllegalStateException("Grid is closed");
        if (getSource() != null) throw new IllegalStateException("Choose items or source");
        itemsProperty().set(items);
    }

    public GridSource<K, T> getSource() {
        return declaration.sourceProperty().get();
    }

    public ObjectProperty<GridSource<K, T>> sourceProperty() {
        return declaration.sourceProperty();
    }

    public Function<T, ResultRow> getRowFactory() {
        return declaration.rowFactory;
    }

    public void setRowFactory(Function<T, ResultRow> factory) {
        declaration.beforeOpen();
        declaration.rowFactory = factory;
    }

    public Function<T, K> getKeyFactory() {
        return declaration.keyFactory;
    }

    public void setKeyFactory(Function<T, K> factory) {
        declaration.beforeOpen();
        declaration.keyFactory = factory;
    }

    public Comparator<K> getKeyComparator() {
        return declaration.keyComparator;
    }

    public void setKeyComparator(Comparator<K> comparator) {
        declaration.beforeOpen();
        declaration.keyComparator = comparator;
    }

    public String getKeyAttribute() {
        return declaration.keyAttribute;
    }

    public void setKeyAttribute(String attribute) {
        declaration.beforeOpen();
        declaration.keyAttribute = attribute;
    }

    public String getSetupId() {
        return declaration.setupId;
    }

    public void setSetupId(String id) {
        declaration.beforeOpen();
        declaration.setupId = id;
    }

    public int getSetupVersion() {
        return declaration.setupVersion;
    }

    public void setSetupVersion(int version) {
        declaration.beforeOpen();
        declaration.setupVersion = version;
    }

    public int getMaximumPageSize() {
        return declaration.maximumPageSize;
    }

    public void setMaximumPageSize(int size) {
        declaration.beforeOpen();
        if (size < 1) throw new IllegalArgumentException("Positive maximum page size required");
        declaration.maximumPageSize = size;
    }

    public String getGroupBy() {
        return declaration.groupBy;
    }

    public void setGroupBy(String columns) {
        declaration.beforeOpen();
        declaration.groupBy = columns;
    }

    public boolean isGroupsCollapsed() {
        return declaration.groupsCollapsed;
    }

    public void setGroupsCollapsed(boolean collapsed) {
        declaration.beforeOpen();
        declaration.groupsCollapsed = collapsed;
    }

    public int getInactiveGroupHistoryLimit() {
        return declaration.inactiveGroupHistoryLimit;
    }

    public void setInactiveGroupHistoryLimit(int limit) {
        declaration.beforeOpen();
        if (limit < 0) throw new IllegalArgumentException("Negative group history limit");
        declaration.inactiveGroupHistoryLimit = limit;
    }

    /** Completes declarative configuration without executing a query. */
    public void initialize() {
        requireFx();
        if (closed) throw new IllegalStateException("Grid is closed");
        if (coordinator == null) {
            try {
                declaration.open();
            } catch (RuntimeException failure) {
                try {
                    close();
                } catch (RuntimeException cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        }
    }

    public DataGrid(
            List<ResultColumn> columns,
            Function<T, ResultRow> rowFactory,
            GridSource<K, T> source) {
        this();
        declaration.setInitialSource(source);
        initialize(columns, rowFactory, source);
    }

    void initialize(
            List<ResultColumn> columns,
            Function<T, ResultRow> rowFactory,
            GridSource<K, T> source) {
        requireFx();
        getChildren().clear();
        columnDefinitions = List.copyOf(columns);
        Objects.requireNonNull(source);
        initial =
                new GridQuery(
                        List.of(),
                        List.of(),
                        new PageRequest.Paged(
                                0, Math.min(50, source.capabilities().maximumPageSize())));
        renderer = table.openResults(columns, rowFactory, columnMenu);
        columnMenu.getStyleClass().add("data-grid-column-menu");
        columnMenu.setAccessibleText("Column visibility");
        columnMenu.setTooltip(new Tooltip("Column visibility"));
        columnMenu.setOnShowing(event -> updateColumnMenu());
        renderer.setHeaderMenuFactory(this::headerMenu);
        renderer.setSelectionMode(getSelectionMode());
        selectedKeys.bind(renderer.selectedKeysProperty());
        selectedElements.bind(renderer.selectedElementsProperty());
        renderer.enableColumnSeparators();
        selectedKey.bind(renderer.selectedKeyProperty());
        selectedElement.bind(renderer.selectedElementProperty());
        coordinator =
                new GridCoordinator<>(
                        source, Platform::runLater, this::changed, result -> publishRows(result));
        coordinator.setConfirmation(this::confirmAll);
        renderer.setOnSortRequest(id -> {
            var q = query();
            SortKey.Direction direction =
                    q.sort().size() == 1
                                    && q.sort().getFirst().columnId().equals(id)
                                    && q.sort().getFirst().direction()
                                            == SortKey.Direction.ASCENDING
                            ? SortKey.Direction.DESCENDING
                            : SortKey.Direction.ASCENDING;
            setSort(new SortKey(id, direction));
        });
        first.setOnAction(e -> first());
        previous.setOnAction(e -> previous());
        next.setOnAction(e -> next());
        last.setOnAction(e -> last());
        retry.setOnAction(e -> coordinator.retry());
        refresh.setOnAction(e -> refresh());
        pageSize.setOnAction(e -> {
            if (updating || pageSize.getValue() == null) return;
            setPageRequest(
                    pageSize.getValue().equals("All")
                            ? new PageRequest.All()
                            : new PageRequest.Paged(0, Integer.parseInt(pageSize.getValue())));
        });
        status.setWrapText(true);
        status.getStyleClass().add("data-grid-status");
        HBox controls =
                new HBox(
                        8,
                        first,
                        previous,
                        next,
                        last,
                        new Label("Rows"),
                        pageSize,
                        refresh,
                        retry);
        controls.getStyleClass().add("data-grid-paging");
        renderer.setOnLayoutCommitted(this::layoutCommitted);
        renderer.setOnInteractionMessage(interactionStatus::setText);
        groupingBar.getStyleClass().add("data-grid-grouping");
        groupingBar.setAccessibleText("Grouping columns");
        groupingBar.setFocusTraversable(true);
        groupingBar.setVisible(false);
        groupingBar.setManaged(false);
        renderer.gestures().groupTarget(groupingBar);
        MenuItem columnsItem = new MenuItem("Column settings…");
        columnsItem.setOnAction(e -> {
            if (columnSettings != null) columnSettings.close();
            columnSettings = new GridColumnSettings<>(this, renderer);
            columnSettings.show();
        });
        MenuItem resetExpansion = new MenuItem("Reset expansion");
        resetExpansion.setOnAction(e -> resetExpansion());
        MenuItem clearFilters = new MenuItem("Clear filters");
        clearFilters.setOnAction(e -> setFilters(List.of()));
        setupMenu.getItems().addAll(columnsItem, resetExpansion, clearFilters);
        controls.getChildren().add(setupMenu);
        interactionStatus.setWrapText(true);
        interactionStatus.getStyleClass().add("data-grid-interaction-status");
        de.neitzel.fx.control.ActionIcon.install(setupMenu, "Grid setup",
                "M2 4 H14 M2 8 H14 M2 12 H14 M5 2 V6 M11 6 V10 M7 10 V14");
        var css = DataGrid.class.getResource("data-grid.css");
        if (css != null) getStylesheets().add(css.toExternalForm());
        Button clearActiveFilters = actionButton("Clear filters",
                "M1 2 H15 L9 8 V14 L6 12 V8 Z M11 10 L15 14 M15 10 L11 14");
        clearActiveFilters.setOnAction(e -> setFilters(List.of()));
        filterSummary.getChildren().addAll(activeFilters, clearActiveFilters);
        filterSummary.setVisible(false);
        filterSummary.setManaged(false);
        getChildren()
                .addAll(groupingBar, table, filterSummary, controls, status, interactionStatus);
        VBox.setVgrow(table, Priority.ALWAYS);
        state.set(coordinator.state());
        updateControls(coordinator.state());
    }

    public ReadOnlyObjectProperty<GridCoordinator.State<K, T>> stateProperty() {
        return state.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<K> selectedKeyProperty() {
        return selectedKey.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<T> selectedElementProperty() {
        return selectedElement.getReadOnlyProperty();
    }

    public ObjectProperty<RowSelectionMode> selectionModeProperty() { return selectionMode; }

    public RowSelectionMode getSelectionMode() { return selectionMode.get(); }

    public void setSelectionMode(RowSelectionMode mode) { selectionMode.set(mode); }

    public ReadOnlyListProperty<K> selectedKeysProperty() { return selectedKeys.getReadOnlyProperty(); }

    public ReadOnlyListProperty<T> selectedElementsProperty() { return selectedElements.getReadOnlyProperty(); }

    public void selectKeys(Collection<? extends K> keys) {
        check();
        renderer.selectKeys(keys);
    }

    public void select(K key) {
        check();
        renderer.select(key);
    }

    public void confirmedDeleted(K key) {
        check();
        renderer.deselect(key);
        refresh();
    }

    public void setEditGuard(Function<GridQuery, CompletionStage<Boolean>> guard) {
        check();
        coordinator.setGuard(guard);
    }

    public void setAllConfirmation(BiFunction<GridQuery, Long, CompletionStage<Boolean>> handler) {
        check();
        coordinator.setConfirmation(handler);
    }

    public void setAllWarningThreshold(long count) {
        check();
        coordinator.setAllWarningThreshold(count);
    }

    public void setColumnOrder(List<String> ids) {
        check();
        closeFilter();
        coordinator.cancelRestore();
        renderer.setColumnOrder(ids);
    }

    /** Returns whether this grid currently displays the column. */
    public boolean isColumnVisible(String id) {
        check();
        return renderer.isColumnVisible(id);
    }

    /** Shows or hides a column without fetching rows or changing the saved grid setup. */
    public void setColumnVisible(String id, boolean visible) {
        if (visible == isColumnVisible(id)) return;
        closeFilter();
        renderer.setColumnVisible(id, visible);
        columnVisibilityRevision.set(columnVisibilityRevision.get() + 1);
    }

    /** Observes effective visibility changes, which are independent of saved grid setups. */
    public ReadOnlyLongProperty columnVisibilityRevisionProperty() {
        return columnVisibilityRevision.getReadOnlyProperty();
    }

    private void updateColumnMenu() {
        columnMenu.getItems().setAll(renderer.columnOrder().stream().map(id -> {
            CheckMenuItem item = new CheckMenuItem(renderer.columnHeader(id));
            item.setSelected(isColumnVisible(id));
            item.setOnAction(e -> setColumnVisible(id, item.isSelected()));
            return item;
        }).toList());
    }

    private ContextMenu headerMenu(String id, HBox cell) {
        var menu = new ContextMenu(
                menuItem("Sort ascending", () -> setSort(new SortKey(id, SortKey.Direction.ASCENDING))),
                menuItem("Sort descending", () -> setSort(new SortKey(id, SortKey.Direction.DESCENDING))));
        if (query().sort().stream().anyMatch(key -> key.columnId().equals(id)))
            menu.getItems().add(menuItem("Clear sort", () -> setSort(null)));
        if (filterColumns.containsKey(id)) {
            var filterButton = cell.lookup(".filter-button");
            menu.getItems().add(menuItem("Filter…", () -> openFilter(id, filterButton)));
        }
        if (renderer.canGroup(id)) {
            boolean grouped = renderer.groupingColumns().contains(id);
            menu.getItems().add(menuItem(grouped ? "Remove grouping" : "Group by column", () -> {
                var nextGroups = new ArrayList<>(renderer.groupingColumns());
                if (grouped) nextGroups.remove(id);
                else if (!nextGroups.contains(id)) nextGroups.add(id);
                setGroupingColumns(nextGroups);
            }));
        }
        menu.getItems().addAll(
                menuItem("Reset column width", () -> setColumnWidth(id, new ColumnWidth.Default())),
                new SeparatorMenuItem(),
                menuItem("Hide column", () -> setColumnVisible(id, false)));
        return menu;
    }

    private static MenuItem menuItem(String text, Runnable action) {
        var item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
    }

    public double setColumnWidth(String id, ColumnWidth width) {
        check();
        closeFilter();
        coordinator.cancelRestore();
        return renderer.setColumnWidth(id, width);
    }

    public ReadOnlyObjectProperty<RuntimeException> disposalFailureProperty() {
        return renderer.disposalFailureProperty();
    }

    public void load() {
        check();
        coordinator.request(query());
    }

    public void refresh() {
        load();
    }

    public void retry() {
        check();
        coordinator.retry();
    }

    public void setSource(GridSource<K, T> source) {
        requireFx();
        if (closed) throw new IllegalStateException("Grid is closed");
        if (getItems() != null) throw new IllegalStateException("Choose items or source");
        Objects.requireNonNull(source);
        if (coordinator != null && source == getSource()) replaceSource(source);
        else sourceProperty().set(source);
    }

    void replaceSource(GridSource<K, T> source) {
        check();
        closeFilter();
        var q = query();
        coordinator.request(source, new GridQuery(q.filters(), q.sort(), firstPage(q.page())));
    }

    public void setFilters(List<FilterCondition> filters) {
        check();
        coordinator.validateFilters(filters);
        var q = query();
        coordinator.request(new GridQuery(filters, q.sort(), firstPage(q.page())));
    }

    /**
     * Definition type IDs are stable schema identifiers, not display labels or Java class names.
     */
    public void configureSetups(
            String id,
            int version,
            Map<String, String> types,
            Map<Integer, java.util.function.UnaryOperator<GridSetup>> migrations) {
        check();
        if (!types.keySet().equals(new HashSet<>(renderer.columnOrder())))
            throw new IllegalArgumentException("Declare a type for every column");
        GridDefinition definition =
                new GridDefinition(
                        id,
                        version,
                        columnDefinitions.stream()
                                .map(c ->
                                        new GridDefinition.Column(
                                                c.id(),
                                                types.get(c.id()),
                                                c.defaultWidth(),
                                                c.minimumWidth(),
                                                c.maximumWidth(),
                                                c.fixedPosition()))
                                .toList());
        Map<Integer, java.util.function.UnaryOperator<GridSetup>> next = Map.copyOf(migrations);
        if (coordinator.isRestoring())
            throw new IllegalStateException("Cannot change definition during restoration");
        setupDefinition = definition;
        setupMigrations = next;
    }

    /** Snapshots committed settings only; never accepts or cancels an open draft. */
    public GridSetup snapshotSetup() {
        check();
        if (setupDefinition == null)
            throw new IllegalStateException("Configure the grid definition before saving");
        var committed =
                coordinator.state().displayed() == null ? initial : coordinator.state().displayed();
        Map<String, String> types = new HashMap<>();
        setupDefinition.columns().forEach(c -> types.put(c.id(), c.type()));
        return new GridSetup(
                2,
                setupDefinition.id(),
                setupDefinition.version(),
                renderer.columnOrder().stream()
                        .map(id ->
                                new GridSetup.ColumnState(
                                        id, types.get(id), renderer.columnWidths().get(id)))
                        .toList(),
                committed.sort(),
                committed.filters(),
                firstPage(committed.page()),
                renderer.groupingSetup());
    }

    public ReadOnlyObjectProperty<List<String>> restorationAdjustmentsProperty() {
        return restorationAdjustments.getReadOnlyProperty();
    }

    /** Decode through a supplier so parse errors receive the same visible, query-free rollback. */
    public void restoreSetup(java.util.function.Supplier<GridSetup> decode) {
        check();
        try {
            restoreSetup(Objects.requireNonNull(decode.get()));
        } catch (RuntimeException error) {
            coordinator.rejectSetup(error);
        }
    }

    public void restoreSetup(GridSetup saved) {
        check();
        try {
            if (setupDefinition == null)
                throw new IllegalStateException("Configure the grid definition before restoring");
            var prepared =
                    setupDefinition.prepare(
                            saved,
                            setupMigrations,
                            coordinator.capabilities(),
                            condition -> coordinator.validateFilters(List.of(condition)));
            var setup = prepared.setup();
            var grouping =
                    setup.grouping() == null
                            ? new GroupingSetup(defaultGrouping, List.of())
                            : setup.grouping();
            renderer.validateGrouping(grouping);
            long serial = restoreSerial;
            Map<String, ColumnWidth> widths = new LinkedHashMap<>();
            setup.columns().forEach(c -> widths.put(c.id(), c.width()));
            coordinator.restoreSetup(
                    new GridQuery(setup.filters(), setup.sort(), setup.page()),
                    this::closeFilter,
                    result -> {
                        renderer.show(
                                result.rows(),
                                setup.columns().stream().map(GridSetup.ColumnState::id).toList(),
                                widths,
                                grouping);
                        lastRestoredSerial = serial;
                        restoredPublication = true;
                        drawGroupingBar();
                        restorationAdjustments.set(prepared.adjustments());
                    });
        } catch (RuntimeException error) {
            coordinator.rejectSetup(error);
        }
    }

    public void setFilterEditors(FilterEditors editors) {
        check();
        Objects.requireNonNull(editors);
        filterColumns.values().forEach(c -> editors.require(c.editorId()));
        closeFilter();
        filterEditors = editors;
    }

    public void setDefaultChecklist(FilterColumn.Checklist mode) {
        check();
        Objects.requireNonNull(mode);
        if (mode == FilterColumn.Checklist.INHERIT)
            throw new IllegalArgumentException("Grid default cannot inherit");
        for (var entry : filterColumns.values())
            if (entry.checklist() == FilterColumn.Checklist.INHERIT
                    && mode != FilterColumn.Checklist.DISABLED
                    && entry.candidates() == null)
                throw new IllegalArgumentException(
                        "Each enabled column needs a candidate provider");
        closeFilter();
        defaultChecklist = mode;
    }

    public void setFilterPopupOptions(FilterPopup.Options options) {
        check();
        closeFilter();
        filterPopupOptions = Objects.requireNonNull(options);
    }

    public void configureFilter(String column, FilterColumn configuration) {
        check();
        Objects.requireNonNull(configuration);
        if (!renderer.columnOrder().contains(column))
            throw new IllegalArgumentException("Unknown filter column: " + column);
        filterEditors.require(configuration.editorId());
        if (configuration.checklist() == FilterColumn.Checklist.INHERIT
                && defaultChecklist != FilterColumn.Checklist.DISABLED
                && configuration.candidates() == null)
            throw new IllegalArgumentException("Enabled checklist requires a provider");
        closeFilter();
        filterColumns.put(column, configuration);
        renderer.setFilterActions(filterColumns.keySet(), this::openFilter);
    }

    private void openFilter(String column, javafx.scene.Node opener) {
        check();
        if (filterPopup != null && filterPopup.isOpen()) {
            filterPopup.outside();
            return;
        }
        var config = filterColumns.get(column);
        var registration = filterEditors.require(config.editorId());
        var current = query().filters();
        var committed =
                current.stream().filter(f -> f.columnId().equals(column)).findFirst().orElse(null);
        var others = current.stream().filter(f -> !f.columnId().equals(column)).toList();
        FilterEditor editor = null;
        try {
            editor =
                    registration
                            .factory()
                            .apply(
                                    new FilterEditorContext(
                                            column,
                                            config,
                                            committed,
                                            others,
                                            config.checklist() == FilterColumn.Checklist.INHERIT
                                                    ? defaultChecklist
                                                    : config.checklist()));
            filterPopup =
                    new FilterPopup(
                            opener,
                            editor,
                            filterPopupOptions,
                            condition -> {
                                registration.validate(condition);
                                if (condition != null) {
                                    registration.requireEvaluation(coordinator.filterExecutionId());
                                    if (!condition.columnId().equals(column))
                                        throw new IllegalArgumentException(
                                                "Editor returned another column");
                                    coordinator.validateFilters(List.of(condition));
                                }
                            },
                            condition -> {
                                // Preserve current other-column state, even if it changed while
                                // this editor was open.
                                List<FilterCondition> nextFilters =
                                        new ArrayList<>(
                                                query().filters().stream()
                                                        .filter(f -> !f.columnId().equals(column))
                                                        .toList());
                                if (condition != null) nextFilters.add(condition);
                                setFilters(nextFilters);
                            });
            filterPopup
                    .disposalFailureProperty()
                    .addListener((o, old, failure) -> filterDisposalFailure.set(failure));
            filterPopup.show();
        } catch (RuntimeException error) {
            if (editor != null) editor.close();
            status.setText("Cannot open filter: " + error.getMessage());
        }
    }

    public ReadOnlyObjectProperty<RuntimeException> filterDisposalFailureProperty() {
        return filterDisposalFailure.getReadOnlyProperty();
    }

    private void closeFilter() {
        if (filterPopup != null) {
            filterPopup.close();
            filterPopup = null;
        }
    }

    public void setSort(SortKey sort) {
        check();
        var q = query();
        coordinator.request(
                new GridQuery(
                        q.filters(),
                        sort == null ? List.of() : List.of(sort),
                        firstPage(q.page())));
    }

    public int getPageSize() {
        return coordinator == null
                ? declaration.pageSize
                : query().page() instanceof PageRequest.Paged page ? page.pageSize() : 0;
    }

    public void setPageSize(int size) {
        requireFx();
        if (closed) throw new IllegalStateException("Grid is closed");
        if (size < 1) throw new IllegalArgumentException("Positive page size required");
        if (coordinator == null) {
            declaration.pageSize = size;
            return;
        }
        setPageRequest(new PageRequest.Paged(0, size));
    }

    void setInitialPageSize(int size) {
        coordinator.capabilities().validate(new PageRequest.Paged(0, size));
        initial = new GridQuery(initial.filters(), initial.sort(), new PageRequest.Paged(0, size));
        updateControls(coordinator.state());
    }

    public void showAll() {
        setPageRequest(new PageRequest.All());
    }

    public void setPageRequest(PageRequest page) {
        check();
        var q = query();
        coordinator.request(new GridQuery(q.filters(), q.sort(), page));
    }

    public void first() {
        check();
        if (query().page() instanceof PageRequest.Paged p)
            setPageRequest(new PageRequest.Paged(0, p.pageSize()));
    }

    public void previous() {
        check();
        if (query().page() instanceof PageRequest.Paged p && p.pageIndex() > 0)
            setPageRequest(new PageRequest.Paged(p.pageIndex() - 1, p.pageSize()));
    }

    public void next() {
        check();
        if (query().page() instanceof PageRequest.Paged p) {
            var s = coordinator.state();
            if (s.phase() == GridCoordinator.Phase.READY && !s.result().hasNext()) return;
            setPageRequest(new PageRequest.Paged(Math.incrementExact(p.pageIndex()), p.pageSize()));
        }
    }

    public void last() {
        check();
        var s = coordinator.state();
        if (s.phase() == GridCoordinator.Phase.READY
                && s.result().total().isPresent()
                && query().page() instanceof PageRequest.Paged p) {
            long count = s.result().total().getAsLong();
            if (count > 0)
                setPageRequest(new PageRequest.Paged((count - 1) / p.pageSize(), p.pageSize()));
        }
    }

    private GridQuery query() {
        if (coordinator.isRestoring())
            return coordinator.state().displayed() == null
                    ? initial
                    : coordinator.state().displayed();
        return coordinator.state().requested() == null ? initial : coordinator.state().requested();
    }

    private static PageRequest firstPage(PageRequest page) {
        return page instanceof PageRequest.Paged p ? new PageRequest.Paged(0, p.pageSize()) : page;
    }

    private void changed(GridCoordinator.State<K, T> value) {
        if (pendingAlert != null && value.generation() != alertGeneration) {
            pendingAlert.close();
            pendingAlert = null;
        }
        if (!closed) {
            pseudoClassStateChanged(
                    javafx.css.PseudoClass.getPseudoClass("loading"),
                    value.phase() == GridCoordinator.Phase.LOADING);
            pseudoClassStateChanged(
                    javafx.css.PseudoClass.getPseudoClass("error"),
                    value.phase() == GridCoordinator.Phase.ERROR);
            if (value.displayed() != null) {
                renderer.setFilteredColumns(
                        value.displayed().filters().stream()
                                .map(FilterCondition::columnId)
                                .collect(java.util.stream.Collectors.toSet()));
                renderer.setSortKeys(value.displayed().sort());
            }
            if (visibleSourceVersion != value.sourceVersion()) {
                visibleSourceVersion = value.sourceVersion();
                renderer.select(null);
            }
            renderer.setRowsInteractive(value.phase() == GridCoordinator.Phase.READY);
            updateControls(value);
        }
        state.set(value);
        if (!closed && value.phase() == GridCoordinator.Phase.READY) {
            GridQuery published =
                    new GridQuery(
                            value.displayed().filters(),
                            value.displayed().sort(),
                            firstPage(value.displayed().page()));
            boolean changedQuery = hasPublished && !Objects.equals(published, lastPublishedQuery);
            hasPublished = true;
            lastPublishedQuery = published;
            if (changedQuery && !restoredPublication) fireCommitted();
            restoredPublication = false;
        }
    }

    private void updateControls(GridCoordinator.State<K, T> s) {
        GridQuery requested = s.requested() == null ? initial : s.requested();
        boolean ready = s.phase() == GridCoordinator.Phase.READY;
        boolean empty = ready && s.result().rows().isEmpty();
        long index = requested.page() instanceof PageRequest.Paged p ? p.pageIndex() : 0;
        boolean paged = requested.page() instanceof PageRequest.Paged;
        first.setDisable(!paged || index == 0 || empty);
        previous.setDisable(first.isDisabled());
        next.setDisable(!paged || empty || (ready && !s.result().hasNext()));
        last.setDisable(
                !paged || empty || !ready || s.result().total().isEmpty() || !s.result().hasNext());
        retry.setDisable(s.phase() != GridCoordinator.Phase.ERROR);
        updating = true;
        try {
            int max = coordinator.capabilities().maximumPageSize();
            List<String> sizes = new ArrayList<>();
            for (int n : new int[] {25, 50, 100, 250}) if (n <= max) sizes.add(Integer.toString(n));
            if (requested.page() instanceof PageRequest.Paged p
                    && !sizes.contains(Integer.toString(p.pageSize())))
                sizes.add(Integer.toString(p.pageSize()));
            if (coordinator.capabilities().all()) sizes.add("All");
            pageSize.getItems().setAll(sizes);
            pageSize.setValue(
                    requested.page() instanceof PageRequest.All
                            ? "All"
                            : Integer.toString(((PageRequest.Paged) requested.page()).pageSize()));
        } finally {
            updating = false;
        }
        String shown =
                s.result() == null
                        ? ""
                        : " Displayed "
                                + pageLabel(s.displayed().page())
                                + " ("
                                + s.result().rows().size()
                                + " rows).";
        String message =
                switch (s.phase()) {
                    case IDLE -> "Not loaded";
                    case GUARD ->
                            "Waiting for edit approval. Requested "
                                    + pageLabel(requested.page())
                                    + "."
                                    + shown;
                    case LOADING ->
                            "Loading "
                                    + pageLabel(requested.page())
                                    + ". Previous rows are outdated."
                                    + shown;
                    case CONFIRMATION ->
                            "Waiting for permission to load all matching rows." + shown;
                    case ERROR ->
                            "Query failed: "
                                    + String.valueOf(s.error().getMessage())
                                    + ". Retry is available; previous rows are outdated."
                                    + shown;
                    case CLOSED -> "Closed";
                    case READY -> {
                        if (empty) yield "No results";
                        if (s.result().total().isPresent()) {
                            long total = s.result().total().getAsLong();
                            yield requested.page() instanceof PageRequest.Paged p
                                    ? "Page "
                                            + (p.pageIndex() + 1)
                                            + " of "
                                            + (total == 0 ? 0 : 1 + (total - 1) / p.pageSize())
                                            + " — "
                                            + total
                                            + " matching rows"
                                    : "All " + total + " matching rows";
                        }
                        yield pageLabel(requested.page()) + " — total unknown";
                    }
                };
        List<String> filtered =
                s.displayed() == null
                        ? List.of()
                        : s.displayed().filters().stream()
                                .map(f ->
                                        columnDefinitions.stream()
                                                .filter(c -> c.id().equals(f.columnId()))
                                                .findFirst()
                                                .orElseThrow()
                                                .header())
                                .toList();
        activeFilters.setText("Active filters: " + String.join(", ", filtered));
        filterSummary.setVisible(!filtered.isEmpty());
        filterSummary.setManaged(!filtered.isEmpty());
        status.setText(
                message
                        + (renderer.groupingEnabled() && !renderer.groupingColumns().isEmpty()
                                ? (requested.page() instanceof PageRequest.All
                                        ? " · Grouping loaded result"
                                        : " · Grouping current page")
                                : ""));
    }

    private static String pageLabel(PageRequest page) {
        return page instanceof PageRequest.Paged p ? "page " + (p.pageIndex() + 1) : "all rows";
    }

    private CompletionStage<Boolean> confirmAll(GridQuery query, long count) {
        CompletableFuture<Boolean> answer = new CompletableFuture<>();
        ButtonType proceed = new ButtonType("Continue", ButtonBar.ButtonData.OK_DONE);
        Alert alert =
                new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Load all "
                                + count
                                + " matching rows? This may use substantial memory and reduce"
                                + " responsiveness.",
                        proceed,
                        ButtonType.CANCEL);
        alert.setTitle("Load all rows");
        alert.setHeaderText("Paging will be disabled");
        if (getScene() != null && getScene().getWindow() != null)
            alert.initOwner(getScene().getWindow());
        pendingAlert = alert;
        alertGeneration = coordinator.state().generation();
        alert.setOnHidden(e -> {
            if (pendingAlert == alert) pendingAlert = null;
            answer.complete(alert.getResult() == proceed);
        });
        alert.show();
        return answer;
    }

    public CompletionStage<Void> loadAsync() {
        return awaitOperation(this::load, -1);
    }

    public CompletionStage<Void> restoreSetupAsync(GridSetup setup) {
        long serial = ++restoreSerial;
        return awaitOperation(() -> restoreSetup(setup), serial);
    }

    private CompletionStage<Void> awaitOperation(Runnable action, long serial) {
        check();
        action.run();
        long generation = coordinator.state().generation();
        CompletableFuture<Void> future = new CompletableFuture<>();
        javafx.beans.value.ChangeListener<GridCoordinator.State<K, T>> listener =
                (observable, before, value) -> {
                    if (value.generation() != generation
                            || value.phase() == GridCoordinator.Phase.CLOSED)
                        future.completeExceptionally(
                                new CancellationException("Grid operation superseded"));
                    else if (value.phase() == GridCoordinator.Phase.ERROR)
                        future.completeExceptionally(value.error());
                    else if (value.phase() == GridCoordinator.Phase.READY) {
                        if (serial < 0 || lastRestoredSerial == serial) future.complete(null);
                        else
                            future.completeExceptionally(
                                    new CancellationException("Restore declined"));
                    }
                };
        state.addListener(listener);
        future.whenComplete((ok, error) -> state.removeListener(listener));
        listener.changed(state, state.get(), state.get());
        return future;
    }

    GridSetup developerDefaults() {
        return developerDefaults == null ? snapshotSetup() : developerDefaults;
    }

    void cancelOwnedRestore(long generation) {
        if (!closed && state.get().generation() == generation) coordinator.cancelRestore();
    }

    public ReadOnlyLongProperty committedRevisionProperty() {
        return committedRevision.getReadOnlyProperty();
    }

    public List<String> groupingColumns() {
        return renderer.groupingColumns();
    }

    public boolean groupingEnabled() {
        return renderer.groupingEnabled();
    }

    public void configureGrouping(
            List<GroupColumn<T>> columns,
            boolean enabled,
            List<String> defaults,
            boolean collapsed,
            int inactiveHistoryLimit) {
        check();
        renderer.configureGrouping(columns, enabled, defaults, collapsed, inactiveHistoryLimit);
        defaultGrouping = List.copyOf(defaults);
        developerDefaults = null;
        drawGroupingBar();
    }

    public void setGroupingColumns(List<String> columns) {
        check();
        closeFilter();
        coordinator.cancelRestore();
        try {
            renderer.setGroupingColumns(columns);
        } catch (RuntimeException failure) {
            interactionStatus.setText("Cannot change grouping: " + failure.getMessage());
            throw failure;
        }
    }

    public boolean isGroupingEnabled() {
        return coordinator == null ? declaration.groupingEnabled : groupingEnabled();
    }

    public void setGroupingEnabled(boolean enabled) {
        requireFx();
        if (coordinator == null) {
            declaration.groupingEnabled = enabled;
            return;
        }
        check();
        coordinator.cancelRestore();
        renderer.setGroupingEnabled(enabled);
        drawGroupingBar();
        updateControls(coordinator.state());
    }

    public void resetExpansion() {
        check();
        coordinator.cancelRestore();
        renderer.resetExpansion();
    }

    public void setExpanded(GroupPath path, boolean expanded) {
        check();
        coordinator.cancelRestore();
        renderer.setExpanded(path, expanded);
    }

    public void regroup() {
        check();
        coordinator.cancelRestore();
        try {
            renderer.regroup();
            interactionStatus.setText("");
        } catch (RuntimeException failure) {
            interactionStatus.setText("Cannot update grouping: " + failure.getMessage());
            throw failure;
        }
    }

    private void publishRows(PageResult<K, T> result) {
        renderer.show(result.rows());
        drawGroupingBar();
    }

    private void layoutCommitted() {
        if (closed) return;
        coordinator.cancelRestore();
        drawGroupingBar();
        updateControls(coordinator.state());
        fireCommitted();
    }

    private void fireCommitted() {
        committedRevision.set(committedRevision.get() + 1);
    }

    private void drawGroupingBar() {
        groupingBar.setVisible(renderer.groupingEnabled());
        groupingBar.setManaged(renderer.groupingEnabled());
        NodeFocus focus = new NodeFocus(groupingBar);
        groupingBar.getChildren().clear();
        if (renderer.groupingColumns().isEmpty())
            groupingBar
                    .getChildren()
                    .add(new Label("Drag a column here to group the current page"));
        for (String id : renderer.groupingColumns()) {
            String label =
                    columnDefinitions.stream()
                            .filter(c -> c.id().equals(id))
                            .findFirst()
                            .orElseThrow()
                            .header();
            Label text = new Label(label);
            Button remove = new Button("×");
            remove.setAccessibleText("Remove grouping by " + label);
            remove.setOnAction(e ->
                    setGroupingColumns(
                            renderer.groupingColumns().stream()
                                    .filter(c -> !c.equals(id))
                                    .toList()));
            remove.addEventHandler(
                    javafx.scene.input.MouseEvent.DRAG_DETECTED, javafx.event.Event::consume);
            HBox chip = new HBox(6, text, remove);
            chip.getStyleClass().add("grouping-chip");
            chip.setAccessibleText(
                    "Grouping level " + (groupingBar.getChildren().size() + 1) + ": " + label);
            chip.setFocusTraversable(true);
            renderer.gestures().chip(chip, id);
            groupingBar.getChildren().add(chip);
        }
        focus.restore(groupingBar);
    }

    private static final class NodeFocus {
        private final boolean inside;

        NodeFocus(javafx.scene.Parent parent) {
            javafx.scene.Node focus =
                    parent.getScene() == null ? null : parent.getScene().getFocusOwner();
            boolean found = false;
            for (var node = focus; node != null; node = node.getParent())
                if (node == parent) found = true;
            inside = found;
        }

        void restore(javafx.scene.Parent parent) {
            if (inside) parent.requestFocus();
        }
    }

    /**
     * Configure defaults without fetching. Call after column/group definitions, before
     * attachment/load.
     */
    public void configureDefaults(GridSetup defaults) {
        check();
        if (hasPublished || attachment != null || state.get().phase() != GridCoordinator.Phase.IDLE)
            throw new IllegalStateException("Configure defaults before attachment or loading");
        if (setupDefinition == null)
            throw new IllegalStateException("Configure the grid definition first");
        var prepared =
                setupDefinition
                        .prepare(
                                defaults,
                                setupMigrations,
                                coordinator.capabilities(),
                                condition -> coordinator.validateFilters(List.of(condition)))
                        .setup();
        var groups =
                prepared.grouping() == null
                        ? new GroupingSetup(defaultGrouping, List.of())
                        : prepared.grouping();
        renderer.validateGrouping(groups);
        var widths = new LinkedHashMap<String, ColumnWidth>();
        prepared.columns().forEach(c -> widths.put(c.id(), c.width()));
        renderer.show(
                List.of(),
                prepared.columns().stream().map(GridSetup.ColumnState::id).toList(),
                widths,
                groups);
        initial = new GridQuery(prepared.filters(), prepared.sort(), firstPage(prepared.page()));
        defaultGrouping = groups.columns();
        developerDefaults = snapshotSetup();
        drawGroupingBar();
        updateControls(coordinator.state());
    }

    public GridSetup defaultSetup() {
        check();
        if (developerDefaults == null) developerDefaults = snapshotSetup();
        return developerDefaults;
    }

    public GridPersistenceAttachment<K, T> attachPersistence(
            GridApplicationConfiguration config, String gridId) {
        return attachPersistence(config, gridId, null);
    }

    public GridPersistenceAttachment<K, T> attachPersistence(
            GridApplicationConfiguration config, String gridId, String context) {
        check();
        if (attachment != null) throw new IllegalStateException("Persistence already attached");
        if (developerDefaults == null) developerDefaults = snapshotSetup();
        attachment =
                new GridPersistenceAttachment<>(
                        this, config, new GridSetupStore.Key(gridId, context));
        return attachment;
    }

    void addPersistenceControls(javafx.scene.Node feedback, List<MenuItem> items) {
        getChildren().add(feedback);
        setupMenu.getItems().addAll(items);
    }

    void removePersistenceControls(javafx.scene.Node feedback, List<MenuItem> items) {
        getChildren().remove(feedback);
        setupMenu.getItems().removeAll(items);
    }

    void detached(GridPersistenceAttachment<K, T> handle) {
        if (attachment == handle) attachment = null;
    }

    private static void requireFx() {
        if (!Platform.isFxApplicationThread())
            throw new IllegalStateException("Use the JavaFX application thread");
    }

    private void check() {
        requireFx();
        if (closed) throw new IllegalStateException("Grid is closed");
        initialize();
    }

    void refreshItems() {
        if (coordinator == null
                || closed
                || coordinator.state().phase() == GridCoordinator.Phase.IDLE) return;
        if (attachment != null) {
            var ready = attachment.ready().toCompletableFuture();
            if (!ready.isDone() || ready.isCompletedExceptionally()) return;
        }
        refresh();
    }

    boolean isInitialized() {
        return coordinator != null;
    }

    boolean isClosed() {
        return closed;
    }

    public void close() {
        requireFx();
        if (closed) return;
        if (columnSettings != null) {
            columnSettings.close();
            columnSettings = null;
        }
        if (attachment != null) attachment.close();
        closed = true;
        columnMenu.hide();
        closeFilter();
        declaration.close();
        if (coordinator != null) coordinator.close();
        if (pendingAlert != null) {
            pendingAlert.close();
            pendingAlert = null;
        }
        if (renderer != null) renderer.close();
        selectedKey.unbind();
        selectedElement.unbind();
        selectedKeys.unbind();
        selectedElements.unbind();
        selectedKeys.set(FXCollections.emptyObservableList());
        selectedElements.set(FXCollections.emptyObservableList());
        selectedKey.set(null);
        selectedElement.set(null);
        getChildren().clear();
    }
}
