package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Persistence reads the completed public layout without retaining live JavaFX content. */
class SnapshotLayoutTest {
    @BeforeAll static void startJavaFx() throws InterruptedException { FxThread.start(); }

    @Test
    void snapshotKeepsCanonicalAliasesSettingsTabOrderAndSelectionAsImmutableVersionedData() throws Exception {
        FxThread.onFx(() -> {
            DockSplitDefinition editors = new DockSplitDefinition();
            editors.setDestinationId("editors");
            editors.setFirst(new DockGroupDefinition("primary"));
            editors.setSecond(new DockGroupDefinition("preview"));
            DockGroupDefinition tools = new DockGroupDefinition("tools");
            tools.setTabSide(Side.RIGHT);
            tools.setCollapsible(true);
            tools.setMode(DockGroupMode.OVERLAY);
            tools.setPreferredWidth(240);
            DockLayout initial = new DockLayout();
            initial.setCenter(editors);
            initial.setRight(tools);
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 900, 600));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            manager.open(new Dockable(new DockableId("a"), new TextArea("Unsaved A")), DockTarget.area("primary"));
            manager.open(new Dockable(new DockableId("b"), new TextArea("Unsaved B")), DockTarget.area("preview"));
            manager.move(new DockableId("b"), DockTarget.area("primary"));
            manager.open(new Dockable(new DockableId("project"), new TextArea("Project")), DockTarget.area("tools"));
            LayoutSnapshot snapshot = manager.snapshot();
            assertEquals(1, snapshot.version());
            assertEquals(1, snapshot.windows().size());
            assertEquals(2, snapshot.windows().getFirst().roots().size(), "Aliases do not duplicate their canonical branch");
            LayoutSnapshot.Group center = assertInstanceOf(LayoutSnapshot.Group.class,
                    snapshot.windows().getFirst().roots().stream()
                            .filter(root -> root.placement() == LayoutSnapshot.Placement.CENTER).findFirst().orElseThrow().branch());
            assertEquals(java.util.Set.of(new DockAreaId("editors"), new DockAreaId("primary"), new DockAreaId("preview")),
                    java.util.Set.copyOf(center.aliases()));
            assertEquals(java.util.List.of(new DockableId("a"), new DockableId("b")), center.tabs());
            assertEquals(new DockableId("b"), center.selected());
            LayoutSnapshot.Group side = assertInstanceOf(LayoutSnapshot.Group.class,
                    snapshot.windows().getFirst().roots().stream()
                            .filter(root -> root.placement() == LayoutSnapshot.Placement.RIGHT).findFirst().orElseThrow().branch());
            assertEquals(DockGroupMode.OVERLAY, side.settings().mode());
            assertEquals(240, side.expandedWidth(), 1);
            assertFalse(side.collapsed());
            assertEquals(new DockableId("project"), snapshot.activeDockableId());
            assertThrows(UnsupportedOperationException.class, () -> center.tabs().clear());
            assertThrows(UnsupportedOperationException.class, () -> snapshot.windows().clear());
            manager.activate(new DockableId("a"));
            assertEquals(new DockableId("b"), center.selected(), "Earlier snapshots never observe later selections");
            assertNotEquals(snapshot, manager.snapshot());
        });
    }
}
