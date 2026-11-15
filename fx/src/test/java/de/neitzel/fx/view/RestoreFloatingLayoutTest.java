package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Multiwindow startup and return through ordinary snapshot/restore APIs. */
class RestoreFloatingLayoutTest {
    @BeforeAll static void startJavaFx() throws Exception {
        FxThread.start(); FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @Test void floatingSplitsStayHiddenUntilMainShowAndReturnToTheirEmptyMainArea() throws Exception {
        FxThread.onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            Stage source = new Stage();
            Stage target = new Stage();
            try {
                DockingManager original = attach(source, new DockingOptions());
                Map<DockableId, Dockable> originals = new LinkedHashMap<>();
                for (String id : List.of("notes", "preview", "readme")) {
                    Dockable dockable = new Dockable(new DockableId(id), new TextArea("Saved " + id));
                    dockable.setTitle(id);
                    originals.put(dockable.getId(), dockable);
                    assertInstanceOf(ActionResult.Applied.class, original.open(dockable, DockTarget.area("editors")));
                }
                source.show();
                var screen = Screen.getPrimary().getVisualBounds();
                DockableId notes = new DockableId("notes");
                DockableId preview = new DockableId("preview");
                DockableId readme = new DockableId("readme");
                assertInstanceOf(ActionResult.Applied.class, original.move(notes, DockTarget.floating(
                        new Rectangle2D(screen.getMinX() + 80, screen.getMinY() + 80, 400, 400))));
                DockGroupId notesGroup = groupContaining(original, notes).id();
                assertInstanceOf(ActionResult.Applied.class, original.move(preview, DockTarget.split(notesGroup, Side.BOTTOM)));
                assertInstanceOf(ActionResult.Applied.class, original.move(readme, DockTarget.floating(
                        new Rectangle2D(screen.getMaxX() - 440, screen.getMinY() + 100, 400, 320))));
                original.activate(notes);
                LayoutSnapshot saved = original.snapshot();
                assertEquals(3, saved.windows().size());
                assertTrue(saved.returnPositions().keySet().containsAll(List.of(notes, preview, readme)));
                assertEquals(2, original.layout().windows().stream().filter(window -> !window.main())
                        .filter(window -> window.areas().stream().anyMatch(area -> area.groups().size() == 2))
                        .findFirst().orElseThrow().areas().getFirst().groups().size());

                AtomicInteger customized = new AtomicInteger();
                DockingOptions options = new DockingOptions();
                options.setFloatingWindowCustomizer(configuration -> {
                    customized.incrementAndGet(); configuration.setTitle("Restored editor window");
                });
                DockingManager restored = attach(target, options);
                Map<DockableId, Dockable> fresh = new LinkedHashMap<>();
                RestoreResult.Applied result = assertInstanceOf(RestoreResult.Applied.class, restored.restore(saved, id -> {
                    Dockable dockable = new Dockable(id, new TextArea("Loaded " + id.value()));
                    dockable.setTitle(id.value()); fresh.put(id, dockable);
                    return new DockableResolution.Available(dockable);
                }));
                assertTrue(result.missingIds().isEmpty());
                assertEquals(2, customized.get());
                assertEquals(3, restored.layout().windows().size());
                for (Dockable dockable : fresh.values()) {
                    assertNotSame(originals.get(dockable.getId()).getContent(), dockable.getContent());
                    Stage floating = (Stage) dockable.getContent().getScene().getWindow();
                    assertNotSame(target, floating);
                    assertFalse(floating.isShowing(), "Restored floating hosts wait for the main Stage");
                    assertEquals("Restored editor window", floating.getTitle());
                }
                target.show();
                for (Dockable dockable : fresh.values()) assertTrue(dockable.getContent().getScene().getWindow().isShowing());
                assertSame(fresh.get(notes), restored.activeDockable());
                for (DockableId id : List.of(notes, preview, readme)) {
                    assertInstanceOf(ActionResult.Applied.class, restored.move(id, DockTarget.returnToMain()));
                    assertSame(target, fresh.get(id).getContent().getScene().getWindow());
                    assertEquals("Loaded " + id.value(), ((TextArea) fresh.get(id).getContent()).getText());
                }
                assertEquals(1, restored.layout().windows().size());
            } finally {
                List.copyOf(Window.getWindows()).stream().filter(window -> !existing.contains(window)).forEach(Window::hide);
                source.hide(); target.hide();
            }
        });
    }

    private static DockingManager attach(Stage stage, DockingOptions options) {
        DockPane pane = new DockPane();
        pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
        stage.setScene(new Scene(pane, 700, 500));
        return DockingManager.attach(stage, pane, options);
    }

    private static DockLayoutView.Group groupContaining(DockingManager manager, DockableId id) {
        return manager.layout().windows().stream().flatMap(window -> window.areas().stream())
                .flatMap(area -> area.groups().stream()).filter(group -> group.dockableIds().contains(id))
                .findFirst().orElseThrow();
    }
}
