package de.neitzel.fx.view;

import de.neitzel.fx.grid.filter.FilterColumn;
import de.neitzel.fx.theme.ThemeManager;
import de.neitzel.grid.*;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Manual native qualification surface. No synthetic OS input or real application data. */
public final class DataGridNativeProbe {
    record Item(long id, String name, String type, String status) {}

    public static void main(String[] args) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            AtomicInteger queries = new AtomicInteger(),
                    created = new AtomicInteger(),
                    disposed = new AtomicInteger();
            AtomicLong started = new AtomicLong();
            List<Item> items = new ArrayList<>();
            for (int i = 0; i < 2000; i++)
                items.add(
                        new Item(
                                i,
                                "record-%04d.example.com.".formatted(i),
                                List.of("A", "CAA", "MX", "NS").get(i % 4),
                                i % 3 == 0 ? "Missing" : "Live"));
            List<MemoryColumn<Item>> mappings =
                    List.of(
                            new MemoryColumn<>(
                                    "entry",
                                    MemoryColumn.Kind.TEXT,
                                    i -> new FilterValue.Text("Record")),
                            new MemoryColumn<>(
                                    "name",
                                    MemoryColumn.Kind.TEXT,
                                    i -> new FilterValue.Text(i.name())),
                            new MemoryColumn<>(
                                    "type",
                                    MemoryColumn.Kind.TEXT,
                                    i -> new FilterValue.Text(i.type())),
                            new MemoryColumn<>(
                                    "status",
                                    MemoryColumn.Kind.TEXT,
                                    i -> new FilterValue.Text(i.status())));
            var source =
                    new MemoryGridSource<Long, Item>(
                            () -> {
                                queries.incrementAndGet();
                                started.set(System.nanoTime());
                                return items;
                            },
                            Item::id,
                            Long::compare,
                            mappings,
                            Runnable::run,
                            250);
            var grid =
                    new DataGrid<Long, Item>(
                            List.of(
                                    new ResultColumn("entry", "Entry (fixed)", 140, 100, 300, 0),
                                    new ResultColumn("name", "Name", 300, 120, 600, -1),
                                    new ResultColumn("type", "Type", 140, 80, 300, -1),
                                    new ResultColumn("status", "Status", 260, 100, 600, -1)),
                            item -> {
                                created.incrementAndGet();
                                Label entry = new Label("Record");
                                TextField name = new TextField(item.name());
                                name.setAccessibleText("Record name draft " + item.id());
                                Label type = new Label(item.type()),
                                        status = new Label(item.status());
                                return new ResultRow(
                                        new HBox(entry, name, type, status),
                                        Map.of(
                                                "entry", entry, "name", name, "type", type,
                                                "status", status),
                                        disposed::incrementAndGet);
                            },
                            source);
            grid.configureGrouping(
                    List.of(
                            new GroupColumn<Item>(
                                    "name",
                                    MemoryColumn.Kind.TEXT,
                                    i -> new FilterValue.Text(i.name()),
                                    GroupColumn::display),
                            new GroupColumn<Item>(
                                    "type",
                                    MemoryColumn.Kind.TEXT,
                                    i -> new FilterValue.Text(i.type()),
                                    GroupColumn::display),
                            new GroupColumn<Item>(
                                    "status",
                                    MemoryColumn.Kind.TEXT,
                                    i -> new FilterValue.Text(i.status()),
                                    GroupColumn::display)),
                    true,
                    List.of("type"),
                    true,
                    1000);
            grid.configureSetups(
                    "native-grid",
                    1,
                    Map.of(
                            "entry", "text.v1", "name", "text.v1", "type", "text.v1", "status",
                            "text.v1"),
                    Map.of());
            for (String id : List.of("entry", "name", "type", "status"))
                grid.configureFilter(id, FilterColumn.text());
            Label metrics = new Label();
            metrics.setWrapText(true);
            metrics.setId("probeMetrics");
            ThemeManager themes = new ThemeManager();
            HBox themeControls = new HBox(8);
            for (String id : List.of("light", "dark", "high-contrast")) {
                Button button = new Button("Theme " + id);
                button.setOnAction(e -> themes.select(id));
                themeControls.getChildren().add(button);
            }
            Button close = new Button("Close probe");
            Button regroup = new Button("Explicit regroup"),
                    select = new Button("Select first record");
            regroup.setOnAction(e -> grid.regroup());
            select.setOnAction(e -> grid.select(0L));
            VBox root =
                    new VBox(
                            10,
                            new Label(
                                    "Native DataGrid qualification · fictional records · no"
                                            + " persistence"),
                            new Label(
                                    "Entry is fixed and has no grouping mapping. Other columns"
                                            + " group. Draft fields test component retention."),
                            themeControls,
                            new HBox(8, regroup, select, close),
                            grid,
                            metrics);
            root.setStyle("-fx-padding: 16;");
            VBox.setVgrow(grid, Priority.ALWAYS);
            Stage stage = new Stage();
            close.setOnAction(e -> stage.close());
            stage.setTitle("DataGrid native qualification");
            stage.setScene(new Scene(root, 1000, 800));
            themes.register(stage.getScene());
            Runnable describe =
                    () -> {
                        GridSetup setup = grid.snapshotSetup();
                        String text =
                                "Order: "
                                        + setup.columns().stream()
                                                .map(GridSetup.ColumnState::id)
                                                .toList()
                                        + " | widths: "
                                        + setup.columns().stream()
                                                .map(c -> c.id() + "=" + c.width())
                                                .toList()
                                        + " | groups: "
                                        + grid.groupingColumns()
                                        + " | source queries: "
                                        + queries.get()
                                        + " | created/disposed: "
                                        + created.get()
                                        + "/"
                                        + disposed.get()
                                        + " | window: "
                                        + stage.getX()
                                        + ","
                                        + stage.getY()
                                        + " scale="
                                        + stage.getOutputScaleX()
                                        + " | actionable selection: "
                                        + (grid.selectedElementProperty().get() != null);
                        metrics.setText(text);
                        System.out.println("STATE " + text);
                    };
            grid.committedRevisionProperty().addListener((o, b, a) -> describe.run());
            grid.selectedElementProperty().addListener((o, b, a) -> describe.run());
            grid.stateProperty()
                    .addListener((o, b, a) -> {
                        if (a.phase() == GridCoordinator.Phase.READY)
                            Platform.runLater(() -> {
                                root.applyCss();
                                root.layout();
                                describe.run();
                                System.out.println(
                                        "PUBLICATION rows="
                                                + a.result().rows().size()
                                                + " elapsedMs="
                                                + (System.nanoTime() - started.get()) / 1_000_000.0
                                                + " retained="
                                                + (created.get() - disposed.get())
                                                + " scale="
                                                + stage.getOutputScaleX());
                            });
                    });
            stage.setOnHidden(e -> {
                grid.close();
                themes.close();
                System.out.println(
                        "CLOSE created=" + created.get() + " disposed=" + disposed.get());
                done.countDown();
                Platform.runLater(Platform::exit);
            });
            stage.show();
            stage.toFront();
            grid.load();
            System.out.println(
                    "NATIVE READY java="
                            + System.getProperty("java.version")
                            + " javafx="
                            + System.getProperty("javafx.version")
                            + " os="
                            + System.getProperty("os.name"));
        });
        done.await();
    }
}
