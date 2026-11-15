package de.neitzel.fx.docking;

import javafx.geometry.Bounds;
import javafx.geometry.Side;
import javafx.scene.layout.Region;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.event.EventHandler;
import java.util.ArrayList;
import java.util.List;

/** Places live overlay groups above docked content without reserving their expanded size. */
final class DockOverlayLayer extends Region {
    private final DockPane host;
    private final List<DockGroupSurface> anchors = new ArrayList<>();
    private final EventHandler<MouseEvent> outsidePress = this::outsidePress;
    private Scene registeredScene;
    private boolean disposed;

    DockOverlayLayer(DockPane host) {
        this.host = host;
        setManaged(false);
        setPickOnBounds(false);
        getStyleClass().add("dock-overlay-layer");
        sceneProperty().addListener((observable, old, current) -> {
            if (registeredScene != null) registeredScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsidePress);
            registeredScene = disposed ? null : current;
            if (registeredScene != null) registeredScene.addEventFilter(MouseEvent.MOUSE_PRESSED, outsidePress);
        });
    }

    void dispose() {
        disposed = true;
        if (registeredScene != null) registeredScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsidePress);
        registeredScene = null;
    }

    private void outsidePress(MouseEvent event) {
        Node target = event.getPickResult().getIntersectedNode();
        if (target == null && event.getTarget() instanceof Node node) target = node;
        if (host.manager != null) host.manager.dismissOutsideOverlays(target);
    }

    void attach(DockGroupSurface anchor) {
        anchors.add(anchor);
        getChildren().add(anchor.tabs);
        requestLayout();
    }

    void detach(DockGroupSurface anchor) {
        anchors.remove(anchor);
        getChildren().remove(anchor.tabs);
    }

    @Override protected void layoutChildren() {
        for (DockGroupSurface anchor : anchors) {
            DockGroupPane tabs = anchor.tabs;
            Bounds rail = sceneToLocal(anchor.localToScene(anchor.getLayoutBounds()));
            Side edge = host.edgeOf(anchor);
            Side side = edge == null ? tabs.getSide() : edge;
            double width = rail.getWidth();
            double height = rail.getHeight();
            if (!tabs.isCollapsed()) {
                width = Math.max(width, anchor.expandedWidth());
                height = Math.max(height, anchor.expandedHeight());
                double extent = edge == null ? -1 : host.committedExtent(edge);
                if (extent > 0 && anchor.spansEdgeExtent(edge)) {
                    extent = anchor.edgeContentExtent(extent, edge);
                    if (edge.isVertical()) width = extent;
                    else height = extent;
                }
            }
            width = Math.min(getWidth(), width);
            height = Math.min(getHeight(), height);
            double x = side == Side.RIGHT ? rail.getMaxX() - width : rail.getMinX();
            double y = side == Side.BOTTOM ? rail.getMaxY() - height : rail.getMinY();
            tabs.resizeRelocate(Math.max(0, Math.min(getWidth() - width, x)),
                    Math.max(0, Math.min(getHeight() - height, y)), width, height);
            tabs.layout();
        }
    }
}
