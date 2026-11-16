package de.neitzel.fx.example.docking.workspace;

import de.neitzel.fx.docking.*;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Side;
import java.io.IOException;

/** Equivalent ordinary FXML and Java definitions; neither requires MVVM. */
final class WorkspaceView {
    private WorkspaceView() { }
    static DockPane create(boolean fxml) throws IOException {
        if (fxml) return FXMLLoader.load(WorkspaceView.class.getResource("ReferenceWorkspace.fxml"));
        DockLayout layout = new DockLayout();
        layout.setCenter(new DockGroupDefinition("editors"));
        layout.setLeft(side("project", Side.LEFT, 240));
        DockGroupDefinition changes = side("changes", Side.RIGHT, 220); changes.setMode(DockGroupMode.OVERLAY);
        layout.setRight(changes);
        DockGroupDefinition bottom = side("bottom", Side.TOP, 200); bottom.setPreferredHeight(160);
        layout.setBottom(bottom);
        DockPane pane = new DockPane(); pane.setId("workspaceDock"); pane.setInitialLayout(layout); return pane;
    }
    private static DockGroupDefinition side(String id, Side side, double width) {
        DockGroupDefinition group = new DockGroupDefinition(id); group.setTabSide(side); group.setPreferredWidth(width);
        group.setCollapsible(true); group.setVisibleWhenEmpty(false); group.setReleaseSpaceWhenEmpty(true); return group;
    }
}
