package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RestoreDockingLayoutTest {
    @BeforeAll static void startJavaFx() throws InterruptedException { FxThread.start(); }

    @Test
    void aFreshManagerRestoresNestedGroupsSelectionAndOverlayModeBeforeItsWindowIsShown() throws Exception {
        FxThread.onFx(() -> {
            DockSplitDefinition editors = new DockSplitDefinition();
            editors.setDestinationId("editors");
            editors.setPosition(.35);
            editors.setFirst(new DockGroupDefinition("primary"));
            editors.setSecond(new DockGroupDefinition("preview"));
            DockGroupDefinition tools = new DockGroupDefinition("tools");
            tools.setTabSide(Side.RIGHT);
            tools.setCollapsible(true);
            tools.setMode(DockGroupMode.OVERLAY);
            tools.setPreferredWidth(240);
            DockLayout definition = new DockLayout();
            definition.setCenter(editors);
            definition.setRight(tools);
            DockingManager original = manager(definition);
            original.open(new Dockable(new DockableId("a"), new TextArea("Original A")), DockTarget.area("primary"));
            original.open(new Dockable(new DockableId("b"), new TextArea("Original B")), DockTarget.area("primary"));
            original.open(new Dockable(new DockableId("preview"), new TextArea("Preview")), DockTarget.area("preview"));
            original.activate(new DockableId("a"));
            original.open(new Dockable(new DockableId("project"), new TextArea("Project")), DockTarget.area("tools"));
            LayoutSnapshot saved = original.snapshot();
            DockLayout fallback = new DockLayout();
            fallback.setCenter(new DockGroupDefinition("fallback"));
            DockingManager restored = manager(fallback);
            Map<DockableId, Dockable> reloaded = new LinkedHashMap<>();
            var publications = new java.util.ArrayList<DockLayoutView>();
            restored.layoutProperty().addListener((observable, old, current) -> {
                publications.add(current);
                assertEquals(4, countTabs(restored.snapshot()));
            });
            RestoreResult.Applied result = assertInstanceOf(RestoreResult.Applied.class,
                    restored.restore(saved, id -> {
                        Dockable dockable = new Dockable(id, new TextArea("Reloaded " + id.value()));
                        reloaded.put(id, dockable);
                        return new DockableResolution.Available(dockable);
                    }));
            assertTrue(result.missingIds().isEmpty());
            assertEquals(1, publications.size());
            assertEquals(List.of(new DockableId("a"), new DockableId("b"), new DockableId("preview"), new DockableId("project")),
                    List.copyOf(reloaded.keySet()));
            LayoutSnapshot.Window window = restored.snapshot().windows().getFirst();
            LayoutSnapshot.Split center = assertInstanceOf(LayoutSnapshot.Split.class,
                    window.roots().stream().filter(root -> root.placement() == LayoutSnapshot.Placement.CENTER).findFirst().orElseThrow().branch());
            assertEquals(((LayoutSnapshot.Split) saved.windows().getFirst().roots().getFirst().branch()).position(), center.position(), .01);
            assertEquals(new DockableId("a"), ((LayoutSnapshot.Group) center.first()).selected());
            LayoutSnapshot.Group overlay = (LayoutSnapshot.Group) window.roots().stream()
                    .filter(root -> root.placement() == LayoutSnapshot.Placement.RIGHT).findFirst().orElseThrow().branch();
            assertEquals(DockGroupMode.OVERLAY, overlay.settings().mode());
            assertTrue(overlay.collapsed(), "A temporary overlay never opens automatically after restore");
            assertEquals(240, overlay.expandedWidth(), 1);
            assertInstanceOf(MoveCheck.NoChange.class, restored.checkMove(new DockableId("preview"), DockTarget.area("preview")));
            assertSame(reloaded.get(new DockableId("a")), restored.findDockable(new DockableId("a")).orElseThrow());
            assertFalse(reloaded.get(new DockableId("a")).getContent().getScene().getWindow().isShowing());
        });
    }

    private static int countTabs(LayoutSnapshot snapshot) {
        return snapshot.windows().stream().flatMap(window -> window.roots().stream())
                .mapToInt(root -> countTabs(root.branch())).sum();
    }
    private static int countTabs(LayoutSnapshot.Branch branch) {
        return branch instanceof LayoutSnapshot.Group group ? group.tabs().size()
                : countTabs(((LayoutSnapshot.Split) branch).first()) + countTabs(((LayoutSnapshot.Split) branch).second());
    }
    private static DockingManager manager(DockLayout initial) {
        DockPane pane = new DockPane(); pane.setInitialLayout(initial);
        Stage stage = new Stage(); stage.setScene(new Scene(pane, 900, 600));
        return DockingManager.attach(stage, pane, new DockingOptions());
    }
}
