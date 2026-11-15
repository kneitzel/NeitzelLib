package de.neitzel.fx.docking;

import javafx.scene.Node;
import javafx.scene.layout.Region;

/** Stable layout anchor; an overlay's live TabPane is hosted above the docked surface. */
final class DockGroupSurface extends Region {
    final DockGroupPane tabs;
    private final DockGroupDefinition definition;
    private DockPane host;
    private double expandedWidth;
    private double expandedHeight;
    private double committedWidth;
    private double committedHeight;

    DockGroupSurface(DockGroupPane tabs, DockGroupDefinition definition) {
        this.tabs = tabs;
        this.definition = definition;
        getStyleClass().add("dock-group-surface");
        getChildren().add(tabs);
        visibleProperty().bind(tabs.visibleProperty());
        managedProperty().bind(tabs.managedProperty());
        parentProperty().addListener((observable, old, current) -> { if (current == null) detach(); });
    }

    boolean unreserved() { return tabs.isCollapsed() || tabs.isOverlay(); }
    double expandedWidth() { return expandedWidth > 0 ? expandedWidth : definition.getPreferredWidth(); }
    double expandedHeight() { return expandedHeight > 0 ? expandedHeight : definition.getPreferredHeight(); }

    double[] snapshotSize(boolean preserveGeometry) {
        if (!preserveGeometry || committedWidth <= 0 || committedHeight <= 0) {
            committedWidth = expandedWidth();
            committedHeight = expandedHeight();
        }
        return new double[]{committedWidth, committedHeight};
    }

    boolean spansEdgeExtent(javafx.geometry.Side side) {
        for (Node current = getParent(); current != null && !(current instanceof DockEdgeRegion); current = current.getParent()) {
            if (current instanceof javafx.scene.control.SplitPane split
                    && (split.getOrientation() == javafx.geometry.Orientation.HORIZONTAL) == side.isVertical()) return false;
        }
        return true;
    }

    double edgeContentExtent(double extent, javafx.geometry.Side side) {
        for (Node current = getParent(); current != null && !(current instanceof DockEdgeRegion); current = current.getParent()) {
            if (current instanceof javafx.scene.control.SplitPane split) {
                extent -= side.isVertical() ? split.getInsets().getLeft() + split.getInsets().getRight()
                        : split.getInsets().getTop() + split.getInsets().getBottom();
            }
        }
        return Math.max(0, extent);
    }

    boolean changesPreferredSize(Double width, Double height) {
        return tabs.isOverlay() && ((width != null && Double.compare(width, expandedWidth()) != 0)
                || (height != null && Double.compare(height, expandedHeight()) != 0));
    }

    void updatePreferredSize(Double width, Double height) {
        if (width != null) expandedWidth = width;
        if (height != null) expandedHeight = height;
    }

    void synchronize() {
        DockPane target = null;
        for (Node current = getParent(); current != null; current = current.getParent()) {
            if (current instanceof DockPane pane) { target = pane; break; }
        }
        if (host != null && (host != target || !tabs.isOverlay())) detach();
        if (tabs.isOverlay() && target != null && host == null) {
            getChildren().remove(tabs);
            host = target;
            host.overlays.attach(this);
        }
        requestLayout();
        if (target != null) target.requestLayout();
    }

    private void detach() {
        if (host == null) return;
        host.overlays.detach(this);
        host = null;
        getChildren().add(tabs);
    }

    @Override protected double computePrefWidth(double height) {
        return tabs.isOverlay() ? tabs.railWidth() : tabs.prefWidth(height);
    }
    @Override protected double computePrefHeight(double width) {
        return tabs.isOverlay() ? tabs.railHeight() : tabs.prefHeight(width);
    }
    @Override protected double computeMinWidth(double height) {
        return tabs.isOverlay() ? tabs.railWidth() : tabs.minWidth(height);
    }
    @Override protected double computeMinHeight(double width) {
        return tabs.isOverlay() ? tabs.railHeight() : tabs.minHeight(width);
    }
    @Override protected void layoutChildren() {
        if (host == null) {
            tabs.resizeRelocate(0, 0, getWidth(), getHeight());
            if (!tabs.isCollapsed() && !tabs.isOverlay()) {
                expandedWidth = getWidth();
                expandedHeight = getHeight();
            }
        } else host.overlays.requestLayout();
    }
}
