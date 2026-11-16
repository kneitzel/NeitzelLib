package de.neitzel.fx.view;

import static org.junit.jupiter.api.Assertions.*;

import de.neitzel.fx.docking.*;
import de.neitzel.fx.grid.filter.*;
import de.neitzel.fx.theme.*;
import de.neitzel.grid.*;

import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

import org.junit.jupiter.api.*;

import java.util.*;

class ThemeManagerTest {
    private static final java.util.Queue<Throwable> uncaught =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static Thread.UncaughtExceptionHandler previousHandler;

    @BeforeEach
    void captureFxErrors() throws Exception {
        DataGridTest.fx(
                () -> {
                    previousHandler = Thread.currentThread().getUncaughtExceptionHandler();
                    Thread.currentThread()
                            .setUncaughtExceptionHandler((thread, error) -> uncaught.add(error));
                });
    }

    @AfterEach
    void verifyFxErrors() throws Exception {
        DataGridTest.fx(() -> Thread.currentThread().setUncaughtExceptionHandler(previousHandler));
        var errors = new java.util.ArrayList<>(uncaught);
        uncaught.clear();
        assertTrue(errors.isEmpty(), "Uncaught FX errors: " + errors);
    }

    @BeforeAll
    static void start() throws Exception {
        DataGridTest.start();
        DataGridTest.fx(() -> Platform.setImplicitExit(false));
    }

    @Test
    void optInSwitchPreservesApplicationStylesAndCloseRemovesOnlyOwnedStyles() throws Exception {
        DataGridTest.fx(
                () -> {
                    Button button = new Button("Unchanged unless opted in");
                    Scene scene = new Scene(new VBox(button));
                    String app =
                            getClass().getResource("shared-theme-override.css").toExternalForm();
                    scene.getStylesheets().add(app);
                    assertFalse(scene.getRoot().getStyleClass().contains("neitzel-theme"));
                    var manager = new ThemeManager();
                    var registration = manager.register(scene);
                    assertEquals(app, scene.getStylesheets().getLast());
                    for (String id : List.of("dark", "high-contrast", "light")) {
                        assertTrue(manager.select(id));
                        scene.getRoot().applyCss();
                        assertEquals(app, scene.getStylesheets().getLast());
                        assertEquals(3, scene.getStylesheets().size());
                        assertEquals(
                                9,
                                button.getBackground()
                                        .getFills()
                                        .getFirst()
                                        .getRadii()
                                        .getTopLeftHorizontalRadius(),
                                0.01);
                    }
                    var old = scene.getRoot();
                    scene.setRoot(new VBox(new Label("Replacement root")));
                    assertFalse(old.getStyleClass().contains("neitzel-theme"));
                    assertTrue(scene.getRoot().getStyleClass().contains("neitzel-theme"));
                    registration.close();
                    assertEquals(List.of(app), scene.getStylesheets());
                    assertFalse(scene.getRoot().getStyleClass().contains("neitzel-theme"));
                    manager.close();
                    manager.close();
                });
    }

    @Test
    void missingThemeAndDetectablePartialInstallationRollBackEveryScene() throws Exception {
        DataGridTest.fx(
                () -> {
                    var manager = new ThemeManager();
                    Scene one = new Scene(new VBox()), two = new Scene(new VBox());
                    manager.register(one);
                    manager.register(two);
                    manager.select("dark");
                    var oldOne = List.copyOf(one.getStylesheets());
                    var oldTwo = List.copyOf(two.getStylesheets());
                    manager.addTheme(
                            new Theme("missing", List.of("file:/missing/neitzel-theme.css")));
                    assertFalse(manager.select("missing"));
                    assertEquals("dark", manager.selectedThemeProperty().get().id());
                    assertEquals(oldOne, one.getStylesheets());
                    manager.setInstallationCheck(
                            s -> {
                                if (s == two)
                                    throw new IllegalArgumentException(
                                            "Application CSS check failed");
                            });
                    assertFalse(manager.select("light"));
                    assertEquals(oldOne, one.getStylesheets());
                    assertEquals(oldTwo, two.getStylesheets());
                    assertEquals("dark", manager.selectedThemeProperty().get().id());
                    assertTrue(manager.failureProperty().get().message().contains("retained dark"));
                    manager.setInstallationCheck(s -> {});
                    assertFalse(manager.selectSaved("deleted-user-theme"));
                    assertEquals("light", manager.selectedThemeProperty().get().id());
                    assertTrue(manager.failureProperty().get().message().contains("using light"));
                    manager.close();
                });
    }

    @Test
    void filterDraftNestedChooserDialogAndFutureOwnedWindowFollowLiveTheme() throws Exception {
        DataGridTest.fx(
                () -> {
                    Stage stage = new Stage();
                    Button opener = new Button("Filter");
                    stage.setScene(new Scene(new VBox(opener), 500, 400));
                    var manager = new ThemeManager();
                    manager.register(stage.getScene());
                    stage.show();
                    var editor =
                            BuiltInFilterEditor.load(
                                    new FilterEditorContext(
                                            "x",
                                            FilterColumn.text(),
                                            new TextFilter(
                                                    "x",
                                                    TextFilter.Operator.EQUALS,
                                                    "unfinished",
                                                    false),
                                            List.of(),
                                            FilterColumn.Checklist.DISABLED));
                    var popup =
                            new FilterPopup(
                                    opener,
                                    editor,
                                    FilterPopup.Options.defaults(),
                                    c -> {},
                                    c -> {});
                    popup.show();
                    Alert alert =
                            new Alert(Alert.AlertType.INFORMATION, "Owned dialog", ButtonType.OK);
                    alert.initOwner(stage);
                    try {
                        manager.select("dark");
                        assertTrue(popup.isOpen());
                        assertEquals(
                                "unfinished",
                                ((TextField) editor.view().lookup("#operand")).getText());
                        assertTrue(
                                editor.view()
                                        .getScene()
                                        .getStylesheets()
                                        .containsAll(Theme.dark().stylesheets()));
                        assertFalse(
                                editor.view()
                                        .getStylesheets()
                                        .contains(Theme.light().stylesheets().getLast()));
                        ComboBox<?> chooser = (ComboBox<?>) editor.view().lookup("#operator");
                        chooser.show();
                        var nested =
                                Window.getWindows().stream()
                                        .filter(
                                                w ->
                                                        w instanceof PopupWindow p
                                                                && p.getOwnerWindow()
                                                                        == editor.view()
                                                                                .getScene()
                                                                                .getWindow())
                                        .toList();
                        assertFalse(nested.isEmpty());
                        manager.select("high-contrast");
                        for (var w : nested)
                            assertTrue(
                                    w.getScene()
                                            .getStylesheets()
                                            .containsAll(Theme.highContrast().stylesheets()));
                        chooser.hide();
                        popup.close();
                        alert.show();
                        assertTrue(
                                alert.getDialogPane()
                                        .getScene()
                                        .getStylesheets()
                                        .containsAll(Theme.highContrast().stylesheets()));
                        manager.select("dark");
                        assertTrue(
                                alert.getDialogPane()
                                        .getScene()
                                        .getStylesheets()
                                        .containsAll(Theme.dark().stylesheets()));
                        manager.select("high-contrast");
                        Stage future = new Stage();
                        future.initOwner(stage);
                        future.setScene(new Scene(new VBox(new Label("Future"))));
                        future.show();
                        assertTrue(
                                future.getScene()
                                        .getStylesheets()
                                        .containsAll(Theme.highContrast().stylesheets()));
                        future.close();
                        assertFalse(
                                future.getScene()
                                        .getStylesheets()
                                        .contains(Theme.highContrast().stylesheets().getLast()));
                    } finally {
                        alert.close();
                        popup.close();
                        stage.close();
                        manager.close();
                    }
                });
    }

    @Test
    void dockingFloatingWindowsInheritWithoutChangingWindowOwnership() throws Exception {
        DataGridTest.fx(
                () -> {
                    Stage stage = new Stage();
                    DockPane pane = new DockPane();
                    pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
                    stage.setScene(new Scene(pane, 600, 400));
                    var themes = new ThemeManager();
                    themes.register(stage.getScene());
                    themes.select("dark");
                    var docking = DockingManager.attach(stage, pane, new DockingOptions());
                    Dockable item =
                            new Dockable(new DockableId("example"), new TextArea("Keep this edit"));
                    docking.open(item, DockTarget.area("editors"));
                    stage.show();
                    try {
                        docking.move(item.getId(), DockTarget.floating());
                        Scene floating = item.getContent().getScene();
                        assertTrue(
                                floating.getStylesheets().containsAll(Theme.dark().stylesheets()));
                        themes.select("high-contrast");
                        assertTrue(
                                floating.getStylesheets()
                                        .containsAll(Theme.highContrast().stylesheets()));
                        assertEquals("Keep this edit", ((TextArea) item.getContent()).getText());
                    } finally {
                        for (var w : List.copyOf(Window.getWindows()))
                            if (w != stage
                                    && w.getScene() != null
                                    && w.getScene()
                                            .getStylesheets()
                                            .contains(Theme.highContrast().stylesheets().getLast()))
                                w.hide();
                        stage.hide();
                        themes.close();
                    }
                });
    }
}
