package de.neitzel.fx.view;

import static org.junit.jupiter.api.Assertions.*;

import de.neitzel.grid.*;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class TableResultsTest {
    @BeforeAll
    static void start() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException e) {
            started.countDown();
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    static void fx(Runnable r) throws Exception {
        FutureTask<Void> task = new FutureTask<>(r, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }

    static List<ResultColumn> columns() {
        return List.of(
                new ResultColumn("name", "Name", 120, 60, 300, -1),
                new ResultColumn("city", "City", 100, 50, 200, -1));
    }

    static ResultRow row(String model, AtomicInteger disposed) {
        Label name = new Label(model), city = new Label("City");
        Button action = new Button("Edit");
        HBox root = new HBox(name, city, action);
        return new ResultRow(root, Map.of("name", name, "city", city), disposed::incrementAndGet);
    }

    @Test
    void resultOrderMappingSelectionAndLifetime() throws Exception {
        fx(() -> {
            Table table = new Table();
            AtomicInteger disposed = new AtomicInteger();
            List<ResultRow> views = new ArrayList<>();
            TableResults<Integer, String> renderer =
                    table.openResults(
                            columns(),
                            s -> {
                                var v = row(s, disposed);
                                views.add(v);
                                return v;
                            });
            renderer.show(List.of(new GridRow<>(2, "Z"), new GridRow<>(1, "A")));
            VBox body = (VBox) ((ScrollPane) table.getChildren().get(1)).getContent();
            assertEquals(
                    "Z",
                    ((Label) ((HBox) body.getChildren().get(0)).getChildren().get(0)).getText());
            renderer.select(2);
            assertEquals("Z", renderer.selectedElementProperty().get());
            renderer.setColumnOrder(List.of("city", "name"));
            assertSame(views.get(0).fields().get("city"), views.get(0).root().getChildren().get(0));
            assertInstanceOf(Button.class, views.get(0).root().getChildren().get(2));
            assertEquals(200, renderer.setColumnWidth("city", new ColumnWidth.Explicit(999)));
            assertEquals(200, views.get(0).fields().get("city").getPrefWidth());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> renderer.setColumnOrder(List.of("name", "name")));
            renderer.show(List.of(new GridRow<>(1, "New A")));
            assertEquals(2, disposed.get());
            assertEquals(2, renderer.selectedKeyProperty().get());
            assertNull(renderer.selectedElementProperty().get());
            renderer.show(List.of(new GridRow<>(2, "New Z")));
            assertEquals("New Z", renderer.selectedElementProperty().get());
            assertEquals(3, disposed.get());
            renderer.close();
            renderer.close();
            assertEquals(4, disposed.get());
            assertTrue(body.getChildren().isEmpty());
            assertThrows(IllegalStateException.class, () -> renderer.show(List.of()));
        });
    }

    @Test
    void failedReplacementKeepsOldRowsAndReleasesStagedRows() throws Exception {
        fx(() -> {
            AtomicInteger disposed = new AtomicInteger();
            Table t = new Table();
            TableResults<Integer, String> r =
                    t.openResults(
                            columns(),
                            s -> {
                                if (s.equals("fail"))
                                    throw new IllegalArgumentException("factory failure");
                                return row(s, disposed);
                            });
            r.show(List.of(new GridRow<>(1, "old")));
            r.select(1);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> r.show(List.of(new GridRow<>(2, "staged"), new GridRow<>(3, "fail"))));
            assertEquals(1, disposed.get());
            assertEquals("old", r.selectedElementProperty().get());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> r.show(List.of(new GridRow<>(2, "a"), new GridRow<>(2, "b"))));
            assertEquals(1, disposed.get());
            r.close();
            assertEquals(2, disposed.get());
        });
    }

    @Test
    void fixedColumnsAndModeAreValidated() throws Exception {
        fx(() -> {
            Table t = new Table();
            var defs =
                    List.of(
                            new ResultColumn("name", "Name", 120, 60, 300, 0),
                            new ResultColumn("city", "City", 100, 50, 200, -1));
            var r = t.openResults(defs, s -> row(String.valueOf(s), new AtomicInteger()));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> r.setColumnOrder(List.of("city", "name")));
            assertThrows(
                    IllegalStateException.class,
                    () -> t.openResults(defs, s -> row(String.valueOf(s), new AtomicInteger())));
            r.close();
        });
    }

    @Test
    void malformedMappingAndDisposalFailureAreObservable() throws Exception {
        fx(() -> {
            AtomicInteger releases = new AtomicInteger();
            Table table = new Table();
            TableResults<Integer, String> renderer =
                    table.openResults(
                            columns(),
                            model -> {
                                Label name = new Label(model);
                                Label city = new Label("City");
                                HBox root = new HBox(name, city);
                                return new ResultRow(
                                        root,
                                        model.equals("invalid")
                                                ? Map.of("name", name)
                                                : Map.of("name", name, "city", city),
                                        () -> {
                                            releases.incrementAndGet();
                                            if (model.equals("old"))
                                                throw new IllegalStateException("cleanup failed");
                                        });
                            });
            renderer.show(List.of(new GridRow<>(1, "old")));
            renderer.select(1);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> renderer.show(List.of(new GridRow<>(2, "invalid"))));
            assertEquals("old", renderer.selectedElementProperty().get());
            assertEquals(1, releases.get());
            renderer.show(List.of(new GridRow<>(3, "new")));
            assertEquals(2, releases.get());
            assertNotNull(renderer.disposalFailureProperty().get());
            renderer.select(3);
            assertEquals("new", renderer.selectedElementProperty().get());
            renderer.close();
            assertEquals(3, releases.get());
        });
    }

    @Test
    void horizontallyScrolledHeadersAlignWithRowsBesideAVerticalScrollbar() throws Exception {
        fx(() -> {
            Table table = new Table();
            List<ResultRow> views = new ArrayList<>();
            var renderer =
                    table.<Integer, String>openResults(
                            columns(),
                            model -> {
                                var view = row(model, new AtomicInteger());
                                view.root().setMinHeight(35);
                                views.add(view);
                                return view;
                            });
            try {
                renderer.setColumnWidth("name", new ColumnWidth.Explicit(300));
                renderer.setColumnWidth("city", new ColumnWidth.Explicit(200));
                renderer.show(
                        java.util.stream.IntStream.range(0, 30)
                                .mapToObj(i -> new GridRow<>(i, "Row " + i))
                                .toList());
                var root = new StackPane(table);
                new javafx.scene.Scene(root, 260, 260);
                root.resize(260, 260);
                root.applyCss();
                root.layout();
                var scroll = (ScrollPane) table.getChildren().get(1);
                var header = (ScrollPane) table.getChildren().getFirst();
                scroll.setHvalue(.7);
                root.layout();
                double cellX = header.getContent().lookup(".column-cell").localToScene(0, 0).getX();
                double rowX = views.getFirst().fields().get("name").localToScene(0, 0).getX();
                assertEquals(
                        cellX,
                        rowX,
                        1,
                        "Header and row must share pixel offset, including scrollbar and trailing"
                                + " actions");
            } finally {
                renderer.close();
            }
        });
    }
}
