package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RestoreFloatingGeometryTest {
    @BeforeAll static void startJavaFx() throws Exception {
        FxThread.start(); FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @Test void newAndRestoredHostsWaitForMainAndOffscreenFramesFitTheCurrentDisplay() throws Exception {
        FxThread.onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            Stage source = new Stage(); Stage target = new Stage();
            try {
                DockingManager original = attach(source);
                DockableId id = new DockableId("notes");
                Dockable notes = new Dockable(id, new TextArea("source"));
                assertInstanceOf(ActionResult.Applied.class, original.open(notes, DockTarget.area("editors")));
                assertInstanceOf(ActionResult.Applied.class, original.move(id, DockTarget.floating(new Rectangle2D(100, 100, 400, 300))));
                Stage originalHost = (Stage) notes.getContent().getScene().getWindow();
                assertFalse(originalHost.isShowing(), "New floating hosts also wait for main startup");
                source.show(); assertTrue(originalHost.isShowing());
                LayoutSnapshot saved = original.snapshot();
                var visual = Screen.getPrimary().getVisualBounds();
                Rectangle2D unavailable = new Rectangle2D(100_000, -100_000, visual.getWidth() + 500, visual.getHeight() + 500);
                LayoutSnapshot offscreen = new LayoutSnapshot(saved.version(), saved.windows().stream()
                        .map(window -> window.main() ? window : new LayoutSnapshot.Window(window.id(), false, unavailable, window.roots()))
                        .toList(), saved.activeDockableId(), saved.returnPositions(), saved.initialIntents());
                DockingManager restored = attach(target); AtomicReference<Dockable> fresh = new AtomicReference<>();
                assertInstanceOf(RestoreResult.Applied.class, restored.restore(offscreen, requested -> {
                    Dockable dockable = new Dockable(requested, new TextArea("loaded")); fresh.set(dockable);
                    return new DockableResolution.Available(dockable);
                }));
                Rectangle2D fitted = restored.snapshot().windows().get(1).bounds();
                assertTrue(visual.contains(fitted), "Committed restored bounds fit an available visual display");
                assertEquals(visual.getWidth(), fitted.getWidth(), .01);
                assertEquals(visual.getHeight(), fitted.getHeight(), .01);
                Stage host = (Stage) fresh.get().getContent().getScene().getWindow();
                assertFalse(host.isShowing());
                target.show(); assertTrue(host.isShowing());
                assertTrue(visual.contains(new Rectangle2D(host.getX(), host.getY(), host.getWidth(), host.getHeight())));
            } finally {
                List.copyOf(Window.getWindows()).stream().filter(window -> !existing.contains(window)).forEach(Window::hide);
                source.hide(); target.hide();
            }
        });
    }

    private static DockingManager attach(Stage stage) {
        DockPane pane = new DockPane(); pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
        stage.setScene(new Scene(pane, 700, 500)); return DockingManager.attach(stage, pane, new DockingOptions());
    }
}
