package de.neitzel.fx.view;

import static org.junit.jupiter.api.Assertions.*;

import de.neitzel.fx.grid.filter.FilterColumn;
import de.neitzel.fx.theme.ThemeManager;
import de.neitzel.grid.*;

import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.PixelFormat;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import javax.imageio.ImageIO;

/** Opt-in native OS input, restricted to this suite's fictional-data window. */
@EnabledIfSystemProperty(named = "neitzellib.nativeGridTest", matches = "true")
@Timeout(120)
class DataGridNativeInteractionTest {
    record Item(long id, String type, String status) {}

    Stage stage;
    Robot robot;
    java.awt.Robot pointer;
    DataGrid<Long, Item> grid;
    ThemeManager themes;
    VBox root;
    CountDownLatch dragDone;
    final List<Throwable> fxFailures = new CopyOnWriteArrayList<>();
    Thread.UncaughtExceptionHandler previousExceptionHandler;
    Map<Long, TextField> editors = new HashMap<>();
    AtomicInteger queries = new AtomicInteger(),
            created = new AtomicInteger(),
            disposed = new AtomicInteger();

    @BeforeAll
    static void toolkit() throws Exception {
        DataGridTest.start();
        Platform.setImplicitExit(false);
        assertTrue(
                canPostInput(),
                "macOS denies native event posting; grant the Java launcher and launching terminal"
                    + " or IDE access");
    }

    private static boolean canPostInput() {
        if (!System.getProperty("os.name").startsWith("Mac")) return true;
        var library =
                com.sun.jna.NativeLibrary.getInstance(
                        "/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics");
        return (byte)
                        library.getFunction("CGPreflightPostEventAccess")
                                .invoke(byte.class, new Object[0])
                != 0;
    }

    @BeforeEach
    void open() throws Exception {
        pointer = new java.awt.Robot();
        pointer.setAutoDelay(15);
        fx(() -> {
            var thread = Thread.currentThread();
            previousExceptionHandler = thread.getUncaughtExceptionHandler();
            thread.setUncaughtExceptionHandler((failedThread, problem) -> fxFailures.add(problem));
            var items =
                    java.util.stream.LongStream.range(0, 24)
                            .mapToObj(i ->
                                    new Item(
                                            i,
                                            i % 2 == 0 ? "A" : "MX",
                                            i % 3 == 0 ? "Missing" : "Live"))
                            .toList();
            var source =
                    new MemoryGridSource<Long, Item>(
                            () -> {
                                queries.incrementAndGet();
                                return items;
                            },
                            Item::id,
                            Long::compare,
                            List.of(
                                    new MemoryColumn<>(
                                            "name",
                                            MemoryColumn.Kind.TEXT,
                                            i -> new FilterValue.Text("record-" + i.id()))),
                            Runnable::run,
                            250);
            grid =
                    new DataGrid<>(
                            List.of(
                                    new ResultColumn("entry", "Entry (fixed)", 140, 100, 300, 0),
                                    new ResultColumn("name", "Name", 300, 120, 600, -1),
                                    new ResultColumn("type", "Type", 140, 100, 300, -1),
                                    new ResultColumn("status", "Status", 260, 100, 600, -1)),
                            item -> {
                                created.incrementAndGet();
                                var name = new TextField("record-" + item.id());
                                editors.put(item.id(), name);
                                var entry = new Label("Record");
                                var type = new Label(item.type());
                                var status = new Label(item.status());
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
                            GroupColumn.<Item>text("name", i -> "record-" + i.id()),
                            GroupColumn.<Item>text("type", Item::type),
                            GroupColumn.<Item>text("status", Item::status)),
                    true,
                    List.of("type"),
                    true,
                    1000);
            grid.configureSetups(
                    "native-edges",
                    1,
                    Map.of("entry", "text", "name", "text", "type", "text", "status", "text"),
                    Map.of());
            grid.configureFilter("name", FilterColumn.text());
            root =
                    new VBox(
                            8,
                            new Label("Native grid edge verification — fictional records only"),
                            grid);
            root.setStyle("-fx-padding: 16;");
            VBox.setVgrow(grid, Priority.ALWAYS);
            stage = new Stage();
            stage.setTitle("DataGrid native edge verification");
            stage.setX(280);
            stage.setY(160);
            stage.setScene(new Scene(root, 1000, 760));
            themes = new ThemeManager();
            themes.register(stage.getScene());
            stage.show();
            stage.toFront();
            stage.requestFocus();
            robot = new Robot();
            return null;
        });
        waitFor(() -> stage.isFocused(), "Native fixture owns focus");
        fx(grid::loadAsync).toCompletableFuture().get(5, TimeUnit.SECONDS);
        pause(120);
        System.out.println(
                "NATIVE os="
                        + System.getProperty("os.name")
                        + " java="
                        + System.getProperty("java.version")
                        + " scale="
                        + fx(stage::getOutputScaleX));
    }

    @AfterEach
    void close() throws Exception {
        if (stage == null) return;
        if (pointer != null) {
            pointer.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            if (dragDone != null) dragDone.await(3, TimeUnit.SECONDS);
        }
        fx(() -> {
            if (pointer != null) pointer.keyRelease(java.awt.event.KeyEvent.VK_ESCAPE);
            if (grid != null) grid.close();
            if (themes != null) themes.close();
            stage.close();
            Thread.currentThread().setUncaughtExceptionHandler(previousExceptionHandler);
            return null;
        });
        assertTrue(fxFailures.isEmpty(), () -> "Unexpected FX exceptions: " + fxFailures);
        assertEquals(created.get(), disposed.get(), "Close releases all retained rows");
    }

    @Test
    void resizePreviewEscapeBoundsAndRelease() throws Exception {
        var before = setup();
        long revision = fx(() -> grid.committedRevisionProperty().get());
        Point2D start = point(fx(() -> header("name").lookup(".column-resize")), .5, .5);
        press(start);
        move(start.add(130, 0));
        pause(120);
        assertEquals(before, setup(), "Preview is not committed");
        capture("resize-preview");
        assertEquals(430, fx(() -> header("name").getWidth()), 2);
        key(KeyCode.ESCAPE);
        release();
        assertEquals(before, setup());
        assertEquals(revision, fx(() -> grid.committedRevisionProperty().get()));
        assertEquals(300, fx(() -> header("name").getWidth()), 1);
        press(point(fx(() -> header("name").lookup(".column-resize")), .5, .5));
        move(start.subtract(500, 0));
        release();
        assertEquals(new ColumnWidth.Explicit(120), width("name"));
        unchangedSource();
        System.out.println("PASS native resize preview/Escape/default intent/minimum/release");
    }

    @Test
    void headerReorderFixedInvalidOutsideAndEscape() throws Exception {
        var original = order();
        begin(fx(() -> label("name")));
        move(point(fx(() -> header("status")), .8, .5));
        pause(120);
        assertTrue(
                fx(() -> header("status").getStyleClass().contains("drop-after")),
                "Insertion marker before release");
        capture("header-ghost-insertion");
        release();
        waitFor(
                () -> grid.snapshotSetup().columns().getLast().id().equals("name"),
                "Native reorder commits");
        assertEquals(List.of("entry", "type", "status", "name"), order());
        fx(() -> {
            grid.setColumnOrder(original);
            return null;
        });
        begin(fx(() -> label("name")));
        move(point(fx(() -> header("entry")), .1, .5));
        pause(100);
        assertTrue(fx(() -> header("entry").getStyleClass().contains("drop-invalid")));
        capture("fixed-target-rejected");
        release();
        assertEquals(original, order());
        begin(fx(() -> label("entry")));
        move(point(fx(() -> header("status")), .8, .5));
        release();
        assertEquals(original, order());
        begin(fx(() -> label("name")));
        move(point(fx(() -> header("status")), .8, .5));
        key(KeyCode.ESCAPE);
        release();
        assertEquals(original, order());
        begin(fx(() -> label("name")));
        move(point(fx(() -> root.getChildren().getFirst()), .5, .5));
        release();
        assertEquals(original, order());
        unchangedSource();
        assertTrue(
                fx(() ->
                        stage.getScene().getFocusOwner() != null
                                && stage.getScene().getFocusOwner().getScene() == stage.getScene()),
                "Focus remains attached");
        System.out.println(
                "PASS native reorder/ghost/insertion/fixed source/fixed target/outside/Escape/no"
                        + " sort");
    }

    @Test
    void groupingAddDuplicateUnmappedChipReorderOutsideEscapeAndRemove() throws Exception {
        var original = order();
        begin(fx(() -> label("status")));
        move(point(fx(this::bar), .8, .5));
        release();
        waitFor(() -> grid.groupingColumns().equals(List.of("type", "status")), "Add group");
        begin(fx(() -> chip("status")));
        move(point(fx(() -> chip("type")), .1, .5));
        pause(100);
        capture("chip-insertion");
        release();
        waitFor(() -> grid.groupingColumns().equals(List.of("status", "type")), "Reorder chips");
        begin(fx(() -> label("status")));
        move(point(fx(this::bar), .8, .5));
        pause(80);
        assertTrue(fx(() -> bar().getStyleClass().contains("drop-invalid")));
        release();
        assertEquals(List.of("status", "type"), groups());
        begin(fx(() -> label("entry")));
        move(point(fx(this::bar), .8, .5));
        release();
        assertEquals(List.of("status", "type"), groups());
        begin(fx(() -> chip("status")));
        move(point(fx(() -> header("name")), .5, .5));
        release();
        assertEquals(List.of("status", "type"), groups());
        begin(fx(() -> chip("status")));
        move(point(fx(this::bar), .9, .5));
        key(KeyCode.ESCAPE);
        release();
        assertEquals(List.of("status", "type"), groups());
        click(fx(() -> chip("status").getChildren().getLast()));
        waitFor(() -> grid.groupingColumns().equals(List.of("type")), "Explicit chip removal");
        assertEquals(original, order());
        fx(() -> {
            grid.setGroupingEnabled(false);
            return null;
        });
        assertFalse(fx(() -> bar().isVisible()));
        fx(() -> {
            grid.setGroupingEnabled(true);
            return null;
        });
        assertEquals(List.of("type"), groups());
        unchangedSource();
        System.out.println(
                "PASS native grouping/chip"
                        + " reorder/duplicate/unmapped/outside/Escape/removal/disabled capability");
    }

    @Test
    void edgeScrollStopsOutsideAndKeepsHeadersAligned() throws Exception {
        fx(() -> {
            grid.setColumnWidth("name", new ColumnWidth.Explicit(600));
            grid.setColumnWidth("status", new ColumnWidth.Explicit(600));
            grid.setExpanded(path("A"), true);
            grid.setExpanded(path("MX"), true);
            return null;
        });
        pause(100);
        ScrollPane scroll = fx(() -> (ScrollPane) grid.lookup(".table-scroll"));
        var original = order();
        begin(fx(() -> label("name")));
        Bounds viewport = fx(() -> {
            var headerScroll = grid.lookup(".table-header-scroll");
            return headerScroll.localToScreen(headerScroll.getBoundsInLocal());
        });
        move(new Point2D(viewport.getMaxX() - 10, point(fx(() -> header("type")), .5, .5).getY()));
        waitFor(() -> scroll.getHvalue() > .1, "Native edge hover scrolls");
        capture("edge-scroll");
        // Leave vertically so the last header x remains in the edge zone.
        // A diagonal exit can mask stale-pointer scrolling by crossing the centre.
        move(
                new Point2D(
                        viewport.getMaxX() - 10,
                        point(fx(() -> root.getChildren().getFirst()), .5, .5).getY()));
        pause(150);
        double stopped = fx(scroll::getHvalue);
        pause(250);
        assertEquals(stopped, fx(scroll::getHvalue), .001, "Leaving header stops scrolling");
        key(KeyCode.ESCAPE);
        release();
        assertEquals(original, order());
        double headerX =
                fx(() -> header("type").localToScreen(header("type").getBoundsInLocal()).getMinX());
        double rowX =
                fx(() -> {
                    Node field = editors.get(0L).getParent().getChildrenUnmodifiable().get(2);
                    return field.localToScreen(field.getBoundsInLocal()).getMinX();
                });
        assertEquals(headerX, rowX, 1, "Header/body alignment");
        fx(() -> {
            scroll.setHvalue(0);
            return null;
        });
        pause(100);
        begin(fx(() -> label("name")));
        move(new Point2D(viewport.getMaxX() - 10, point(fx(() -> header("type")), .5, .5).getY()));
        waitFor(() -> scroll.getHvalue() > .95, "Reach offscreen insertion target");
        move(point(fx(() -> header("status")), .85, .5));
        release();
        assertEquals(List.of("entry", "type", "status", "name"), order());
        capture("offscreen-drop-committed");
        unchangedSource();
        System.out.println(
                "PASS native edge hover/outside stop/header-body alignment/offscreen drop");
    }

    @Test
    void disclosureFocusDraftsFilterIsolationAndThemes() throws Exception {
        Node heading =
                fx(() ->
                        grid.lookupAll(".group-heading").stream()
                                .filter(n -> ((Button) n).getText().contains("Type: A"))
                                .findFirst()
                                .orElseThrow());
        click(heading);
        waitFor(
                () -> editors.get(0L).getParent().getParent() != null,
                "Pointer disclosure expands");
        click(editors.get(0L));
        key(KeyCode.HOME);
        key(KeyCode.X);
        String draft = fx(() -> editors.get(0L).getText());
        assertTrue(draft.toLowerCase(Locale.ROOT).startsWith("x"));
        fx(() -> {
            grid.select(0L);
            grid.setExpanded(path("A"), false);
            return null;
        });
        assertNull(fx(() -> grid.selectedElementProperty().get()));
        assertSame(heading, fx(() -> stage.getScene().getFocusOwner()));
        key(KeyCode.RIGHT);
        waitFor(
                () -> grid.selectedElementProperty().get() != null,
                "Keyboard disclosure restores selection");
        assertEquals(draft, fx(() -> editors.get(0L).getText()));
        click(fx(() -> header("name").lookup(".filter-button")));
        pause(100);
        assertEquals(1, queries.get());
        assertTrue(setup().sort().isEmpty());
        key(KeyCode.ESCAPE);
        for (String theme : List.of("light", "dark", "high-contrast")) {
            fx(() -> {
                themes.select(theme);
                grid.setGroupingColumns(List.of("type", "status"));
                grid.setExpanded(path("A"), true);
                return null;
            });
            pause(100);
            capture("nested-" + theme);
        }
        unchangedSource();
        System.out.println(
                "PASS native disclosure/drafts/hidden selection/focus/filter isolation/nested"
                        + " themes");
    }

    private void unchangedSource() throws Exception {
        assertEquals(1, queries.get());
        assertEquals(0, disposed.get());
        assertTrue(setup().sort().isEmpty());
    }

    private static GroupPath path(String value) {
        return new GroupPath(List.of(new GroupPath.Segment("type", new FilterValue.Text(value))));
    }

    private HBox header(String id) {
        return (HBox)
                grid.lookupAll(".column-cell").stream()
                        .filter(n -> id.equals(n.getUserData()))
                        .findFirst()
                        .orElseThrow();
    }

    private Node label(String id) {
        return header(id).getChildren().getFirst();
    }

    private HBox bar() {
        return (HBox) grid.lookup(".data-grid-grouping");
    }

    private HBox chip(String id) {
        return (HBox)
                bar().getChildren().stream()
                        .filter(n -> id.equals(n.getUserData()))
                        .findFirst()
                        .orElseThrow();
    }

    private GridSetup setup() throws Exception {
        return fx(grid::snapshotSetup);
    }

    private List<String> order() throws Exception {
        return setup().columns().stream().map(GridSetup.ColumnState::id).toList();
    }

    private List<String> groups() throws Exception {
        return fx(grid::groupingColumns);
    }

    private ColumnWidth width(String id) throws Exception {
        return setup().columns().stream()
                .filter(c -> c.id().equals(id))
                .findFirst()
                .orElseThrow()
                .width();
    }

    private Point2D point(Node n, double x, double y) throws Exception {
        return fx(() -> {
            root.applyCss();
            for (int pulse = 0; pulse < 3; pulse++) root.layout();
            return n.localToScreen(
                    n.getBoundsInLocal().getMinX() + n.getBoundsInLocal().getWidth() * x,
                    n.getBoundsInLocal().getMinY() + n.getBoundsInLocal().getHeight() * y);
        });
    }

    private void focus() {
        boolean ownedFocus =
                javafx.stage.Window.getWindows().stream()
                        .anyMatch(window -> window.isFocused() && belongsToFixture(window));
        assertTrue(ownedFocus, "Stop input if fixture and its owned popups lose focus");
    }

    private boolean belongsToFixture(javafx.stage.Window window) {
        for (var current = window; current != null; ) {
            if (current == stage) return true;
            current =
                    current instanceof javafx.stage.PopupWindow popup
                            ? popup.getOwnerWindow()
                            : current instanceof Stage owned ? owned.getOwner() : null;
        }
        return false;
    }

    private void press(Point2D p) throws Exception {
        fx(() -> {
            focus();
            return null;
        });
        pointer.mouseMove((int) Math.round(p.getX()), (int) Math.round(p.getY()));
        pause(60);
        pointer.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        pause(100);
    }

    private void begin(Node n) throws Exception {
        dragDone = new CountDownLatch(1);
        var completed = dragDone;
        fx(() -> {
            n.addEventHandler(DragEvent.DRAG_DONE, event -> completed.countDown());
            return null;
        });
        Point2D p = point(n, .35, .5);
        press(p);
        move(p.add(16, 0));
        pause(160);
    }

    private void move(Point2D end) throws Exception {
        Point2D start = fx(() -> new Point2D(robot.getMouseX(), robot.getMouseY()));
        for (int i = 1; i <= 12; i++) {
            Point2D p = start.add(end.subtract(start).multiply(i / 12.0));
            fx(() -> {
                focus();
                return null;
            });
            pointer.mouseMove((int) Math.round(p.getX()), (int) Math.round(p.getY()));
            pause(18);
        }
        pause(70);
    }

    private void release() throws Exception {
        pointer.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        if (dragDone != null) {
            assertTrue(
                    dragDone.await(3, TimeUnit.SECONDS),
                    "Native drag completion must drain before the next gesture");
            dragDone = null;
            fx(() -> null);
        }
        pause(140);
    }

    private void click(Node n) throws Exception {
        press(point(n, .5, .5));
        release();
    }

    private void key(KeyCode code) throws Exception {
        fx(() -> {
            focus();
            return null;
        });
        // Use one native input source for the held pointer and keyboard events.
        pointer.keyPress(code.getCode());
        pointer.keyRelease(code.getCode());
        pause(100);
        // macOS completes cancellation asynchronously (including its return animation).
        // Releasing early can clear JavaFX gesture state before the native callback.
        if (code == KeyCode.ESCAPE && dragDone != null) {
            assertTrue(
                    dragDone.await(5, TimeUnit.SECONDS),
                    "Escape must finish the native drag before mouse release");
        }
    }

    private void capture(String name) throws Exception {
        Path dir = Path.of(System.getProperty("neitzellib.nativeGridOutput", "target/native-grid"));
        Files.createDirectories(dir);
        var image =
                fx(() -> {
                    focus();
                    return robot.getScreenCapture(
                            null,
                            stage.getX(),
                            stage.getY(),
                            stage.getWidth(),
                            stage.getHeight(),
                            false);
                });
        int w = (int) image.getWidth(), h = (int) image.getHeight();
        int[] pixels = new int[w * h];
        image.getPixelReader()
                .getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        var output = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        output.setRGB(0, 0, w, h, pixels, 0, w);
        ImageIO.write(output, "png", dir.resolve(name + ".png").toFile());
    }

    private static void pause(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }

    private static <V> V fx(Callable<V> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(5, TimeUnit.SECONDS);
    }

    private static void waitFor(BooleanSupplier condition, String message) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < until) {
            if (fx(condition::getAsBoolean)) return;
            pause(30);
        }
        assertTrue(fx(condition::getAsBoolean), message);
    }
}
