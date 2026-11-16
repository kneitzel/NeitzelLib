package de.neitzel.fx.example.docking;

import de.neitzel.fx.docking.ActionResult;
import de.neitzel.fx.docking.DockGroupChanges;
import de.neitzel.fx.docking.DockGroupId;
import de.neitzel.fx.docking.DockGroupMode;
import de.neitzel.fx.docking.DockLayoutView;
import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Loads the runnable example's actual FXML through the standalone public API. */
class OverlayDockingExampleTest {
    @Test
    void fxmlOverlaysArbitrateAndPinWithoutMovingTheirLiveContents() throws Exception {
        onFx(() -> {
            var resource = DockingApp.class.getResource("OverlayDocking.fxml");
            assertNotNull(resource, "The runnable overlay example supplies its FXML layout");
            DockPane pane = FXMLLoader.load(resource);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 920, 540));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            assertEquals(DockGroupMode.DOCKED, group(manager, "editors").mode());
            assertTrue(group(manager, "project").collapsed());
            assertTrue(group(manager, "changes").collapsed());
            Dockable editor = new Dockable(new DockableId("notes"), new TextArea("Unsaved notes"));
            Dockable project = new Dockable(new DockableId("project"), new TextArea("Project"));
            Dockable changes = new Dockable(new DockableId("changes"), new TextArea("Changes"));
            manager.open(editor, DockTarget.area("editors"));
            manager.open(project, DockTarget.area("project"));
            manager.open(changes, DockTarget.area("changes"));
            assertTrue(group(manager, "project").collapsed());
            assertFalse(group(manager, "changes").collapsed());
            assertEquals(240, area(manager, "project").preferredWidth(), 1);
            assertEquals(260, area(manager, "changes").preferredWidth(), 1);
            manager.activate(project.getId());
            assertFalse(group(manager, "project").collapsed());
            assertTrue(group(manager, "changes").collapsed());
            assertInstanceOf(ActionResult.Applied.class, manager.updateGroup(new DockGroupId("project"),
                    DockGroupChanges.builder().mode(DockGroupMode.DOCKED).build()));
            manager.activate(changes.getId());
            assertFalse(group(manager, "project").collapsed(), "A pinned group stays open beside an overlay");
            assertEquals(DockGroupMode.DOCKED, group(manager, "project").mode());
            assertEquals(DockGroupMode.OVERLAY, group(manager, "changes").mode());
            assertSame(project, manager.findDockable(project.getId()).orElseThrow());
            assertSame(stage.getScene(), project.getContent().getScene());
            assertEquals("Unsaved notes", ((TextArea) editor.getContent()).getText());
        });
    }

    @Test
    void theToolbarPinCommandRevealsItsGroupAfterTheOutsidePressDismissesTheOverlay() throws Exception {
        onFx(() -> {
            Platform.setImplicitExit(false);
            Stage stage = new Stage();
            try {
                new OverlayDockingApp().start(stage);
                var root = stage.getScene().getRoot();
                root.applyCss();
                root.layout();
                button(root, "Show Project").fire();
                var projectTabs = root.lookupAll(".tab-pane").stream()
                        .map(javafx.scene.control.TabPane.class::cast)
                        .filter(tabs -> tabs.getTabs().stream().anyMatch(tab -> tab.getText().equals("Project")))
                        .findFirst().orElseThrow();
                var content = projectTabs.getSelectionModel().getSelectedItem().getContent();
                assertTrue(projectTabs.lookup(".tab-content-area").isVisible());
                var pin = button(root, "Pin Project");
                press(pin);
                pin.fire();
                root.applyCss();
                root.layout();
                assertTrue(projectTabs.lookup(".tab-content-area").isVisible(),
                        "Pin Project opens and pins the group even after its toolbar press dismissed the overlay");
                assertEquals("Unpin Project", pin.getText());
                assertEquals(240, projectTabs.getWidth(), 1);
                button(root, "Show Changes").fire();
                assertTrue(projectTabs.lookup(".tab-content-area").isVisible());
                press(pin);
                pin.fire();
                assertEquals("Pin Project", pin.getText());
                assertTrue(projectTabs.lookup(".tab-content-area").isVisible());
                assertSame(content, projectTabs.getSelectionModel().getSelectedItem().getContent());
                assertEquals(240, projectTabs.getWidth(), 1);
            } finally { stage.hide(); }
        });
    }

    private static javafx.scene.control.Button button(javafx.scene.Parent root, String text) {
        return root.lookupAll(".button").stream().filter(javafx.scene.control.Button.class::isInstance)
                .map(javafx.scene.control.Button.class::cast).filter(button -> button.getText().equals(text))
                .findFirst().orElseThrow();
    }

    private static void press(javafx.scene.Node target) {
        target.fireEvent(new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_PRESSED,
                0, 0, 0, 0, javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false,
                true, false, false, false, false, true, new javafx.scene.input.PickResult(target, 0, 0)));
    }

    private static DockLayoutView.Area area(DockingManager manager, String name) {
        return manager.layout().windows().getFirst().areas().stream()
                .filter(area -> area.id().value().equals(name)).findFirst().orElseThrow();
    }

    private static DockLayoutView.Group group(DockingManager manager, String name) {
        return area(manager, name).groups().getFirst();
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
        if (failure.get() != null) fail("Overlay docking example failed", failure.get());
    }

    @FunctionalInterface
    private interface FxAction { void run() throws Exception; }
}
