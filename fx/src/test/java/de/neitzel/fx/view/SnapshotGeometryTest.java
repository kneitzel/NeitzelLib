package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotGeometryTest {
    @BeforeAll static void startJavaFx() throws InterruptedException { FxThread.start(); }

    @Test
    void aSelectionPublicationDuringResizeRetainsCommittedSnapshotSizesUntilRelease() throws Exception {
        FxThread.onFx(() -> {
            javafx.application.Platform.setImplicitExit(false);
            DockLayout initial = new DockLayout(); initial.setCenter(new DockGroupDefinition("editors"));
            DockGroupDefinition tools = new DockGroupDefinition("tools");
            tools.setTabSide(Side.RIGHT); tools.setPreferredWidth(240); initial.setRight(tools);
            DockPane pane = new DockPane(); pane.setInitialLayout(initial);
            Stage stage = new Stage(); stage.setScene(new Scene(pane, 900, 600));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            manager.open(new Dockable(new DockableId("editor"), new TextArea("Editor")), DockTarget.area("editors"));
            TextArea first = new TextArea("First");
            manager.open(new Dockable(new DockableId("a"), first), DockTarget.area("tools"));
            manager.open(new Dockable(new DockableId("b"), new TextArea("Second")), DockTarget.area("tools"));
            manager.activate(new DockableId("a"));
            stage.show();
            try {
                Node divider = pane.lookup(".dock-area-divider");
                LayoutSnapshot before = manager.snapshot();
                mouse(divider, MouseEvent.MOUSE_PRESSED, 0);
                mouse(divider, MouseEvent.MOUSE_DRAGGED, -80);
                pane.layout();
                assertSame(before, manager.snapshot(), "A snapshot read cannot capture live resize geometry");
                manager.activate(new DockableId("b"));
                assertEquals(new DockableId("b"), group(manager.snapshot()).selected());
                assertEquals(240, group(manager.snapshot()).expandedWidth(), 1,
                        "Selection publication cannot commit the still-dragged group width");
                assertEquals(240, root(manager.snapshot()).preferredWidth(), 1);
                mouse(divider, MouseEvent.MOUSE_RELEASED, -80);
                assertEquals(320, group(manager.snapshot()).expandedWidth(), 1);
                assertEquals(320, root(manager.snapshot()).preferredWidth(), 1);
                assertEquals(320, manager.layout().windows().getFirst().areas().stream()
                        .filter(area -> area.id().value().equals("tools")).findFirst().orElseThrow().preferredWidth(), 1);
            } finally { stage.hide(); }
        });
    }

    private static LayoutSnapshot.Root root(LayoutSnapshot snapshot) {
        return snapshot.windows().getFirst().roots().stream()
                .filter(root -> root.placement() == LayoutSnapshot.Placement.RIGHT).findFirst().orElseThrow();
    }
    private static LayoutSnapshot.Group group(LayoutSnapshot snapshot) { return (LayoutSnapshot.Group) root(snapshot).branch(); }
    private static void mouse(Node node, javafx.event.EventType<MouseEvent> type, double screenX) {
        node.fireEvent(new MouseEvent(type, 0, 0, screenX, 0, MouseButton.PRIMARY, 1,
                false, false, false, false, type != MouseEvent.MOUSE_RELEASED, false, false,
                false, false, false, new PickResult(node, 0, 0)));
    }
}
