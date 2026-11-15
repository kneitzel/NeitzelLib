package de.neitzel.fx.docking;

import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;

import java.util.function.DoubleSupplier;
import java.util.function.BooleanSupplier;

/** Retains a side area's extent while its split branch is replaced or hidden. */
final class DockEdgeRegion extends Region {
    private static final double DIVIDER_SIZE = 5;
    private final Side side;
    private final Region divider = new Region();
    private final DoubleSupplier maximum;
    private final Runnable completed;
    private final BooleanSupplier canResize;
    private Node content;
    private double extent = -1;
    private double committedExtent = -1;
    private double pressedAt;
    private double pressedExtent;
    private boolean dragging;

    DockEdgeRegion(Side side, DoubleSupplier maximum, BooleanSupplier canResize, Runnable completed) {
        this.side = side;
        this.maximum = maximum;
        this.completed = completed;
        this.canResize = canResize;
        getStyleClass().add("dock-edge-area");
        divider.getStyleClass().add("dock-area-divider");
        divider.setCursor(side.isVertical() ? Cursor.H_RESIZE : Cursor.V_RESIZE);
        divider.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            if (DockGroupPane.allCollapsed(content) || !canResize.getAsBoolean()) {
                event.consume();
                return;
            }
            dragging = true;
            pressedAt = side.isVertical() ? event.getScreenX() : event.getScreenY();
            pressedExtent = Math.max(0, (side.isVertical() ? getWidth() : getHeight()) - DIVIDER_SIZE);
            event.consume();
        });
        divider.setOnMouseDragged(event -> {
            if (!dragging || content == null) return;
            if (!canResize.getAsBoolean()) {
                cancelResize();
                event.consume();
                return;
            }
            double current = side.isVertical() ? event.getScreenX() : event.getScreenY();
            double direction = side == Side.LEFT || side == Side.TOP ? 1 : -1;
            double minimum = side.isVertical() ? content.minWidth(-1) : content.minHeight(-1);
            extent = Math.max(minimum, Math.min(maximum.getAsDouble() - DIVIDER_SIZE,
                    pressedExtent + direction * (current - pressedAt)));
            requestLayout();
            event.consume();
        });
        divider.setOnMouseReleased(event -> {
            if (!dragging) return;
            if (!canResize.getAsBoolean()) {
                cancelResize();
                event.consume();
                return;
            }
            dragging = false;
            if (Double.compare(committedExtent, extent) != 0) {
                committedExtent = extent;
                completed.run();
            }
            event.consume();
        });
    }

    private void cancelResize() {
        dragging = false;
        extent = committedExtent;
        requestLayout();
    }

    void setContent(Node content) {
        managedProperty().unbind();
        visibleProperty().unbind();
        this.content = content;
        getChildren().clear();
        if (content == null) {
            setManaged(false);
            setVisible(false);
            return;
        }
        getChildren().addAll(content, divider);
        managedProperty().bind(content.managedProperty());
        visibleProperty().bind(content.visibleProperty());
        requestLayout();
    }

    boolean changesPreferredExtent(Double width, Double height) {
        Double requested = side.isVertical() ? width : height;
        return requested != null && Double.compare(requested, committedExtent) != 0;
    }

    void updatePreferredExtent(Double width, Double height) {
        Double requested = side.isVertical() ? width : height;
        if (requested == null) return;
        extent = requested;
        committedExtent = requested;
        requestLayout();
    }

    double committedExtent() { initializeExtent(); return committedExtent; }

    private void initializeExtent() {
        if (extent >= 0 || content == null) return;
        extent = expandedPreference(content, side.isVertical());
        committedExtent = extent;
    }

    private static double expandedPreference(Node node, boolean width) {
        if (node instanceof DockGroupSurface anchor) return width ? anchor.expandedWidth() : anchor.expandedHeight();
        if (!(node instanceof javafx.scene.control.SplitPane split)) return width ? node.prefWidth(-1) : node.prefHeight(-1);
        boolean parallel = (split.getOrientation() == javafx.geometry.Orientation.HORIZONTAL) == width;
        var extents = split.getItems().stream().mapToDouble(child -> expandedPreference(child, width));
        double extent = parallel ? extents.sum() : extents.max().orElse(0);
        if (parallel) {
            for (Node divider : split.lookupAll(".split-pane-divider")) {
                Node owner = divider.getParent();
                while (owner != null && !(owner instanceof javafx.scene.control.SplitPane)) owner = owner.getParent();
                if (owner == split) extent += width ? divider.prefWidth(-1) : divider.prefHeight(-1);
            }
        }
        return extent + (width ? split.getInsets().getLeft() + split.getInsets().getRight()
                : split.getInsets().getTop() + split.getInsets().getBottom());
    }
    Side side() { return side; }

    @Override protected double computePrefWidth(double height) {
        return side.isVertical() ? displayedExtent() + dividerSize() : content == null ? 0 : content.prefWidth(height);
    }

    @Override protected double computePrefHeight(double width) {
        return side.isHorizontal() ? displayedExtent() + dividerSize() : content == null ? 0 : content.prefHeight(width);
    }

    @Override protected double computeMinWidth(double height) {
        return content == null ? 0 : content.minWidth(height) + (side.isVertical() ? dividerSize() : 0);
    }

    @Override protected double computeMinHeight(double width) {
        return content == null ? 0 : content.minHeight(width) + (side.isHorizontal() ? dividerSize() : 0);
    }

    private double dividerSize() { return DockGroupPane.allCollapsed(content) ? 0 : DIVIDER_SIZE; }

    private double displayedExtent() {
        initializeExtent();
        return DockGroupPane.allCollapsed(content)
                ? side.isVertical() ? content.prefWidth(-1) : content.prefHeight(-1) : extent;
    }

    @Override protected void layoutChildren() {
        if (content == null) return;
        double dividerSize = dividerSize();
        divider.setVisible(dividerSize > 0);
        double width = getWidth();
        double height = getHeight();
        if (side.isVertical()) {
            content.resizeRelocate(side == Side.RIGHT ? dividerSize : 0, 0,
                    Math.max(0, width - dividerSize), height);
            divider.resizeRelocate(side == Side.RIGHT ? 0 : width - dividerSize, 0, dividerSize, height);
        } else {
            content.resizeRelocate(0, side == Side.BOTTOM ? dividerSize : 0,
                    width, Math.max(0, height - dividerSize));
            divider.resizeRelocate(0, side == Side.BOTTOM ? 0 : height - dividerSize, width, dividerSize);
        }
    }
}
