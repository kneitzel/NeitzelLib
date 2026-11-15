package de.neitzel.fx.control;

import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Standalone status content aligned to the left and right edges of a window.
 * Items are ordinary JavaFX Nodes; no docking or MVVM runtime is required.
 * Modify the item lists on the JavaFX Application Thread.
 */
public final class StatusBar extends Region {
    private final HBox left = new HBox(8);
    private final HBox right = new HBox(8);
    private final HBox row;

    /** Creates an empty status bar with independently populated left and right sides. */
    public StatusBar() {
        getStyleClass().add("status-bar");
        setPadding(new Insets(4, 8, 4, 8));
        left.getStyleClass().add("status-bar-left");
        right.getStyleClass().add("status-bar-right");
        left.setAlignment(Pos.CENTER_LEFT);
        right.setAlignment(Pos.CENTER_RIGHT);
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        row = new HBox(8, left, gap, right);
        row.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(row);
    }

    /**
     * Returns the live list of content aligned at the left edge.
     *
     * @return ordered application Nodes
     */
    public ObservableList<Node> getLeftItems() {
        return left.getChildren();
    }

    /**
     * Returns the live list of content aligned at the right edge.
     *
     * @return ordered application Nodes
     */
    public ObservableList<Node> getRightItems() {
        return right.getChildren();
    }

    @Override
    protected double computeMinWidth(double height) {
        return snappedLeftInset() + row.minWidth(height) + snappedRightInset();
    }

    @Override
    protected double computePrefWidth(double height) {
        return snappedLeftInset() + row.prefWidth(height) + snappedRightInset();
    }

    @Override
    protected double computeMinHeight(double width) {
        return snappedTopInset() + row.minHeight(contentWidth(width)) + snappedBottomInset();
    }

    @Override
    protected double computePrefHeight(double width) {
        return snappedTopInset() + row.prefHeight(contentWidth(width)) + snappedBottomInset();
    }

    @Override
    protected void layoutChildren() {
        row.resizeRelocate(snappedLeftInset(), snappedTopInset(), contentWidth(getWidth()),
                Math.max(0, getHeight() - snappedTopInset() - snappedBottomInset()));
    }

    private double contentWidth(double width) {
        return width < 0 ? width : Math.max(0, width - snappedLeftInset() - snappedRightInset());
    }
}
