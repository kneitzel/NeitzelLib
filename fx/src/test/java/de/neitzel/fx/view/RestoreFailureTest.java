package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RestoreFailureTest {
    @BeforeAll static void startJavaFx() throws InterruptedException { FxThread.start(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aLoadFailureDiscardsEveryPreparedContentOnceAndSeparatesCleanupDiagnostics(boolean throwsFailure) throws Exception {
        FxThread.onFx(() -> {
            LayoutSnapshot saved = threeDockables();
            DockLayout initial = new DockLayout(); initial.setCenter(new DockGroupDefinition("start"));
            DockingManager manager = RestoreValidationTest.manager(initial);
            var before = manager.layout();
            var beforeSnapshot = manager.snapshot();
            RuntimeException loadFailure = new IllegalStateException("Document cannot be loaded");
            RuntimeException cleanupFailure = new IllegalArgumentException("Cleanup A failed");
            List<DockableId> requested = new ArrayList<>();
            List<Dockable> prepared = new ArrayList<>();
            AtomicInteger discardedA = new AtomicInteger();
            AtomicInteger discardedC = new AtomicInteger();
            AtomicInteger publications = new AtomicInteger();
            manager.layoutProperty().addListener((observable, old, current) -> publications.incrementAndGet());
            RestoreResult.Rejected result = assertInstanceOf(RestoreResult.Rejected.class,
                    manager.restore(saved, id -> {
                        requested.add(id);
                        if (id.value().equals("b")) {
                            if (throwsFailure) throw loadFailure;
                            return new DockableResolution.Failed(loadFailure);
                        }
                        Dockable dockable = new Dockable(id, new TextArea("Prepared " + id.value()));
                        dockable.setOnDiscarded(() -> {
                            if (id.value().equals("a")) { discardedA.incrementAndGet(); throw cleanupFailure; }
                            discardedC.incrementAndGet();
                        });
                        prepared.add(dockable);
                        return new DockableResolution.Available(dockable);
                    }));
            assertEquals(List.of(new DockableId("a"), new DockableId("c"), new DockableId("b")), requested);
            assertEquals(DockFailure.Code.DOCKABLE_LOAD_FAILED, result.failure().code());
            assertEquals(new DockableId("b"), result.failure().dockableId());
            assertSame(loadFailure, result.failure().cause());
            assertEquals(1, discardedA.get());
            assertEquals(1, discardedC.get(), "Cleanup continues after an earlier callback fails");
            assertEquals(1, result.cleanupFailures().size());
            assertEquals(DockFailure.Code.CLEANUP_FAILED, result.cleanupFailures().getFirst().code());
            assertSame(cleanupFailure, result.cleanupFailures().getFirst().cause());
            assertSame(before, manager.layout());
            assertEquals(beforeSnapshot, manager.snapshot());
            assertEquals(0, publications.get());
            for (Dockable dockable : prepared) {
                assertNull(dockable.getContent().getParent());
                assertNull(dockable.getContent().getScene());
                assertTrue(manager.findDockable(dockable.getId()).isEmpty());
            }
        });
    }

    static LayoutSnapshot threeDockables() {
        LayoutSnapshot saved = RestoreValidationTest.saved();
        var window = saved.windows().getFirst(); var root = window.roots().getFirst();
        var split = (LayoutSnapshot.Split) root.branch(); var first = (LayoutSnapshot.Group) split.first();
        var withThird = new LayoutSnapshot.Group(first.aliases(), first.id(), first.originArea(), first.settings(),
                first.collapsed(), first.expandedWidth(), first.expandedHeight(),
                List.of(new DockableId("a"), new DockableId("c")), first.selected());
        return new LayoutSnapshot(saved.version(), List.of(new LayoutSnapshot.Window(window.id(), true, null,
                List.of(new LayoutSnapshot.Root(root.placement(), root.preferredWidth(), root.preferredHeight(),
                        new LayoutSnapshot.Split(split.aliases(), split.orientation(), split.position(), withThird, split.second()))))),
                saved.activeDockableId(), saved.returnPositions(), saved.initialIntents());
    }
}
