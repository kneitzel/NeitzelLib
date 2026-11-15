package de.neitzel.fx.docking;

import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.geometry.Bounds;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import lombok.Getter;
import lombok.Setter;

/** Embeddable docking area whose definition is captured when a manager attaches. */
public final class DockPane extends Region {
    @Getter @Setter private DockLayout initialLayout = new DockLayout();
    final BorderPane surface = new BorderPane();
    private final Rectangle dropIndicator = new Rectangle();
    private final java.util.Map<Side, DockEdgeRegion> edgeAreas = new java.util.EnumMap<>(Side.class);
    DockingManager manager;

    public DockPane() {
        getStyleClass().add("dock-pane");
        getChildren().add(surface);
        dropIndicator.getStyleClass().add("dock-drop-indicator");
        dropIndicator.setManaged(false);
        dropIndicator.setMouseTransparent(true);
        dropIndicator.setVisible(false);
        dropIndicator.setStrokeWidth(2);
        getChildren().add(dropIndicator);
    }

    void showDropIndicator(Bounds screenBounds, boolean noChange) {
        Bounds local = screenToLocal(screenBounds);
        dropIndicator.setX(local.getMinX());
        dropIndicator.setY(local.getMinY());
        dropIndicator.setWidth(local.getWidth());
        dropIndicator.setHeight(local.getHeight());
        dropIndicator.setFill(noChange ? Color.rgb(100, 100, 100, .15) : Color.rgb(30, 144, 255, .18));
        dropIndicator.setStroke(noChange ? Color.GRAY : Color.DODGERBLUE);
        dropIndicator.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("no-change"), noChange);
        dropIndicator.setVisible(true);
    }

    void hideDropIndicator() {
        dropIndicator.setVisible(false);
    }

    void setArea(Side side, Node content, java.util.function.BooleanSupplier canResize, Runnable completed) {
        DockEdgeRegion area = edgeAreas.computeIfAbsent(side,
                edge -> new DockEdgeRegion(edge, () -> availableExtent(edge), canResize, completed));
        area.setContent(content);
        switch (side) {
            case LEFT -> surface.setLeft(area);
            case RIGHT -> surface.setRight(area);
            case TOP -> surface.setTop(area);
            case BOTTOM -> surface.setBottom(area);
        }
    }

    double committedExtent(Side side) {
        DockEdgeRegion area = edgeAreas.get(side);
        return area == null ? -1 : area.committedExtent();
    }

    Side edgeOf(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof DockEdgeRegion edge) return edge.side();
        }
        return null;
    }

    private double availableExtent(Side side) {
        boolean horizontal = side.isVertical();
        double available = horizontal ? getWidth() : getHeight();
        for (var entry : edgeAreas.entrySet()) {
            if (entry.getKey() != side && entry.getKey().isVertical() == horizontal && entry.getValue().isManaged()) {
                available -= horizontal ? entry.getValue().getWidth() : entry.getValue().getHeight();
            }
        }
        Node center = surface.getCenter();
        double remainingMinimum = center == null || !center.isManaged() ? 0
                : horizontal ? center.minWidth(-1) : center.minHeight(-1);
        if (!horizontal) {
            for (Side edge : new Side[]{Side.LEFT, Side.RIGHT}) {
                DockEdgeRegion area = edgeAreas.get(edge);
                if (area != null && area.isManaged()) remainingMinimum = Math.max(remainingMinimum, area.minHeight(-1));
            }
        }
        return available - remainingMinimum;
    }

    @Override protected void layoutChildren() { surface.resizeRelocate(0, 0, getWidth(), getHeight()); }
    @Override protected double computePrefWidth(double height) { return surface.prefWidth(height); }
    @Override protected double computePrefHeight(double width) { return surface.prefHeight(width); }
    @Override protected double computeMinWidth(double height) { return surface.minWidth(height); }
    @Override protected double computeMinHeight(double width) { return surface.minHeight(width); }
}
