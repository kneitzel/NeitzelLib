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
            if (!canResize.getAsBoolean()) {
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
        if (extent < 0) {
            extent = side.isVertical() ? content.prefWidth(-1) : content.prefHeight(-1);
            committedExtent = extent;
        }
        getChildren().addAll(content, divider);
        managedProperty().bind(content.managedProperty());
        visibleProperty().bind(content.visibleProperty());
        requestLayout();
    }

    double committedExtent() { return committedExtent; }
    Side side() { return side; }

    @Override protected double computePrefWidth(double height) {
        return side.isVertical() ? extent + DIVIDER_SIZE : content == null ? 0 : content.prefWidth(height);
    }

    @Override protected double computePrefHeight(double width) {
        return side.isHorizontal() ? extent + DIVIDER_SIZE : content == null ? 0 : content.prefHeight(width);
    }

    @Override protected double computeMinWidth(double height) {
        return content == null ? 0 : content.minWidth(height) + (side.isVertical() ? DIVIDER_SIZE : 0);
    }

    @Override protected double computeMinHeight(double width) {
        return content == null ? 0 : content.minHeight(width) + (side.isHorizontal() ? DIVIDER_SIZE : 0);
    }

    @Override protected void layoutChildren() {
        if (content == null) return;
        double width = getWidth();
        double height = getHeight();
        if (side.isVertical()) {
            content.resizeRelocate(side == Side.RIGHT ? DIVIDER_SIZE : 0, 0,
                    Math.max(0, width - DIVIDER_SIZE), height);
            divider.resizeRelocate(side == Side.RIGHT ? 0 : width - DIVIDER_SIZE, 0, DIVIDER_SIZE, height);
        } else {
            content.resizeRelocate(0, side == Side.BOTTOM ? DIVIDER_SIZE : 0,
                    width, Math.max(0, height - DIVIDER_SIZE));
            divider.resizeRelocate(0, side == Side.BOTTOM ? 0 : height - DIVIDER_SIZE, width, DIVIDER_SIZE);
        }
    }
}
