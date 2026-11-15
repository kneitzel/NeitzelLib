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
