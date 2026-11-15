package de.neitzel.fx.docking;

import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import lombok.Getter;
import lombok.Setter;

/** Embeddable docking area whose definition is captured when a manager attaches. */
public final class DockPane extends Region {
    @Getter @Setter private DockLayout initialLayout = new DockLayout();
    final BorderPane surface = new BorderPane();
    DockingManager manager;

    public DockPane() {
        getStyleClass().add("dock-pane");
        getChildren().add(surface);
    }

    @Override protected void layoutChildren() { surface.resizeRelocate(0, 0, getWidth(), getHeight()); }
    @Override protected double computePrefWidth(double height) { return surface.prefWidth(height); }
    @Override protected double computePrefHeight(double width) { return surface.prefHeight(width); }
    @Override protected double computeMinWidth(double height) { return surface.minWidth(height); }
    @Override protected double computeMinHeight(double width) { return surface.minHeight(width); }
}
