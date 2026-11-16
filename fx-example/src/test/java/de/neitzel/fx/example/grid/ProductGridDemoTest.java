package de.neitzel.fx.example.grid;

import static org.junit.jupiter.api.Assertions.*;

import de.neitzel.fx.grid.filter.*;
import de.neitzel.grid.*;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import org.junit.jupiter.api.*;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

import javax.imageio.ImageIO;

class ProductGridDemoTest {
    static final Queue<Throwable> uncaught = new ConcurrentLinkedQueue<>();
    ProductCatalog catalog;
    ProductGridDemo demo;
    Stage stage;
    Path setup;
    Thread.UncaughtExceptionHandler previous;

    @BeforeAll
    static void startup() throws Exception {
        var ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException already) {
            ready.countDown();
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        fx(() -> Platform.setImplicitExit(false));
    }

    static void fx(Runnable work) throws Exception {
        var f = new FutureTask<Void>(work, null);
        Platform.runLater(f);
        f.get(90, TimeUnit.SECONDS);
    }

    @BeforeEach
    void open() throws Exception {
        catalog = new ProductCatalog(10000);
        setup = Files.createTempDirectory("grid-demo-test").resolve("setup.json");
        fx(
                () -> {
                    previous = Thread.currentThread().getUncaughtExceptionHandler();
                    Thread.currentThread().setUncaughtExceptionHandler((t, e) -> uncaught.add(e));
                    demo = new ProductGridDemo(catalog, setup);
                    stage = new Stage();
                    var scene = new Scene(demo, 1120, 820);
                    demo.install(scene);
                    stage.setScene(scene);
                    stage.show();
                });
        ready();
    }

    @AfterEach
    void close() throws Exception {
        fx(
                () -> {
                    demo.close();
                    stage.close();
                    Thread.currentThread().setUncaughtExceptionHandler(previous);
                });
        catalog.close();
        Files.deleteIfExists(setup);
        Files.deleteIfExists(setup.getParent());
        assertTrue(uncaught.isEmpty(), uncaught.toString());
        uncaught.clear();
    }

    void await(BooleanSupplier condition) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        var value = new AtomicBoolean();
        while (System.nanoTime() < until) {
            fx(() -> value.set(condition.getAsBoolean()));
            if (value.get()) return;
            Thread.sleep(20);
        }
        fail("Timed out waiting for demo state");
    }

    void ready() throws Exception {
        await(() -> demo.grid.stateProperty().get().phase() == GridCoordinator.Phase.READY);
    }

    void click(String label) {
        Button button =
                (Button)
                        demo.lookupAll(".button").stream()
                                .filter(n -> n instanceof Button b && b.getText().equals(label))
                                .findFirst()
                                .orElseThrow();
        button.fire();
    }

    @Test
    void fxmlFieldsAlignWithHeadersAfterCssAndReorder() throws Exception {
        fx(
                () -> {
                    for (String theme : List.of("light", "dark", "high-contrast")) {
                        demo.themePicker.setValue(theme);
                        demo.grid.setColumnOrder(
                                List.of("price", "id", "name", "category", "quantity"));
                        demo.grid.setColumnWidth("name", new ColumnWidth.Explicit(400));
                        demo.applyCss();
                        demo.layout();
                        var row = (javafx.scene.layout.HBox) demo.grid.lookup(".result-row");
                        assertEquals(javafx.geometry.Insets.EMPTY, row.getPadding());
                        var headers =
                                demo.grid.lookupAll(".column-cell").stream()
                                        .sorted(
                                                java.util.Comparator.comparingDouble(
                                                        n ->
                                                                n.localToScene(n.getBoundsInLocal())
                                                                        .getMinX()))
                                        .toList();
                        assertEquals(5, headers.size());
                        for (int index = 0; index < headers.size(); index++) {
                            var field = row.getChildren().get(index);
                            assertEquals(
                                    headers.get(index)
                                            .localToScene(headers.get(index).getBoundsInLocal())
                                            .getMinX(),
                                    field.localToScene(field.getBoundsInLocal()).getMinX(),
                                    .1);
                            assertEquals(
                                    headers.get(index).getBoundsInLocal().getWidth(),
                                    field.getBoundsInLocal().getWidth(),
                                    .1);
                            assertEquals(4, ((Label) field).getPadding().getLeft(), .1);
                        }
                    }
                });
    }

    @Test
    void currentPageChecklistKeepsExactOriginalValues() throws Exception {
        var root = new AtomicReference<javafx.scene.Parent>();
        fx(
                () -> {
                    var opener =
                            (Button)
                                    demo.lookupAll(".filter-button").stream()
                                            .filter(
                                                    n ->
                                                            "Filter Name"
                                                                    .equals(n.getAccessibleText()))
                                            .findFirst()
                                            .orElseThrow();
                    opener.fire();
                    var window =
                            javafx.stage.Window.getWindows().stream()
                                    .filter(
                                            w ->
                                                    w != stage
                                                            && w.getScene()
                                                                            .getRoot()
                                                                            .lookup("#mode")
                                                                    != null)
                                    .findFirst()
                                    .orElseThrow();
                    root.set(window.getScene().getRoot());
                });
        // Select the mode by its displayed name without assuming the internal list ordering.
        fx(
                () -> {
                    var box = (ComboBox<?>) root.get().lookup("#mode");
                    box.getSelectionModel().select(box.getItems().indexOf("Checklist"));
                });
        await(
                () ->
                        root.get().lookupAll(".check-box").stream()
                                .anyMatch(
                                        n ->
                                                n instanceof CheckBox b
                                                        && "Alpha".equals(b.getText())));
        fx(
                () -> {
                    assertTrue(
                            ((Label) root.get().lookup("#scope"))
                                    .getText()
                                    .contains("Current page"));
                    var choices =
                            root.get().lookupAll(".check-box").stream()
                                    .map(n -> (CheckBox) n)
                                    .toList();
                    assertTrue(choices.stream().anyMatch(b -> "alpha".equals(b.getText())));
                    choices.stream()
                            .filter(b -> "Alpha".equals(b.getText()))
                            .findFirst()
                            .orElseThrow()
                            .fire();
                    ((Button)
                                    root.get().lookupAll(".button").stream()
                                            .filter(
                                                    n ->
                                                            n instanceof Button b
                                                                    && "OK".equals(b.getText()))
                                            .findFirst()
                                            .orElseThrow())
                            .fire();
                });
        ready();
        fx(
                () ->
                        assertEquals(
                                List.of("Alpha"),
                                demo.grid.stateProperty().get().result().rows().stream()
                                        .map(r -> r.model().name())
                                        .toList()));
    }

    @Test
    void actualFxmlEditorsSourceSwitchSetupThemesAndCapabilityErrors() throws Exception {
        fx(
                () -> {
                    var context =
                            new FilterEditorContext(
                                    "price",
                                    FilterColumn.number(Locale.ROOT, false, null, null),
                                    null,
                                    List.of(),
                                    FilterColumn.Checklist.DISABLED);
                    var editor = PricePresetEditor.load(context);
                    assertEquals(
                            new NumericFilter(
                                    "price",
                                    NumericFilter.Operator.LESS_OR_EQUAL,
                                    new BigDecimal("10.00"),
                                    null),
                            editor.draft());
                    TextField field = (TextField) editor.view().lookup("#maximum");
                    field.setText("1.001");
                    assertFalse(editor.validationMessageProperty().get().isEmpty());
                    field.setText("1.0");
                    catalog.jdbc.validateFilter(editor.draft());
                    demo.grid.setFilters(List.of(editor.draft()));
                    editor.close();
                });
        ready();
        fx(
                () -> {
                    assertTrue(
                            demo.grid.stateProperty().get().result().rows().stream()
                                    .allMatch(
                                            r -> r.model().price().compareTo(BigDecimal.ONE) <= 0));
                    demo.grid.select(1L);
                    demo.grid.setColumnOrder(
                            List.of("price", "id", "name", "category", "quantity"));
                    demo.grid.setColumnWidth("name", new ColumnWidth.Explicit(400));
                    try {
                        demo.save();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    demo.themePicker.setValue("dark");
                    demo.grid.setFilters(List.of());
                });
        ready();
        fx(() -> demo.restoreLegacy());
        ready();
        fx(
                () -> {
                    assertEquals(2, demo.grid.snapshotSetup().definitionVersion());
                    assertTrue(
                            demo.grid.stateProperty().get().result().rows().stream()
                                    .allMatch(r -> "Office".equals(r.model().category())));
                    demo.restore();
                });
        ready();
        fx(
                () -> {
                    assertEquals("price", demo.grid.snapshotSetup().columns().getFirst().id());
                    assertEquals(1, demo.grid.snapshotSetup().filters().size());
                    demo.sourcePicker.setValue("Memory");
                });
        ready();
        fx(
                () -> {
                    click("Memory-only filter");
                });
        ready();
        fx(
                () -> {
                    assertTrue(
                            demo.grid.stateProperty().get().result().rows().stream()
                                    .allMatch(r -> r.model().name().length() <= 5));
                    demo.sourcePicker.setValue("JDBC / H2");
                });
        await(() -> demo.grid.stateProperty().get().phase() == GridCoordinator.Phase.ERROR);
        fx(
                () -> {
                    assertTrue(
                            demo.grid
                                    .stateProperty()
                                    .get()
                                    .error()
                                    .getMessage()
                                    .contains("does not support"));
                    click("Clear filters");
                });
        ready();
        fx(() -> demo.sourcePicker.setValue("Memory"));
        ready();
        fx(() -> demo.sourcePicker.setValue("JDBC / H2"));
        ready();
        fx(
                () -> {
                    demo.themePicker.setValue("high-contrast");
                    stage.getScene().getRoot().applyCss();
                    stage.getScene().getRoot().layout();
                    var image = demo.snapshot(null, null);
                    var buffered =
                            new BufferedImage(
                                    (int) image.getWidth(),
                                    (int) image.getHeight(),
                                    BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < buffered.getHeight(); y++)
                        for (int x = 0; x < buffered.getWidth(); x++)
                            buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                    try {
                        Files.createDirectories(Path.of("target/jdbc-evidence"));
                        ImageIO.write(
                                buffered, "png", Path.of("target/jdbc-evidence/demo.png").toFile());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Test
    void lateSuccessFailureRetryAndDisappearingPage() throws Exception {
        fx(() -> click("Late success then failure"));
        await(() -> demo.grid.stateProperty().get().phase() == GridCoordinator.Phase.ERROR);
        Thread.sleep(2800);
        fx(
                () -> {
                    assertEquals(
                            GridCoordinator.Phase.ERROR, demo.grid.stateProperty().get().phase());
                    demo.grid.retry();
                });
        ready();
        fx(() -> click("Late failure then success"));
        ready();
        Thread.sleep(2800);
        fx(
                () -> {
                    assertEquals(
                            GridCoordinator.Phase.READY, demo.grid.stateProperty().get().phase());
                    assertTrue(demo.diagnostics.getText().contains("obsolete completion"));
                    demo.grid.last();
                });
        ready();
        fx(
                () -> {
                    assertEquals(
                            199,
                            ((PageRequest.Paged) demo.grid.stateProperty().get().displayed().page())
                                    .pageIndex());
                    click("Delete displayed page");
                });
        await(
                () ->
                        demo.grid.stateProperty().get().phase() == GridCoordinator.Phase.READY
                                && demo.grid.stateProperty().get().result().total().orElseThrow()
                                        == 9950);
        fx(
                () ->
                        assertEquals(
                                198,
                                ((PageRequest.Paged)
                                                demo.grid.stateProperty().get().displayed().page())
                                        .pageIndex()));
    }

    @Test
    void allCancellationSupersessionRestorationAndFullMaterialization() throws Exception {
        var decision = new CompletableFuture<Boolean>();
        var asked = new AtomicLong();
        fx(
                () -> {
                    demo.grid.setAllConfirmation(
                            (q, count) -> {
                                asked.set(count);
                                return decision;
                            });
                    demo.grid.showAll();
                });
        await(() -> asked.get() == 10000);
        assertEquals(
                1,
                catalog.database.getActiveConnections(),
                "Only application anchor remains while awaiting person");
        fx(() -> decision.complete(false));
        ready();
        fx(() -> assertInstanceOf(PageRequest.Paged.class, demo.grid.snapshotSetup().page()));
        var obsolete = new CompletableFuture<Boolean>();
        asked.set(0);
        fx(
                () -> {
                    demo.grid.setAllConfirmation(
                            (q, count) -> {
                                asked.set(count);
                                return obsolete;
                            });
                    demo.grid.showAll();
                });
        await(() -> asked.get() == 10000);
        fx(
                () ->
                        demo.grid.setFilters(
                                List.of(
                                        new NumericFilter(
                                                "id",
                                                NumericFilter.Operator.LESS_OR_EQUAL,
                                                new BigDecimal("25"),
                                                null))));
        ready();
        fx(() -> obsolete.complete(true));
        ready();
        fx(() -> assertEquals(25, demo.grid.stateProperty().get().result().rows().size()));
        fx(
                () -> {
                    demo.grid.setPageSize(50);
                    demo.grid.setFilters(List.of());
                });
        ready();
        // One measured, explicitly approved All load of real FXML Components (not virtualization).
        long before = System.nanoTime();
        var approvals = new AtomicInteger();
        fx(
                () -> {
                    demo.grid.setAllConfirmation(
                            (q, count) -> {
                                approvals.incrementAndGet();
                                return CompletableFuture.completedFuture(true);
                            });
                    demo.grid.showAll();
                });
        ready();
        long elapsed = (System.nanoTime() - before) / 1_000_000;
        fx(
                () -> {
                    assertEquals(10000, demo.grid.stateProperty().get().result().rows().size());
                    assertInstanceOf(PageRequest.All.class, demo.grid.snapshotSetup().page());
                    try {
                        demo.save();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    demo.grid.setPageSize(50);
                });
        ready();
        asked.set(0);
        var restoreDecision = new CompletableFuture<Boolean>();
        fx(
                () -> {
                    demo.grid.setAllConfirmation(
                            (q, count) -> {
                                asked.set(count);
                                return restoreDecision;
                            });
                    demo.restore();
                });
        await(() -> asked.get() == 10000);
        fx(() -> restoreDecision.complete(false));
        ready();
        fx(() -> assertInstanceOf(PageRequest.Paged.class, demo.grid.snapshotSetup().page()));
        assertEquals(1, approvals.get());
        Files.createDirectories(Path.of("target/jdbc-evidence"));
        Files.writeString(
                Path.of("target/jdbc-evidence/fx-materialization.txt"),
                "10,000 H2 rows as fresh FXML Components; count/approval/fetch/publication"
                        + " elapsedMs="
                        + elapsed
                        + "; includes UI materialization, no virtualization. Single observed"
                        + " run.\n");
    }
}
