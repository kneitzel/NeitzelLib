package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RestoreFloatingFallbackTest {
    @BeforeAll static void startJavaFx() throws Exception {
        FxThread.start(); FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void changedMainOnlyAndMissingContentsNeverCreateAnEmptyFloatingHost(boolean designatedFallback) throws Exception {
        FxThread.onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            Stage source = new Stage(); Stage target = new Stage();
            try {
                DockingManager original = attach(source, new DockingOptions());
                DockableId project = new DockableId("project");
                DockableId preview = new DockableId("preview");
                for (DockableId id : List.of(project, preview)) {
                    assertInstanceOf(ActionResult.Applied.class, original.open(new Dockable(id, new TextArea(id.value())), DockTarget.area("editors")));
                    assertInstanceOf(ActionResult.Applied.class, original.move(id, DockTarget.floating(new Rectangle2D(100, 100, 400, 300))));
                }
                LayoutSnapshot saved = original.snapshot();
                AtomicInteger customized = new AtomicInteger(); AtomicInteger discarded = new AtomicInteger();
                DockingOptions options = new DockingOptions();
                if (designatedFallback) options.setFallbackTarget(DockTarget.area("editors"));
                options.setFloatingWindowCustomizer(configuration -> customized.incrementAndGet());
                DockingManager restored = attach(target, options);
                LayoutSnapshot before = restored.snapshot();
                AtomicReference<Dockable> prepared = new AtomicReference<>();
                RestoreResult result = restored.restore(saved, id -> {
                    if (id.equals(preview)) return new DockableResolution.Missing();
                    Dockable dockable = new Dockable(id, new TextArea("fresh project"));
                    dockable.setMainWindowOnly(true); dockable.setOnDiscarded(discarded::incrementAndGet);
                    prepared.set(dockable); return new DockableResolution.Available(dockable);
                });
                assertEquals(0, customized.get(), "Omitted windows are not prepared or customized");
                assertEquals(1, restored.layout().windows().size());
                if (designatedFallback) {
                    assertEquals(List.of(preview), assertInstanceOf(RestoreResult.Applied.class, result).missingIds());
                    assertSame(target, prepared.get().getContent().getScene().getWindow());
                    assertSame(prepared.get(), restored.activeDockable());
                    assertEquals(0, discarded.get());
                } else {
                    DockFailure failure = assertInstanceOf(RestoreResult.Rejected.class, result).failure();
                    assertEquals(DockFailure.Code.MAIN_WINDOW_ONLY, failure.code());
                    assertEquals(project, failure.dockableId());
                    assertSame(before, restored.snapshot());
                    assertNull(prepared.get().getContent().getParent());
                    assertEquals(1, discarded.get());
                }
                target.show();
                assertEquals(1, restored.layout().windows().size());
            } finally {
                List.copyOf(Window.getWindows()).stream().filter(window -> !existing.contains(window)).forEach(Window::hide);
                source.hide(); target.hide();
            }
        });
    }

    private static DockingManager attach(Stage stage, DockingOptions options) {
        DockPane pane = new DockPane(); pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
        stage.setScene(new Scene(pane, 700, 500)); return DockingManager.attach(stage, pane, options);
    }
}
