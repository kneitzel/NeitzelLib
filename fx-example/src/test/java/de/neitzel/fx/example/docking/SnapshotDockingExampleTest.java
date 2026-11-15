package de.neitzel.fx.example.docking;

import de.neitzel.fx.docking.DockGroupMode;
import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockableResolution;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import de.neitzel.fx.docking.LayoutSnapshot;
import de.neitzel.fx.docking.RestoreResult;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.Screen;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Loads the snapshot application's real FXML before restoring fresh content into an empty manager. */
class SnapshotDockingExampleTest {
    @Test
    void fxmlRoundTripRestoresNestedStructureAndKeepsTheOverlayCollapsedBeforeShow() throws Exception {
        onFx(() -> {
            Platform.setImplicitExit(false);
            Fixture source = fixture();
            for (String area : List.of("primary", "secondary", "preview", "tools")) {
                Dockable dockable = new Dockable(new DockableId(area), new TextArea("Original " + area));
                source.manager().open(dockable, DockTarget.area(area));
            }
            LayoutSnapshot snapshot = source.manager().snapshot();
            LayoutSnapshot.Split center = assertInstanceOf(LayoutSnapshot.Split.class,
                    snapshot.windows().getFirst().roots().getFirst().branch());
            assertInstanceOf(LayoutSnapshot.Split.class, center.second());
            Fixture target = fixture();
            var result = target.manager().restore(snapshot, id ->
                    new DockableResolution.Available(new Dockable(id, new TextArea("Reloaded " + id.value()))));
            assertInstanceOf(RestoreResult.Applied.class, result);
            assertFalse(target.stage().isShowing(), "The application restores before showing the new main window");
            assertEquals(4, target.manager().layout().windows().getFirst().areas().stream()
                    .flatMap(area -> area.groups().stream()).distinct().mapToLong(group -> group.dockableIds().size()).sum());
            var tools = target.manager().layout().windows().getFirst().areas().stream()
                    .filter(area -> area.id().value().equals("tools")).findFirst().orElseThrow().groups().getFirst();
            assertEquals(DockGroupMode.OVERLAY, tools.mode());
            assertTrue(tools.collapsed());
            TextArea original = (TextArea) source.manager().findDockable(new DockableId("primary")).orElseThrow().getContent();
            TextArea restored = (TextArea) target.manager().findDockable(new DockableId("primary")).orElseThrow().getContent();
            assertNotSame(original, restored);
            assertEquals("Reloaded primary", restored.getText(), "Document loading belongs to the resolver, not the layout snapshot");
        });
    }

    private static Fixture fixture() throws Exception {
        var resource = DockingApp.class.getResource("SnapshotDocking.fxml");
        assertNotNull(resource, "The snapshot example supplies its real FXML layout");
        DockPane pane = FXMLLoader.load(resource);
        Stage stage = new Stage();
        stage.setScene(new Scene(pane, 1000, 650));
        return new Fixture(stage, DockingManager.attach(stage, pane, new DockingOptions()));
    }

    @Test
    void applicationButtonsShowFreshRestoreFallbackAndSeparateCleanupDiagnostics() throws Exception {
        onFx(() -> {
            Platform.setImplicitExit(false);
            List<Window> existing = List.copyOf(Window.getWindows());
            Stage source = new Stage();
            try {
                new SnapshotDockingApp().start(source);
                Parent root = source.getScene().getRoot();
                root.applyCss();
                root.layout();
                Label status = (Label) root.lookup("#snapshotStatus");
                button(root, "Capture layout").fire();
                button(root, "Restore saved layout").fire();
                assertTrue(status.getText().contains("prepared 4 fresh contents"), status.getText());
                Stage restored = Window.getWindows().stream().filter(Stage.class::isInstance).map(Stage.class::cast)
                        .filter(stage -> stage.getTitle().equals("Restored snapshot")).findFirst().orElseThrow();
                assertEquals(4, tabCount(restored.getScene().getRoot()));
                assertEquals(4, tabCount(root), "Restoring another workspace leaves the original editor Nodes in place");

                button(root, "Try incompatible version").fire();
                assertTrue(status.getText().contains("UNSUPPORTED_SNAPSHOT_VERSION"), status.getText());
                assertTrue(status.getText().contains("prepared 0, discarded 0"), status.getText());
                button(root, "Simulate load failure").fire();
                assertTrue(status.getText().contains("DOCKABLE_LOAD_FAILED"), status.getText());
                assertTrue(status.getText().contains("prepared 1, discarded 1, cleanup diagnostics 0"), status.getText());
                ((CheckBox) root.lookup(".check-box")).setSelected(true);
                button(root, "Simulate load failure").fire();
                assertTrue(status.getText().contains("prepared 1, discarded 1, cleanup diagnostics 1"), status.getText());
                assertTrue(status.getText().contains("Demo document source is unavailable"), status.getText());
                Window.getWindows().stream().filter(Stage.class::isInstance).map(Stage.class::cast)
                        .filter(stage -> stage.getTitle().equals("Unchanged fallback layout"))
                        .forEach(stage -> assertEquals(0, tabCount(stage.getScene().getRoot())));
            } finally {
                List.copyOf(Window.getWindows()).stream().filter(window -> !existing.contains(window)).forEach(Window::hide);
            }
        });
    }

    private static int tabCount(Parent root) {
        return root.lookupAll(".tab-pane").stream().map(TabPane.class::cast).mapToInt(tabs -> tabs.getTabs().size()).sum();
    }

    @Test
    void partialRestoreShowsMissingIdsNamedFallbackAndAtomicPlacementRejection() throws Exception {
        onFx(() -> {
            Platform.setImplicitExit(false);
            List<Window> existing = List.copyOf(Window.getWindows());
            Stage source = new Stage();
            try {
                new SnapshotDockingApp().start(source);
                Parent root = source.getScene().getRoot();
                root.applyCss();
                root.layout();
                Label status = (Label) root.lookup("#snapshotStatus");
                TextArea notes = root.lookupAll(".tab-pane").stream().map(TabPane.class::cast)
                        .flatMap(tabs -> tabs.getTabs().stream()).filter(tab -> tab.getText().equals("Notes"))
                        .map(tab -> (TextArea) tab.getContent()).findFirst().orElseThrow();
                notes.setText("Unsaved original remains open");
                button(root, "Restore without Preview/Project").fire();
                assertTrue(status.getText().contains("missing 2"), status.getText());
                assertTrue(status.getText().contains("preview") && status.getText().contains("project"), status.getText());
                Stage partial = latestResultWindow(source, existing);
                assertEquals(2, tabCount(partial.getScene().getRoot()));
                assertEquals(3, partial.getScene().getRoot().lookupAll(".tab-pane").size(),
                        "The persistent empty tools group remains beside the two populated editor groups");
                assertEquals(1, partial.getScene().getRoot().lookupAll(".dock-split").size(),
                        "The missing Preview dissolves the nested split");
                partial.hide();

                button(root, "Restore with changed rule").fire();
                Stage fallback = latestResultWindow(source, existing);
                assertTrue(status.getText().contains("editor fallback (primary)"), status.getText());
                assertTrue(fallback.getScene().getRoot().lookupAll(".tab-pane").stream().map(TabPane.class::cast)
                        .anyMatch(tabs -> tabs.getTabs().stream().anyMatch(tab -> tab.getText().equals("Notes"))
                                && tabs.getTabs().stream().anyMatch(tab -> tab.getText().equals("Project"))));
                fallback.hide();

                button(root, "Reject Project placement").fire();
                Stage rejected = latestResultWindow(source, existing);
                assertEquals(0, tabCount(rejected.getScene().getRoot()));
                assertTrue(status.getText().contains("PLACEMENT_REJECTED"), status.getText());
                assertTrue(status.getText().contains("project"), status.getText());
                assertTrue(status.getText().contains("prepared 4, discarded 4"), status.getText());
                assertEquals("Unsaved original remains open", notes.getText());
                assertEquals(4, tabCount(root));
            } finally {
                List.copyOf(Window.getWindows()).stream().filter(window -> !existing.contains(window)).forEach(Window::hide);
            }
        });
    }

    private static Stage latestResultWindow(Stage source, List<Window> existing) {
        return Window.getWindows().stream().filter(window -> window != source && !existing.contains(window))
                .filter(Stage.class::isInstance).map(Stage.class::cast).reduce((first, second) -> second).orElseThrow();
    }

    @Test
    void floatingStartupFitsDisplaysReturnsProjectAndOmitsOrAbortsPreparedHosts() throws Exception {
        onFx(() -> {
            Platform.setImplicitExit(false);
            List<Window> existing = List.copyOf(Window.getWindows());
            Stage source = new Stage();
            try {
                new SnapshotDockingApp().start(source);
                Parent root = source.getScene().getRoot();
                root.applyCss();
                root.layout();
                Label status = (Label) root.lookup("#snapshotStatus");
                assertTrue(button(root, "Restore off-screen windows").isDisabled());
                assertTrue(button(root, "Abort floating customizer").isDisabled());
                button(root, "Prepare floating snapshot").fire();
                assertTrue(status.getText().contains("2 floating windows"), status.getText());
                assertFalse(root.lookupAll(".tab-pane").stream().map(TabPane.class::cast)
                        .filter(tabs -> tabs.getTabs().isEmpty()).findFirst().orElseThrow().isVisible(),
                        "The persistent Project return target is hidden while empty");
                List<Window> beforeOrdinaryRestore = List.copyOf(Window.getWindows());
                button(root, "Restore saved layout").fire();
                List<Stage> ordinary = newStages(beforeOrdinaryRestore);
                assertEquals(3, ordinary.size());
                assertTrue(status.getText().contains("Floating windows before main show: 0; now: 2"), status.getText());
                ordinary.forEach(Window::hide);
                List<Window> beforePartial = List.copyOf(Window.getWindows());
                button(root, "Restore partial floating").fire();
                List<Stage> partial = newStages(beforePartial);
                assertEquals(1, partial.size(), "Missing Preview and main-only Project leave no floating hosts");
                assertEquals(3, tabCount(partial.getFirst().getScene().getRoot()));
                assertTrue(status.getText().contains("missing 1 [preview]"), status.getText());
                assertSame(partial.getFirst(), document(partial.getFirst().getScene().getRoot(), "Project").getScene().getWindow());
                partial.forEach(Window::hide);

                List<Window> beforeRestore = List.copyOf(Window.getWindows());
                button(root, "Restore off-screen windows").fire();
                List<Stage> restored = newStages(beforeRestore);
                assertEquals(3, restored.size(), "One main window and two floating hosts appear after startup");
                Stage main = restored.stream().filter(stage -> stage.getTitle().equals("Restored snapshot")).findFirst().orElseThrow();
                assertTrue(status.getText().contains("Floating windows before main show: 0; now: 2"), status.getText());
                for (Stage floating : restored.stream().filter(stage -> stage != main).toList()) {
                    assertEquals("Snapshot floating content", floating.getTitle());
                    assertTrue(Screen.getScreens().stream().anyMatch(screen -> {
                        var bounds = screen.getVisualBounds();
                        return floating.getX() >= bounds.getMinX() - 2 && floating.getY() >= bounds.getMinY() - 2
                                && floating.getX() + floating.getWidth() <= bounds.getMaxX() + 2
                                && floating.getY() + floating.getHeight() <= bounds.getMaxY() + 2;
                    }), "The restored floating frame must fit an available display");
                }
                Stage projectHost = restored.stream().filter(stage -> stage != main)
                        .filter(stage -> document(stage.getScene().getRoot(), "Project") != null).findFirst().orElseThrow();
                TextArea project = document(projectHost.getScene().getRoot(), "Project");
                button(main.getScene().getRoot(), "Return Project").fire();
                assertSame(main, project.getScene().getWindow());
                assertFalse(projectHost.isShowing());
                assertTrue(((Label) main.getScene().getRoot().lookup("#snapshotStatus")).getText().contains("preserved main-window target"));
                restored.forEach(Window::hide);

                List<Window> beforeFailure = List.copyOf(Window.getWindows());
                button(root, "Abort floating customizer").fire();
                List<Stage> failed = newStages(beforeFailure);
                assertEquals(1, failed.size(), "Neither the first prepared floating host nor the failed second one may appear");
                assertEquals(0, tabCount(failed.getFirst().getScene().getRoot()));
                assertTrue(status.getText().contains("WINDOW_CREATION_FAILED"), status.getText());
                assertTrue(status.getText().contains("prepared 4, discarded 4"), status.getText());
                assertTrue(status.getText().contains("Floating windows before main show: 0; now: 0"), status.getText());
            } finally {
                List.copyOf(Window.getWindows()).stream().filter(window -> !existing.contains(window)).forEach(Window::hide);
            }
        });
    }

    private static List<Stage> newStages(List<Window> existing) {
        return Window.getWindows().stream().filter(window -> !existing.contains(window))
                .filter(Stage.class::isInstance).map(Stage.class::cast).toList();
    }

    private static TextArea document(Parent root, String title) {
        return root.lookupAll(".tab-pane").stream().map(TabPane.class::cast).flatMap(tabs -> tabs.getTabs().stream())
                .filter(tab -> tab.getText().equals(title)).map(tab -> (TextArea) tab.getContent()).findFirst().orElse(null);
    }

    private static Button button(Parent root, String label) {
        return root.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getText().equals(label)).findFirst().orElseThrow();
    }

    private static void onFx(FxAction action) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); }
        catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try { action.run(); }
            catch (Throwable thrown) { failure.set(thrown); }
            finally { done.countDown(); }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) fail("Snapshot docking example failed", failure.get());
    }

    private record Fixture(Stage stage, DockingManager manager) { }
    @FunctionalInterface private interface FxAction { void run() throws Exception; }
}
