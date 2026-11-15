package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RestoreValidationTest {
    @BeforeAll static void startJavaFx() throws InterruptedException { FxThread.start(); }

    @ParameterizedTest
    @ValueSource(strings = {"version", "duplicate-root", "duplicate-tab", "unknown-selection", "invalid-size", "invalid-split", "null-side", "null-mode", "null-split-policy", "not-empty"})
    void invalidInputIsRejectedBeforeResolvingAndLeavesTheStartingLayoutUsable(String fault) throws Exception {
        FxThread.onFx(() -> {
            LayoutSnapshot saved = saved();
            DockLayout initial = new DockLayout(); initial.setCenter(new DockGroupDefinition("start"));
            DockingManager manager = manager(initial);
            if (fault.equals("not-empty")) manager.open(new Dockable(new DockableId("live"), new TextArea("Live edits")), DockTarget.area("start"));
            LayoutSnapshot invalid = invalid(saved, fault);
            var before = manager.layout();
            var snapshotBefore = manager.snapshot();
            AtomicInteger calls = new AtomicInteger();
            RestoreResult.Rejected result = assertInstanceOf(RestoreResult.Rejected.class,
                    manager.restore(invalid, id -> {
                        calls.incrementAndGet();
                        return new DockableResolution.Available(new Dockable(id, new TextArea("Unexpected loading")));
                    }));
            assertEquals(fault.equals("version") ? DockFailure.Code.UNSUPPORTED_SNAPSHOT_VERSION
                    : fault.equals("not-empty") ? DockFailure.Code.RESTORE_REQUIRES_EMPTY_MANAGER
                    : DockFailure.Code.INVALID_SNAPSHOT, result.failure().code());
            assertEquals(0, calls.get());
            assertTrue(result.cleanupFailures().isEmpty());
            assertSame(before, manager.layout());
            assertEquals(snapshotBefore, manager.snapshot());
            assertInstanceOf(ActionResult.Applied.class, manager.open(new Dockable(new DockableId("recovery"),
                    new TextArea("Fallback remains usable")), DockTarget.area("start")));
        });
    }

    private static LayoutSnapshot invalid(LayoutSnapshot saved, String fault) {
        var window = saved.windows().getFirst();
        var root = window.roots().getFirst();
        var split = (LayoutSnapshot.Split) root.branch();
        var group = (LayoutSnapshot.Group) split.first();
        var settings = group.settings();
        var changedSettings = new LayoutSnapshot.GroupSettings(fault.equals("null-side") ? null : settings.tabSide(),
                fault.equals("null-mode") ? null : settings.mode(), settings.preferredWidth(), settings.preferredHeight(),
                settings.minimumWidth(), settings.minimumHeight(), settings.persistent(), settings.visibleWhenEmpty(),
                settings.releaseSpaceWhenEmpty(), settings.collapsible(), fault.equals("null-split-policy") ? null : settings.splitDirections());
        LayoutSnapshot.Branch first = new LayoutSnapshot.Group(group.aliases(), group.id(), group.originArea(),
                changedSettings, group.collapsed(), fault.equals("invalid-size") ? Double.NaN : group.expandedWidth(),
                group.expandedHeight(), fault.equals("duplicate-tab") ? List.of(group.tabs().getFirst(), group.tabs().getFirst()) : group.tabs(),
                fault.equals("unknown-selection") ? new DockableId("missing") : group.selected());
        var changedRoot = new LayoutSnapshot.Root(root.placement(), root.preferredWidth(), root.preferredHeight(),
                new LayoutSnapshot.Split(split.aliases(), split.orientation(), fault.equals("invalid-split") ? 1.2 : split.position(), first, split.second()));
        var changedWindow = new LayoutSnapshot.Window(window.id(), window.main(), window.bounds(),
                fault.equals("duplicate-root") ? List.of(changedRoot, changedRoot) : List.of(changedRoot));
        return new LayoutSnapshot(fault.equals("version") ? 99 : saved.version(), List.of(changedWindow),
                saved.activeDockableId(), saved.returnPositions(), saved.initialIntents());
    }

    static LayoutSnapshot saved() {
        DockSplitDefinition split = new DockSplitDefinition(); split.setDestinationId("editors");
        split.setFirst(new DockGroupDefinition("primary")); split.setSecond(new DockGroupDefinition("preview"));
        DockLayout initial = new DockLayout(); initial.setCenter(split);
        DockingManager manager = manager(initial);
        manager.open(new Dockable(new DockableId("a"), new TextArea("A")), DockTarget.area("primary"));
        manager.open(new Dockable(new DockableId("b"), new TextArea("B")), DockTarget.area("preview"));
        return manager.snapshot();
    }

    static DockingManager manager(DockLayout initial) {
        DockPane pane = new DockPane(); pane.setInitialLayout(initial);
        Stage stage = new Stage(); stage.setScene(new Scene(pane, 900, 600));
        return DockingManager.attach(stage, pane, new DockingOptions());
    }
}
