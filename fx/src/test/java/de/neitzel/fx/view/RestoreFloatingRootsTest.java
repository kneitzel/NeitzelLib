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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RestoreFloatingRootsTest {
    @BeforeAll static void startJavaFx() throws Exception {
        FxThread.start(); FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @Test void acceptedFloatingSideRootsKeepTheirPlacementAndCommittedExtentAcrossRepeatedStartup() throws Exception {
        FxThread.onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            Stage source = new Stage(); Stage first = new Stage(); Stage second = new Stage();
            try {
                DockPane pane = new DockPane();
                pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
                DockGroupDefinition tools = new DockGroupDefinition("tools"); tools.setPreferredWidth(220);
                pane.getInitialLayout().setLeft(tools);
                source.setScene(new Scene(pane, 800, 600));
                DockingManager original = DockingManager.attach(source, pane, new DockingOptions());
                assertInstanceOf(ActionResult.Applied.class, original.open(new Dockable(new DockableId("editor"), new TextArea("edit")), DockTarget.area("editors")));
                assertInstanceOf(ActionResult.Applied.class, original.open(new Dockable(new DockableId("project"), new TextArea("project")), DockTarget.area("tools")));
                LayoutSnapshot saved = original.snapshot();
                List<LayoutSnapshot.Root> roots = saved.windows().getFirst().roots();
                LayoutSnapshot floating = new LayoutSnapshot(saved.version(), List.of(
                        new LayoutSnapshot.Window(new DockWindowId("main"), true, null, List.of()),
                        new LayoutSnapshot.Window(new DockWindowId("detached-workspace"), false, new Rectangle2D(100, 100, 800, 600), roots)),
                        saved.activeDockableId(), Map.of(), saved.initialIntents());
                DockingManager restored = attach(first);
                assertInstanceOf(RestoreResult.Applied.class, restored.restore(floating, RestoreFloatingRootsTest::load));
                first.show();
                LayoutSnapshot exported = restored.snapshot();
                List<LayoutSnapshot.Root> floatingRoots = exported.windows().get(1).roots();
                assertEquals(roots.stream().map(LayoutSnapshot.Root::placement).toList(),
                        floatingRoots.stream().map(LayoutSnapshot.Root::placement).toList());
                LayoutSnapshot.Root left = floatingRoots.stream().filter(root -> root.placement() == LayoutSnapshot.Placement.LEFT).findFirst().orElseThrow();
                assertEquals(220, left.preferredWidth(), 1);
                var toolsView = restored.layout().windows().get(1).areas().stream()
                        .filter(area -> area.id().equals(new DockAreaId("tools"))).findFirst().orElseThrow();
                assertEquals(left.preferredWidth(), toolsView.preferredWidth(), 1);
                DockingManager restarted = attach(second);
                assertInstanceOf(RestoreResult.Applied.class, restarted.restore(exported, RestoreFloatingRootsTest::load),
                        "A successful restore must publish a valid snapshot for the next startup");
                assertEquals(floatingRoots.stream().map(LayoutSnapshot.Root::placement).toList(),
                        restarted.snapshot().windows().get(1).roots().stream().map(LayoutSnapshot.Root::placement).toList());
            } finally {
                List.copyOf(Window.getWindows()).stream().filter(window -> !existing.contains(window)).forEach(Window::hide);
                source.hide(); first.hide(); second.hide();
            }
        });
    }

    @Test void aRestoredSideRootCannotResizeAnAdjacentGroupAwaitingConsent() throws Exception {
        FxThread.onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            Stage source = new Stage(); Stage target = new Stage();
            try {
                DockPane pane = new DockPane();
                pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
                pane.getInitialLayout().setLeft(new DockGroupDefinition("tools"));
                source.setScene(new Scene(pane, 800, 600));
                DockingManager original = DockingManager.attach(source, pane, new DockingOptions());
                original.open(new Dockable(new DockableId("editor"), new TextArea()), DockTarget.area("editors"));
                original.open(new Dockable(new DockableId("project"), new TextArea()), DockTarget.area("tools"));
                LayoutSnapshot saved = original.snapshot();
                LayoutSnapshot floating = new LayoutSnapshot(saved.version(), List.of(
                        new LayoutSnapshot.Window(new DockWindowId("main"), true, null, List.of()),
                        new LayoutSnapshot.Window(new DockWindowId("detached-workspace"), false,
                                new Rectangle2D(100, 100, 800, 600), saved.windows().getFirst().roots())),
                        saved.activeDockableId(), Map.of(), saved.initialIntents());
                DockingManager restored = attach(target);
                assertInstanceOf(RestoreResult.Applied.class, restored.restore(floating, RestoreFloatingRootsTest::load));
                target.show();
                Dockable editor = restored.findDockable(new DockableId("editor")).orElseThrow();
                var consent = new java.util.concurrent.CompletableFuture<CloseConsent>();
                editor.setCloseCheck(request -> consent);
                restored.requestTabClose(editor.getId());
                LayoutSnapshot before = restored.snapshot();
                try {
                    ActionResult.Rejected rejected = assertInstanceOf(ActionResult.Rejected.class,
                            restored.updateGroup(new DockGroupId("tools"), DockGroupChanges.builder().preferredWidth(300).build()));
                    assertEquals(DockFailure.Code.CLOSE_IN_PROGRESS, rejected.failure().code());
                    assertSame(before, restored.snapshot());
                } finally { consent.complete(CloseConsent.VETO); }
                assertInstanceOf(ActionResult.Applied.class,
                        restored.updateGroup(new DockGroupId("tools"), DockGroupChanges.builder().preferredWidth(300).build()));
            } finally {
                List.copyOf(Window.getWindows()).stream().filter(window -> !existing.contains(window)).forEach(Window::hide);
                source.hide(); target.hide();
            }
        });
    }

    private static DockableResolution load(DockableId id) {
        return new DockableResolution.Available(new Dockable(id, new TextArea("loaded " + id.value())));
    }
    private static DockingManager attach(Stage stage) {
        DockPane pane = new DockPane(); pane.getInitialLayout().setCenter(new DockGroupDefinition("initial"));
        stage.setScene(new Scene(pane, 800, 600)); return DockingManager.attach(stage, pane, new DockingOptions());
    }
}
