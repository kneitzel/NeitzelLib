package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PartialRestoreTest {
    @BeforeAll static void startJavaFx() throws InterruptedException { FxThread.start(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void missingBranchesNormalizeInBothDirectionsWhilePersistentSideDestinationsRemainReachable(boolean missingLeft) throws Exception {
        FxThread.onFx(() -> {
            DockSplitDefinition pair = split("pair", new DockGroupDefinition("middle"), new DockGroupDefinition("right"));
            DockLayout definition = new DockLayout();
            definition.setCenter(split("editors", new DockGroupDefinition("left"), pair));
            DockGroupDefinition tools = new DockGroupDefinition("tools");
            tools.setVisibleWhenEmpty(false); tools.setReleaseSpaceWhenEmpty(true); definition.setRight(tools);
            DockGroupDefinition scratch = new DockGroupDefinition("scratch");
            scratch.setPersistent(false); definition.setTop(scratch);
            DockingManager original = RestoreValidationTest.manager(definition);
            for (String[] entry : new String[][]{{"a", "left"}, {"b", "middle"}, {"c", "right"}, {"tool", "tools"}, {"scratch", "scratch"}}) {
                original.open(new Dockable(new DockableId(entry[0]), new TextArea(entry[0])), DockTarget.area(entry[1]));
            }
            LayoutSnapshot saved = original.snapshot();
            Set<String> missing = missingLeft ? Set.of("a", "tool", "scratch") : Set.of("b", "c", "tool", "scratch");
            DockLayout starting = new DockLayout(); starting.setCenter(new DockGroupDefinition("start"));
            DockingManager restored = RestoreValidationTest.manager(starting);
            RestoreResult.Applied result = assertInstanceOf(RestoreResult.Applied.class, restored.restore(saved, id ->
                    missing.contains(id.value()) ? new DockableResolution.Missing()
                            : new DockableResolution.Available(new Dockable(id, new TextArea("Reloaded " + id.value())))));
            assertEquals((missingLeft ? List.of("a", "tool", "scratch") : List.of("b", "c", "tool", "scratch"))
                    .stream().map(DockableId::new).toList(), result.missingIds());
            for (String id : missing) assertTrue(restored.findDockable(new DockableId(id)).isEmpty());
            LayoutSnapshot.Window window = restored.snapshot().windows().getFirst();
            assertEquals(2, window.roots().size(), "The nonpersistent empty standalone root disappears");
            LayoutSnapshot.Branch center = window.roots().stream().filter(root -> root.placement() == LayoutSnapshot.Placement.CENTER)
                    .findFirst().orElseThrow().branch();
            if (missingLeft) assertInstanceOf(LayoutSnapshot.Split.class, center);
            else assertInstanceOf(LayoutSnapshot.Group.class, center);
            DockableId survivor = new DockableId(missingLeft ? "b" : "a");
            for (String area : List.of("editors", "left", "pair", "middle", "right")) {
                assertFalse(restored.checkMove(survivor, DockTarget.area(area)) instanceof MoveCheck.Rejected,
                        "Normalized area remains addressable: " + area);
            }
            assertInstanceOf(MoveCheck.Allowed.class, restored.checkMove(survivor, DockTarget.area("tools")));
            assertEquals(DockFailure.Code.TARGET_NOT_FOUND,
                    assertInstanceOf(MoveCheck.Rejected.class, restored.checkMove(survivor, DockTarget.area("scratch"))).failure().code());
            LayoutSnapshot.Group emptyTools = (LayoutSnapshot.Group) window.roots().stream()
                    .filter(root -> root.placement() == LayoutSnapshot.Placement.RIGHT).findFirst().orElseThrow().branch();
            assertTrue(emptyTools.tabs().isEmpty());
            assertTrue(emptyTools.settings().persistent());
            DockingManager nextStartup = RestoreValidationTest.manager(starting);
            assertTrue(assertInstanceOf(RestoreResult.Applied.class, nextStartup.restore(restored.snapshot(), id ->
                    new DockableResolution.Available(new Dockable(id, new TextArea("Next startup"))))).missingIds().isEmpty(),
                    "Omitted identities cannot leave invalid references in the next saved snapshot");
        });
    }

    private static DockSplitDefinition split(String id, DockLayoutNode first, DockLayoutNode second) {
        DockSplitDefinition split = new DockSplitDefinition(); split.setDestinationId(id);
        split.setFirst(first); split.setSecond(second); return split;
    }
}
