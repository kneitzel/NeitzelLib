package de.neitzel.fx.example.grid;

import de.neitzel.fx.grid.filter.*;
import de.neitzel.fx.theme.ThemeManager;
import de.neitzel.fx.view.*;
import de.neitzel.grid.*;
import de.neitzel.gson.grid.GridSetupJson;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Application-owned composition; existing person-desk launcher and legacy Table are unchanged. */
public final class ProductGridDemo extends VBox implements AutoCloseable {
    public final DataGrid<Long, Product> grid;
    public final ThemeManager themes = new ThemeManager();
    public final ComboBox<String> sourcePicker = new ComboBox<>(), themePicker = new ComboBox<>();
    public final TextArea diagnostics = new TextArea();
    public final Label message = new Label();
    public final ProductCatalog catalog;
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor();
    private final DiagnosticSource memory, jdbc;
    private DiagnosticSource requested;
    private final GridSetupJson codec = new GridSetupJson();
    private final Path setupFile;
    private boolean closed;

    public ProductGridDemo(ProductCatalog catalog, Path setupFile) {
        this.catalog = catalog;
        this.setupFile = setupFile;
        setSpacing(8);
        setStyle("-fx-padding: 12;");
        memory =
                new DiagnosticSource(
                        catalog.memory,
                        clock,
                        this::log,
                        condition -> {
                            if (!(condition instanceof LocalNameLength))
                                catalog.jdbc.validateFilter(condition);
                        });
        jdbc = new DiagnosticSource(catalog.jdbc, clock, this::log, catalog.jdbc::validateFilter);
        requested = jdbc;
        grid =
                new DataGrid<>(
                        List.of(
                                new ResultColumn("id", "ID", 80, 60, 140, -1),
                                new ResultColumn("name", "Name", 260, 120, 600, -1),
                                new ResultColumn("category", "Category", 150, 90, 300, -1),
                                new ResultColumn("quantity", "Quantity", 110, 80, 220, -1),
                                new ResultColumn("price", "Price", 120, 90, 240, -1)),
                        ProductRow::create,
                        jdbc);
        grid.configureSetups(
                "demo.products",
                2,
                Map.of(
                        "id",
                        "long.v1",
                        "name",
                        "text.v1",
                        "category",
                        "text.v1",
                        "quantity",
                        "long.v1",
                        "price",
                        "decimal12-2.v1"),
                Map.of(1, ProductGridDemo::migrateLegacy));
        var editors = FilterEditors.builtIns(Set.of("jdbc-h2"));
        editors.register(
                new FilterEditors.Registration(
                        "demo.budget",
                        new FilterCapabilities(true, true, Set.of("jdbc-h2")),
                        Set.of("neitzel.number"),
                        PricePresetEditor::load));
        grid.setFilterEditors(editors);
        var pageNames =
                FilterCandidates.currentPage(
                        () -> {
                            var state = grid.stateProperty().get();
                            if (state.result() == null) return List.of();
                            return state.result().rows().stream()
                                    .map(
                                            row ->
                                                    new FilterCandidates.Candidate(
                                                            row.model().name() == null
                                                                    ? null
                                                                    : new FilterValue.Text(
                                                                            row.model().name()),
                                                            row.model().name() == null
                                                                    ? "(missing)"
                                                                    : row.model().name()))
                                    .toList();
                        });
        grid.configureFilter(
                "name",
                FilterColumn.text().withChecklist(FilterColumn.Checklist.CURRENT_PAGE, pageNames));
        grid.configureFilter("category", FilterColumn.text());
        grid.configureFilter("quantity", FilterColumn.number(Locale.ROOT, true, null, null));
        grid.configureFilter(
                "price",
                FilterColumn.number(
                        Locale.ROOT, false, BigDecimal.ZERO, new BigDecimal("9999999999.99")));
        grid.configureFilter("id", FilterColumn.number(Locale.ROOT, true, BigDecimal.ONE, null));
        sourcePicker.getItems().setAll("JDBC / H2", "Memory");
        sourcePicker.setValue("JDBC / H2");
        sourcePicker.setOnAction(
                e -> {
                    requested = sourcePicker.getValue().equals("Memory") ? memory : jdbc;
                    grid.setSource(requested);
                });
        themePicker.getItems().setAll("light", "dark", "high-contrast");
        themePicker.setValue("light");
        themePicker.setOnAction(e -> themes.select(themePicker.getValue()));
        var threshold = new Spinner<Integer>(0, 100000, 1000, 250);
        threshold.setPrefWidth(110);
        threshold.valueProperty().addListener((o, a, b) -> grid.setAllWarningThreshold(b));
        var custom = new CheckBox("Custom budget editor");
        custom.setOnAction(
                e ->
                        grid.configureFilter(
                                "price",
                                custom.isSelected()
                                        ? new FilterColumn(
                                                "demo.budget",
                                                Locale.ROOT,
                                                false,
                                                false,
                                                BigDecimal.ZERO,
                                                new BigDecimal("9999999999.99"),
                                                FilterColumn.Checklist.DISABLED,
                                                null)
                                        : FilterColumn.number(
                                                Locale.ROOT,
                                                false,
                                                BigDecimal.ZERO,
                                                new BigDecimal("9999999999.99"))));
        var outside = new CheckBox("Accept on outside click");
        var actions = new CheckBox("Show OK / Cancel");
        actions.setSelected(true);
        Runnable popup =
                () ->
                        grid.setFilterPopupOptions(
                                new FilterPopup.Options(
                                        outside.isSelected()
                                                ? FilterPopup.Outside.ACCEPT
                                                : FilterPopup.Outside.CANCEL,
                                        actions.isSelected(),
                                        actions.isSelected()));
        outside.setOnAction(e -> popup.run());
        actions.setOnAction(e -> popup.run());
        Button save = button("Save setup", this::save), load = button("Load setup", this::restore);
        Button legacy = button("Load v1 setup", this::restoreLegacy);
        Button clear = button("Clear filters", () -> grid.setFilters(List.of())),
                unsupported =
                        button(
                                "Memory-only filter",
                                () -> grid.setFilters(List.of(new LocalNameLength("name"))));
        Button slow =
                button(
                        "Slow next refresh",
                        () -> {
                            requested.next(2500, false, false);
                            grid.refresh();
                        });
        Button failure =
                button(
                        "Fail next refresh",
                        () -> {
                            requested.next(500, true, false);
                            grid.refresh();
                        });
        Button late =
                button(
                        "Late success then failure",
                        () -> {
                            requested.next(2500, false, true);
                            grid.refresh();
                            Platform.runLater(
                                    () -> {
                                        if (!closed) {
                                            requested.next(100, true, false);
                                            grid.refresh();
                                        }
                                    });
                        });
        Button lateFailure =
                button(
                        "Late failure then success",
                        () -> {
                            requested.next(2500, true, true);
                            grid.refresh();
                            Platform.runLater(
                                    () -> {
                                        if (!closed) grid.refresh();
                                    });
                        });
        Button insert = button("Insert fixture", () -> mutate(() -> catalog.insert()));
        Button delete =
                button(
                        "Delete selected",
                        () -> {
                            Long id = grid.selectedKeyProperty().get();
                            if (id == null)
                                throw new IllegalArgumentException("Select a row first");
                            mutate(() -> catalog.delete(id), () -> grid.confirmedDeleted(id));
                        });
        Button reset = button("Reset fixtures", () -> mutate(() -> catalog.reset()));
        Button tail =
                button(
                        "Delete displayed page",
                        () -> {
                            var state = grid.stateProperty().get();
                            if (state.result() == null) return;
                            var ids = state.result().rows().stream().map(GridRow::key).toList();
                            mutate(
                                    () -> {
                                        for (long id : ids) catalog.delete(id);
                                    });
                        });
        Button reorder =
                button(
                        "Price first",
                        () ->
                                grid.setColumnOrder(
                                        List.of("price", "id", "name", "category", "quantity")));
        Button width =
                button(
                        "Widen name",
                        () -> grid.setColumnWidth("name", new ColumnWidth.Explicit(400)));
        message.getStyleClass().add("theme-error");
        message.setWrapText(true);
        diagnostics.setEditable(false);
        diagnostics.setPrefRowCount(7);
        var detail = new TitledPane("Request diagnostics", diagnostics);
        detail.setExpanded(false);
        grid.stateProperty()
                .addListener(
                        (o, a, b) ->
                                log(
                                        "Grid generation "
                                                + b.generation()
                                                + ": "
                                                + b.phase()
                                                + "; requested="
                                                + b.requested().page()
                                                + "; displayed="
                                                + (b.displayed() == null
                                                        ? "none"
                                                        : b.displayed().page())));
        getChildren()
                .addAll(
                        new Label("Product catalog — deterministic memory / H2 demonstration"),
                        new FlowPane(
                                8,
                                8,
                                new Label("Source"),
                                sourcePicker,
                                new Label("Theme"),
                                themePicker,
                                new Label("All warning threshold"),
                                threshold),
                        new FlowPane(8, 8, save, load, legacy, reorder, width, clear, unsupported),
                        new FlowPane(8, 8, custom, outside, actions),
                        new FlowPane(8, 8, insert, delete, tail, reset),
                        new FlowPane(8, 8, slow, failure, late, lateFailure),
                        message,
                        grid,
                        detail);
        VBox.setVgrow(grid, Priority.ALWAYS);
        grid.setPrefHeight(450);
        grid.load();
    }

    @FunctionalInterface
    private interface Work {
        void run() throws Exception;
    }

    private Button button(String text, Work action) {
        var b = new Button(text);
        b.setOnAction(
                e -> {
                    try {
                        message.setText("");
                        action.run();
                    } catch (Exception error) {
                        message.setText(error.getMessage());
                    }
                });
        return b;
    }

    private void mutate(Work work) {
        mutate(work, grid::refresh);
    }

    private void mutate(Work work, Runnable publish) {
        CompletableFuture.runAsync(
                        () -> {
                            try {
                                work.run();
                            } catch (Exception e) {
                                throw new CompletionException(e);
                            }
                        },
                        catalog.workers)
                .whenComplete(
                        (v, e) ->
                                Platform.runLater(
                                        () -> {
                                            if (closed) return;
                                            if (e != null)
                                                message.setText(e.getCause().getMessage());
                                            else publish.run();
                                        }));
    }

    public void save() throws java.io.IOException {
        String document = codec.encode(grid.snapshotSetup());
        Files.createDirectories(setupFile.toAbsolutePath().getParent());
        var temporary =
                Files.createTempFile(setupFile.toAbsolutePath().getParent(), "grid-", ".json");
        try {
            Files.writeString(temporary, document);
            Files.move(
                    temporary,
                    setupFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        message.setText("Saved setup to " + setupFile);
    }

    /** Demonstrates an explicit column rename, not heuristic recovery of unknown columns. */
    public void restoreLegacy() {
        var current = grid.snapshotSetup();
        var columns =
                current.columns().stream()
                        .map(
                                c ->
                                        new GridSetup.ColumnState(
                                                c.id().equals("category") ? "group" : c.id(),
                                                c.type(),
                                                c.width()))
                        .toList();
        grid.restoreSetup(
                new GridSetup(
                        1,
                        "demo.products",
                        1,
                        columns,
                        List.of(new SortKey("group", SortKey.Direction.ASCENDING)),
                        List.of(
                                new TextFilter(
                                        "group", TextFilter.Operator.EQUALS, "Office", true)),
                        new PageRequest.Paged(0, 50)));
    }

    private static GridSetup migrateLegacy(GridSetup old) {
        var columns =
                old.columns().stream()
                        .map(c -> new GridSetup.ColumnState(rename(c.id()), c.type(), c.width()))
                        .toList();
        var sorts =
                old.sort().stream()
                        .map(s -> new SortKey(rename(s.columnId()), s.direction()))
                        .toList();
        var filters =
                old.filters().stream()
                        .map(
                                f -> {
                                    if (!f.columnId().equals("group")) return f;
                                    if (f instanceof TextFilter t)
                                        return (FilterCondition)
                                                new TextFilter(
                                                        "category",
                                                        t.operator(),
                                                        t.operand(),
                                                        t.caseSensitive());
                                    if (f instanceof MissingFilter m)
                                        return (FilterCondition)
                                                new MissingFilter("category", m.missing());
                                    if (f instanceof ValueFilter v)
                                        return (FilterCondition)
                                                new ValueFilter(
                                                        "category", v.values(), v.includeMissing());
                                    throw new IllegalArgumentException(
                                            "Cannot migrate legacy group filter " + f.typeId());
                                })
                        .toList();
        return new GridSetup(
                old.formatVersion(), old.definitionId(), 2, columns, sorts, filters, old.page());
    }

    private static String rename(String id) {
        return id.equals("group") ? "category" : id;
    }

    public void restore() {
        grid.restoreSetup(
                () -> {
                    try {
                        return codec.decode(Files.readString(setupFile));
                    } catch (java.io.IOException e) {
                        throw new IllegalArgumentException(
                                "Cannot read setup: " + e.getMessage(), e);
                    }
                });
    }

    private void log(String text) {
        Platform.runLater(
                () -> {
                    if (!closed) {
                        if (diagnostics.getLength() > 16000) diagnostics.deleteText(0, 8000);
                        diagnostics.appendText(text + "\n");
                    }
                });
    }

    public void install(Scene scene) {
        themes.register(scene);
    }

    public void close() {
        closed = true;
        DemoResources.closeAll(grid, themes, clock::shutdown);
    }
}
