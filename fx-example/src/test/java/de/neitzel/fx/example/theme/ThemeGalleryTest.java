package de.neitzel.fx.example.theme;

import static org.junit.jupiter.api.Assertions.*;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.stage.Stage;

import org.junit.jupiter.api.*;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import javax.imageio.ImageIO;

class ThemeGalleryTest {
    private static final java.util.Queue<Throwable> uncaught =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static Thread.UncaughtExceptionHandler previousHandler;

    @BeforeEach
    void captureFxErrors() throws Exception {
        fx(
                () -> {
                    previousHandler = Thread.currentThread().getUncaughtExceptionHandler();
                    Thread.currentThread()
                            .setUncaughtExceptionHandler((thread, error) -> uncaught.add(error));
                });
    }

    @AfterEach
    void verifyFxErrors() throws Exception {
        fx(() -> Thread.currentThread().setUncaughtExceptionHandler(previousHandler));
        var errors = new java.util.ArrayList<>(uncaught);
        uncaught.clear();
        assertTrue(errors.isEmpty(), "Uncaught FX errors: " + errors);
    }

    @BeforeAll
    static void start() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException e) {
            ready.countDown();
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        fx(() -> Platform.setImplicitExit(false));
    }

    static void fx(Runnable code) throws Exception {
        var task = new FutureTask<Void>(code, null);
        Platform.runLater(task);
        task.get(15, TimeUnit.SECONDS);
    }

    static double luminance(Color c) {
        return .2126 * linear(c.getRed())
                + .7152 * linear(c.getGreen())
                + .0722 * linear(c.getBlue());
    }

    static double linear(double c) {
        return c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4);
    }

    static double contrast(Color a, Color b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + .05) / (Math.min(x, y) + .05);
    }

    static Color background(Node node) {
        for (Node current = node; current != null; current = current.getParent())
            if (current instanceof Region r && r.getBackground() != null)
                for (int i = r.getBackground().getFills().size() - 1; i >= 0; i--) {
                    Paint p = r.getBackground().getFills().get(i).getFill();
                    if (p instanceof Color c && c.getOpacity() == 1) return c;
                }
        throw new AssertionError("No opaque resolved background for " + node);
    }

    static void measure(
            String theme,
            String name,
            Paint foreground,
            Node node,
            double target,
            List<String> report) {
        assertInstanceOf(Color.class, foreground);
        Color fg = (Color) foreground, bg = background(node);
        double ratio = contrast(fg, bg);
        report.add(
                theme
                        + " | "
                        + name
                        + " | "
                        + fg
                        + " | "
                        + bg
                        + " | "
                        + String.format(Locale.ROOT, "%.2f", ratio));
        assertTrue(ratio >= target, theme + " " + name + " contrast " + ratio + " < " + target);
    }

    @Test
    void resolvedStateContrastStableGeometryAndThemeSwitchingPreserveGridState() throws Exception {
        var ref = new AtomicReference<ThemeGallery>();
        var window = new AtomicReference<Stage>();
        fx(
                () -> {
                    var gallery = new ThemeGallery();
                    ref.set(gallery);
                    Stage stage = new Stage();
                    window.set(stage);
                    Scene scene = new Scene(gallery, 920, 900);
                    gallery.install(scene);
                    stage.setScene(scene);
                    stage.show();
                });
        for (int i = 0; i < 5; i++) fx(() -> {});
        List<String> report = new ArrayList<>();
        try {
            fx(
                    () -> {
                        var g = ref.get();
                        var selected = g.grid.selectedKeyProperty().get();
                        var query = g.grid.stateProperty().get().displayed();
                        g.draft.setText("Unfinished draft");
                        g.grid.setColumnWidth(
                                "name", new de.neitzel.grid.ColumnWidth.Explicit(475));
                        double[] normalSize = new double[2];
                        for (String theme : List.of("light", "dark", "high-contrast")) {
                            assertTrue(g.themes.select(theme));
                            g.hovered.pseudoClassStateChanged(
                                    PseudoClass.getPseudoClass("hover"), true);
                            g.focused.pseudoClassStateChanged(
                                    PseudoClass.getPseudoClass("focused"), true);
                            g.applyCss();
                            g.layout();
                            double target = theme.equals("high-contrast") ? 7 : 4.5;
                            for (var button : List.of(g.normal, g.hovered, g.focused, g.disabled))
                                measure(
                                        theme,
                                        button.getText(),
                                        button.getTextFill(),
                                        button,
                                        target,
                                        report);
                            measure(
                                    theme,
                                    "invalid",
                                    g.invalid.lookupAll(".text").stream()
                                            .filter(
                                                    n ->
                                                            n instanceof javafx.scene.text.Text t
                                                                    && t.getText()
                                                                            .equals(
                                                                                    g.invalid
                                                                                            .getText()))
                                            .map(n -> ((javafx.scene.text.Text) n).getFill())
                                            .findFirst()
                                            .orElseThrow(),
                                    g.invalid,
                                    target,
                                    report);
                            measure(theme, "error", g.error.getTextFill(), g.error, target, report);
                            measure(
                                    theme,
                                    "warning",
                                    g.warning.getTextFill(),
                                    g.warning,
                                    target,
                                    report);
                            var focus =
                                    (Color)
                                            g.focused
                                                    .getBorder()
                                                    .getStrokes()
                                                    .getFirst()
                                                    .getTopStroke();
                            double focusRatio = contrast(focus, background(g.focused));
                            report.add(
                                    theme
                                            + " | focus border | "
                                            + focus
                                            + " | "
                                            + background(g.focused)
                                            + " | "
                                            + focusRatio);
                            assertTrue(focusRatio >= 3);
                            var box = (Region) g.lookup(".check-box .box");
                            measure(
                                    theme,
                                    "checkbox boundary",
                                    box.getBorder().getStrokes().getFirst().getTopStroke(),
                                    box,
                                    3,
                                    report);
                            var icon = (javafx.scene.shape.SVGPath) g.grid.lookup(".filter-icon");
                            measure(theme, "filter icon", icon.getFill(), icon, 3, report);
                            var filterButton = (Button) g.grid.lookup(".filter-button");
                            assertTrue(filterButton.getAccessibleText().contains("active"));
                            assertFalse(filterButton.getText().isBlank());
                            var cells =
                                    g.standard.lookupAll(".table-cell").stream()
                                            .filter(
                                                    n ->
                                                            n instanceof TableCell<?, ?> cell
                                                                    && cell.getText() != null
                                                                    && cell.getText()
                                                                            .startsWith("REMOVE"))
                                            .toList();
                            assertEquals(1, cells.size());
                            var cell = (TableCell<?, ?>) cells.getFirst();
                            measure(
                                    theme,
                                    "selected TableView",
                                    cell.getTextFill(),
                                    cell,
                                    target,
                                    report);
                            var selectedLabel =
                                    (Label) g.grid.lookup(".result-row:selected .label");
                            assertNotNull(selectedLabel);
                            measure(
                                    theme,
                                    "selected DataGrid",
                                    selectedLabel.getTextFill(),
                                    selectedLabel,
                                    target,
                                    report);
                            selectedLabel.getParent().setDisable(true);
                            g.applyCss();
                            measure(
                                    theme,
                                    "selected disabled row",
                                    selectedLabel.getTextFill(),
                                    selectedLabel,
                                    target,
                                    report);
                            selectedLabel.getParent().setDisable(false);
                            g.applyCss();
                            assertEquals(selected, g.grid.selectedKeyProperty().get());
                            assertEquals(query, g.grid.stateProperty().get().displayed());
                            assertEquals(
                                    475, ((Region) g.grid.lookup(".column-cell")).getWidth(), .01);
                            assertEquals("Unfinished draft", g.draft.getText());
                            if (theme.equals("light")) {
                                normalSize[0] = g.normal.getWidth();
                                normalSize[1] = g.normal.getHeight();
                            } else {
                                assertEquals(normalSize[0], g.normal.getWidth(), .01);
                                assertEquals(normalSize[1], g.normal.getHeight(), .01);
                            }
                            try {
                                Path output = Path.of("target/theme-gallery");
                                Files.createDirectories(output);
                                WritableImage shot = g.snapshot(null, null);
                                BufferedImage image =
                                        new BufferedImage(
                                                (int) shot.getWidth(),
                                                (int) shot.getHeight(),
                                                BufferedImage.TYPE_INT_ARGB);
                                for (int y = 0; y < image.getHeight(); y++)
                                    for (int x = 0; x < image.getWidth(); x++)
                                        image.setRGB(x, y, shot.getPixelReader().getArgb(x, y));
                                ImageIO.write(
                                        image, "png", output.resolve(theme + ".png").toFile());
                            } catch (java.io.IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
        } finally {
            fx(
                    () -> {
                        ref.get().close();
                        window.get().close();
                    });
            Path output = Path.of("target/theme-gallery");
            Files.createDirectories(output);
            Files.write(output.resolve("contrast.txt"), report);
        }
    }

    @Test
    void popupDialogAndFloatingWindowResolvedTextMeetThemeTargets() throws Exception {
        List<String> report = new ArrayList<>();
        fx(
                () -> {
                    var manager = new de.neitzel.fx.theme.ThemeManager();
                    Stage owner = new Stage();
                    Button opener = new Button("Open filter");
                    owner.setScene(new Scene(new VBox(opener), 500, 400));
                    manager.register(owner.getScene());
                    owner.show();
                    var editor =
                            de.neitzel.fx.grid.filter.BuiltInFilterEditor.load(
                                    new de.neitzel.fx.grid.filter.FilterEditorContext(
                                            "x",
                                            de.neitzel.fx.grid.filter.FilterColumn.text(),
                                            new de.neitzel.grid.TextFilter(
                                                    "x",
                                                    de.neitzel.grid.TextFilter.Operator.EQUALS,
                                                    "Typed draft",
                                                    false),
                                            List.of(),
                                            de.neitzel.fx.grid.filter.FilterColumn.Checklist
                                                    .DISABLED));
                    var popup =
                            new de.neitzel.fx.grid.filter.FilterPopup(
                                    opener,
                                    editor,
                                    de.neitzel.fx.grid.filter.FilterPopup.Options.defaults(),
                                    c -> {},
                                    c -> {});
                    popup.show();
                    try {
                        for (String theme : List.of("light", "dark", "high-contrast")) {
                            manager.select(theme);
                            editor.view().applyCss();
                            editor.view().layout();
                            var input = (TextField) editor.view().lookup("#operand");
                            var text =
                                    input.lookupAll(".text").stream()
                                            .filter(
                                                    n ->
                                                            n instanceof javafx.scene.text.Text t
                                                                    && t.getText()
                                                                            .equals("Typed draft"))
                                            .map(n -> (javafx.scene.text.Text) n)
                                            .findFirst()
                                            .orElseThrow();
                            measure(
                                    theme,
                                    "popup draft",
                                    text.getFill(),
                                    input,
                                    theme.equals("high-contrast") ? 7 : 4.5,
                                    report);
                            assertTrue(popup.isOpen());
                            assertEquals("Typed draft", input.getText());
                            snapshot(editor.view().getScene().getRoot(), theme + "-popup");
                        }
                        popup.close();
                        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
                        dialog.initOwner(owner);
                        Label content = new Label("Dialog content remains readable");
                        dialog.getDialogPane().setContent(content);
                        dialog.show();
                        try {
                            for (String theme : List.of("light", "dark", "high-contrast")) {
                                manager.select(theme);
                                dialog.getDialogPane().applyCss();
                                dialog.getDialogPane().layout();
                                measure(
                                        theme,
                                        "dialog",
                                        content.getTextFill(),
                                        content,
                                        theme.equals("high-contrast") ? 7 : 4.5,
                                        report);
                            }
                        } finally {
                            dialog.close();
                        }
                        Stage floating = new Stage();
                        floating.initOwner(owner);
                        Label label = new Label("Floating editor");
                        floating.setScene(
                                new Scene(
                                        new VBox(label, new TextField("Unfinished edit")),
                                        400,
                                        150));
                        floating.show();
                        try {
                            for (String theme : List.of("light", "dark", "high-contrast")) {
                                manager.select(theme);
                                floating.getScene().getRoot().applyCss();
                                measure(
                                        theme,
                                        "floating",
                                        label.getTextFill(),
                                        label,
                                        theme.equals("high-contrast") ? 7 : 4.5,
                                        report);
                            }
                        } finally {
                            floating.close();
                        }
                    } finally {
                        popup.close();
                        owner.close();
                        manager.close();
                    }
                });
        Path output = Path.of("target/theme-gallery");
        Files.createDirectories(output);
        Files.write(output.resolve("window-contrast.txt"), report);
    }

    static void snapshot(Parent root, String name) {
        try {
            Path output = Path.of("target/theme-gallery");
            Files.createDirectories(output);
            WritableImage shot = root.snapshot(null, null);
            BufferedImage image =
                    new BufferedImage(
                            (int) shot.getWidth(),
                            (int) shot.getHeight(),
                            BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < image.getHeight(); y++)
                for (int x = 0; x < image.getWidth(); x++)
                    image.setRGB(x, y, shot.getPixelReader().getArgb(x, y));
            ImageIO.write(image, "png", output.resolve(name + ".png").toFile());
        } catch (java.io.IOException error) {
            throw new RuntimeException(error);
        }
    }
}
