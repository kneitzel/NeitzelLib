package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RestoreFloatingFailureTest {
    @BeforeAll static void startJavaFx() throws Exception {
        FxThread.start(); FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @Test void aLaterCustomizerFailureDiscardsAllPreparedContentsAndNeverShowsPreparedHosts() throws Exception {
        FxThread.onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            Stage source = new Stage(); Stage target = new Stage();
            try {
                DockingManager original = attach(source, new DockingOptions());
                for (String name : List.of("first", "second")) {
                    DockableId id = new DockableId(name);
                    assertInstanceOf(ActionResult.Applied.class, original.open(new Dockable(id, new TextArea(name)), DockTarget.area("editors")));
                    assertInstanceOf(ActionResult.Applied.class, original.move(id, DockTarget.floating(new Rectangle2D(100, 100, 400, 300))));
                }
                LayoutSnapshot saved = original.snapshot();
                RuntimeException creationFailure = new IllegalStateException("second floating host");
                RuntimeException cleanupFailure = new IllegalStateException("first prepared content cleanup");
                AtomicInteger customized = new AtomicInteger();
                DockingOptions options = new DockingOptions();
                options.setFloatingWindowCustomizer(configuration -> {
                    if (customized.incrementAndGet() == 2) throw creationFailure;
                });
                DockingManager restored = attach(target, options);
                LayoutSnapshot before = restored.snapshot(); var layoutBefore = restored.layout();
                List<DockableId> discarded = new ArrayList<>(); List<Dockable> prepared = new ArrayList<>();
                AtomicInteger closed = new AtomicInteger(); AtomicInteger publications = new AtomicInteger();
                restored.layoutProperty().addListener((observable, old, current) -> publications.incrementAndGet());
                RestoreResult.Rejected result = assertInstanceOf(RestoreResult.Rejected.class, restored.restore(saved, id -> {
                    Dockable dockable = new Dockable(id, new TextArea("fresh " + id.value()));
                    dockable.setOnClosed(closed::incrementAndGet);
                    dockable.setOnDiscarded(() -> {
                        discarded.add(id);
                        if (id.value().equals("first")) throw cleanupFailure;
                    });
                    prepared.add(dockable); return new DockableResolution.Available(dockable);
                }));
                assertEquals(2, customized.get());
                assertEquals(DockFailure.Code.WINDOW_CREATION_FAILED, result.failure().code());
                assertSame(creationFailure, result.failure().cause());
                assertEquals(List.of(new DockableId("first"), new DockableId("second")), discarded);
                assertEquals(1, result.cleanupFailures().size());
                assertSame(cleanupFailure, result.cleanupFailures().getFirst().cause());
                assertEquals(0, closed.get()); assertEquals(0, publications.get());
                assertSame(before, restored.snapshot()); assertSame(layoutBefore, restored.layout());
                prepared.forEach(dockable -> {
                    assertNull(dockable.getContent().getParent()); assertNull(dockable.getContent().getScene());
                });
                target.show();
                assertEquals(List.of(target), Window.getWindows().stream().filter(window -> !existing.contains(window)).toList(),
                        "The main shown hook must not resurrect any prepared floating host after failure");
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
