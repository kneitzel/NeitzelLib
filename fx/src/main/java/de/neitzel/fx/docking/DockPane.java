package de.neitzel.fx.docking;

import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.geometry.Bounds;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import lombok.Getter;
import lombok.Setter;

/** Embeddable docking area whose definition is captured when a manager attaches. */
public final class DockPane extends Region {
    @Getter @Setter private DockLayout initialLayout = new DockLayout();
    final BorderPane surface = new BorderPane();
    private final Rectangle dropIndicator = new Rectangle();
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

    @Override protected void layoutChildren() { surface.resizeRelocate(0, 0, getWidth(), getHeight()); }
    @Override protected double computePrefWidth(double height) { return surface.prefWidth(height); }
    @Override protected double computePrefHeight(double width) { return surface.prefHeight(width); }
    @Override protected double computeMinWidth(double height) { return surface.minWidth(height); }
    @Override protected double computeMinHeight(double width) { return surface.minHeight(width); }
}
