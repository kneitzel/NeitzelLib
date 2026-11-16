package de.neitzel.fx.view;

import de.neitzel.fx.grid.filter.*;
import de.neitzel.grid.*;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/**
 * Local/external data grid coordinator and paging presentation. Construct and use on the FX thread.
 * No query runs until load/refresh or an explicit query change, allowing configuration before first
 * load. The application owns the source and executor. Closing releases only this grid's requests
 * and rows.
 */
public final class DataGrid<K, T> extends VBox implements AutoCloseable {
    private final Table table = new Table();
    private final TableResults<K, T> renderer;
    private final GridCoordinator<K, T> coordinator;
    private final ReadOnlyObjectWrapper<GridCoordinator.State<K, T>> state =
            new ReadOnlyObjectWrapper<>();
    private final Label status = new Label("Not loaded");
    private final Button first = new Button("First"),
            previous = new Button("Previous"),
            next = new Button("Next"),
            last = new Button("Last"),
            retry = new Button("Retry"),
            refresh = new Button("Refresh");
    private final ComboBox<String> pageSize = new ComboBox<>();
    private final GridQuery initial;
    private final List<ResultColumn> columnDefinitions;
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

    public DataGrid(
            List<ResultColumn> columns,
            Function<T, ResultRow> rowFactory,
            GridSource<K, T> source) {
        requireFx();
        columnDefinitions = List.copyOf(columns);
        Objects.requireNonNull(source);
        initial =
                new GridQuery(
                        List.of(),
                        List.of(),
                        new PageRequest.Paged(
                                0, Math.min(50, source.capabilities().maximumPageSize())));
        renderer = table.openResults(columns, rowFactory);
        coordinator =
                new GridCoordinator<>(
                        source,
                        Platform::runLater,
                        this::changed,
                        result -> renderer.show(result.rows()));
        coordinator.setConfirmation(this::confirmAll);
        renderer.setOnSortRequest(
                id -> {
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
        pageSize.setOnAction(
                e -> {
                    if (updating || pageSize.getValue() == null) return;
                    setPageRequest(
                            pageSize.getValue().equals("All")
                                    ? new PageRequest.All()
                                    : new PageRequest.Paged(
                                            0, Integer.parseInt(pageSize.getValue())));
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
        getStyleClass().add("data-grid");
        setSpacing(8);
        getChildren().addAll(table, controls, status);
        VBox.setVgrow(table, Priority.ALWAYS);
        state.set(coordinator.state());
        updateControls(coordinator.state());
    }

    public ReadOnlyObjectProperty<GridCoordinator.State<K, T>> stateProperty() {
        return state.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<K> selectedKeyProperty() {
        return renderer.selectedKeyProperty();
    }

    public ReadOnlyObjectProperty<T> selectedElementProperty() {
        return renderer.selectedElementProperty();
    }

    public void select(K key) {
        check();
        renderer.select(key);
    }

    public void confirmedDeleted(K key) {
        check();
        if (Objects.equals(key, selectedKeyProperty().get())) renderer.select(null);
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
                                .map(
                                        c ->
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
                1,
                setupDefinition.id(),
                setupDefinition.version(),
                renderer.columnOrder().stream()
                        .map(
                                id ->
                                        new GridSetup.ColumnState(
                                                id, types.get(id), renderer.columnWidths().get(id)))
                        .toList(),
                committed.sort(),
                committed.filters(),
                firstPage(committed.page()));
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
            Map<String, ColumnWidth> widths = new LinkedHashMap<>();
            setup.columns().forEach(c -> widths.put(c.id(), c.width()));
            coordinator.restoreSetup(
                    new GridQuery(setup.filters(), setup.sort(), setup.page()),
                    this::closeFilter,
                    result -> {
                        renderer.show(
                                result.rows(),
                                setup.columns().stream().map(GridSetup.ColumnState::id).toList(),
                                widths);
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

    public void setPageSize(int size) {
        setPageRequest(new PageRequest.Paged(0, size));
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
            if (value.displayed() != null)
                renderer.setFilteredColumns(
                        value.displayed().filters().stream()
                                .map(FilterCondition::columnId)
                                .collect(java.util.stream.Collectors.toSet()));
            if (visibleSourceVersion != value.sourceVersion()) {
                visibleSourceVersion = value.sourceVersion();
                renderer.select(null);
            }
            renderer.setRowsInteractive(value.phase() == GridCoordinator.Phase.READY);
            updateControls(value);
        }
        state.set(value);
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
        status.setText(message);
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
        alert.setOnHidden(
                e -> {
                    if (pendingAlert == alert) pendingAlert = null;
                    answer.complete(alert.getResult() == proceed);
                });
        alert.show();
        return answer;
    }

    private static void requireFx() {
        if (!Platform.isFxApplicationThread())
            throw new IllegalStateException("Use the JavaFX application thread");
    }

    private void check() {
        requireFx();
        if (closed) throw new IllegalStateException("Grid is closed");
    }

    public void close() {
        requireFx();
        if (closed) return;
        closed = true;
        closeFilter();
        coordinator.close();
        if (pendingAlert != null) {
            pendingAlert.close();
            pendingAlert = null;
        }
        renderer.close();
        getChildren().clear();
    }
}
