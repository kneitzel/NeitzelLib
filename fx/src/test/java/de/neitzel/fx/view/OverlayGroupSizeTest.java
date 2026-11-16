package de.neitzel.fx.view;

import de.neitzel.fx.docking.ActionResult;
import de.neitzel.fx.docking.DockGroupChanges;
import de.neitzel.fx.docking.DockGroupDefinition;
import de.neitzel.fx.docking.DockGroupId;
import de.neitzel.fx.docking.DockGroupMode;
import de.neitzel.fx.docking.DockLayout;
import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Overlay dimensions belong to the concrete group, including groups inside nested layouts. */
class OverlayGroupSizeTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @Test
    void unpinningOneHorizontallySplitSideGroupPreservesItsOwnWidth() throws Exception {
        FxThread.onFx(() -> {
            DockLayout layout = new DockLayout();
            layout.setCenter(new DockGroupDefinition("editors"));
            DockGroupDefinition tools = new DockGroupDefinition("tools");
            tools.setCollapsible(true);
            tools.setTabSide(Side.RIGHT);
            tools.setPreferredWidth(480);
            layout.setRight(tools);
            try (Fixture fixture = fixture(layout, "tools")) {
                TabPane first = tabs(fixture.pane, fixture.first);
                double groupWidth = first.getWidth();
                assertTrue(groupWidth > 100 && groupWidth < 300,
                        "Each docked group occupies only part of the 480px side area");
                assertInstanceOf(ActionResult.Applied.class, fixture.manager.updateGroup(new DockGroupId("tools"),
                        DockGroupChanges.builder().mode(DockGroupMode.OVERLAY).build()));
                assertEquals(groupWidth, first.getWidth(), 1,
                        "An individual overlay must not expand to the entire side area's width");
                assertSame(fixture.first.getContent(), first.getSelectionModel().getSelectedItem().getContent());
                TabPane sibling = tabs(fixture.pane, fixture.second);
                double siblingWidth = sibling.getWidth();
                assertInstanceOf(ActionResult.Applied.class, fixture.manager.updateGroup(new DockGroupId("tools"),
                        DockGroupChanges.builder().preferredWidth(300).build()));
                assertEquals(300, first.getWidth(), 1);
                assertEquals(siblingWidth, sibling.getWidth(), 1,
                        "Resizing the overlay does not resize its docked sibling");
                assertEquals(480, fixture.manager.layout().windows().getFirst().areas().stream()
                        .filter(area -> area.id().value().equals("tools")).findFirst().orElseThrow().preferredWidth(), 1,
                        "The group's width must not replace the whole outer area's extent");
                assertInstanceOf(ActionResult.Applied.class, fixture.manager.updateGroup(new DockGroupId("tools"),
                        DockGroupChanges.builder().mode(DockGroupMode.DOCKED).build()));
                assertEquals(300, first.getWidth(), 1, "Pinning restores the updated group size within the split");
                assertTrue(sibling.getWidth() >= 80, "The docked sibling retains its minimum width");
                fixture.manager.updateGroup(new DockGroupId("tools"), DockGroupChanges.builder().mode(DockGroupMode.OVERLAY).build());
                assertEquals(300, first.getWidth(), 1);
            }
        });
    }

    @Test
    void anOpenOverlayInsideTheCenterAcceptsAnExplicitPreferredWidth() throws Exception {
        FxThread.onFx(() -> {
            DockLayout layout = new DockLayout();
            DockGroupDefinition editors = new DockGroupDefinition("editors");
            editors.setCollapsible(true);
            editors.setTabSide(Side.LEFT);
            layout.setCenter(editors);
            try (Fixture fixture = fixture(layout, "editors")) {
                TabPane first = tabs(fixture.pane, fixture.first);
                DockGroupId group = new DockGroupId("editors");
                assertInstanceOf(ActionResult.Applied.class, fixture.manager.updateGroup(group,
                        DockGroupChanges.builder().mode(DockGroupMode.OVERLAY).build()));
                assertTrue(first.getWidth() > 400, "The initial overlay remembers the expanded center leaf width");
                assertInstanceOf(ActionResult.Applied.class, fixture.manager.updateGroup(group,
                        DockGroupChanges.builder().preferredWidth(200).build()),
                        "Even the original configured width changes a different live overlay width");
                assertEquals(200, first.getWidth(), 1);
                assertInstanceOf(ActionResult.Applied.class, fixture.manager.updateGroup(group,
                        DockGroupChanges.builder().preferredWidth(300).build()));
                assertEquals(300, first.getWidth(), 1,
                        "The explicit group width replaces the previously remembered overlay width");
                assertSame(fixture.first.getContent(), first.getSelectionModel().getSelectedItem().getContent());
            }
        });
    }

    @Test
    void anAtomicUnpinAndWidthUpdateUsesTheExplicitWidthOnFirstOverlayLayout() throws Exception {
        FxThread.onFx(() -> {
            DockLayout layout = new DockLayout();
            layout.setCenter(new DockGroupDefinition("editors"));
            DockGroupDefinition tools = new DockGroupDefinition("tools");
            tools.setCollapsible(true);
            tools.setTabSide(Side.RIGHT);
            tools.setPreferredWidth(480);
            layout.setRight(tools);
            try (Fixture fixture = fixture(layout, "tools")) {
                assertInstanceOf(ActionResult.Applied.class, fixture.manager.updateGroup(new DockGroupId("tools"),
                        DockGroupChanges.builder().mode(DockGroupMode.OVERLAY).preferredWidth(300).build()));
                assertEquals(300, tabs(fixture.pane, fixture.first).getWidth(), 1,
                        "The explicit width survives attaching the live group to the overlay layer");
            }
        });
    }

    @Test
    void initialOverlayLeavesInAVerticalSideSplitRememberExpandedWidthForOpeningAndPinning() throws Exception {
        FxThread.onFx(() -> {
            DockLayout layout = new DockLayout();
            layout.setCenter(new DockGroupDefinition("editors"));
            DockGroupDefinition first = new DockGroupDefinition("top");
            first.setMode(DockGroupMode.OVERLAY);
            first.setCollapsible(true);
            first.setTabSide(Side.RIGHT);
            first.setPreferredWidth(240);
            DockGroupDefinition second = new DockGroupDefinition("bottom");
            second.setMode(DockGroupMode.OVERLAY);
            second.setCollapsible(true);
            second.setTabSide(Side.RIGHT);
            second.setPreferredWidth(240);
            de.neitzel.fx.docking.DockSplitDefinition tools = new de.neitzel.fx.docking.DockSplitDefinition();
            tools.setDestinationId("tools");
            tools.setOrientation(javafx.geometry.Orientation.VERTICAL);
            tools.setFirst(first);
            tools.setSecond(second);
            layout.setRight(tools);
            DockPane pane = new DockPane();
            pane.setInitialLayout(layout);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 1000, 600));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            Dockable content = new Dockable(new DockableId("bottom-content"), new TextArea("Bottom"));
            manager.open(content, DockTarget.area("bottom"));
            assertEquals(240, tabs(pane, content).getWidth(), 1,
                    "Initial split overlays keep their configured expanded width instead of the rail width");
            manager.updateGroup(new DockGroupId("bottom"), DockGroupChanges.builder().mode(DockGroupMode.DOCKED).build());
            assertEquals(240, tabs(pane, content).getWidth(), 1);
        });
    }

    private static Fixture fixture(DockLayout layout, String area) {
        DockPane pane = new DockPane();
        pane.setInitialLayout(layout);
        Stage stage = new Stage();
        stage.setScene(new Scene(pane, 1000, 600));
        DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
        Dockable first = new Dockable(new DockableId("first"), new TextArea("First group"));
        Dockable second = new Dockable(new DockableId("second"), new TextArea("Second group"));
        manager.open(first, DockTarget.area(area));
        manager.open(second, DockTarget.area(area));
        stage.show();
        try {
            assertInstanceOf(ActionResult.Applied.class,
                    manager.move(second.getId(), DockTarget.split(new DockGroupId(area), Side.RIGHT)));
            return new Fixture(stage, pane, manager, first, second);
        } catch (RuntimeException | Error failure) {
            stage.hide();
            throw failure;
        }
    }

    private static TabPane tabs(DockPane pane, Dockable dockable) {
        return pane.lookupAll(".tab-pane").stream().map(TabPane.class::cast)
                .filter(tabs -> tabs.getTabs().stream().anyMatch(tab -> tab.getContent() == dockable.getContent()))
                .findFirst().orElseThrow();
    }

    private record Fixture(Stage stage, DockPane pane, DockingManager manager, Dockable first, Dockable second) implements AutoCloseable {
        @Override public void close() { stage.hide(); }
    }
}
