package de.neitzel.fx.docking;

import javafx.scene.Node;
import javafx.scene.control.SplitPane;

/** Exposes stable divider styling as soon as a docking split is laid out. */
final class DockSplitPane extends SplitPane {
    @Override protected void layoutChildren() {
        for (Node child : getChildrenUnmodifiable()) {
            if (child.getStyleClass().contains("split-pane-divider")
                    && !child.getStyleClass().contains("dock-divider")) {
                child.getStyleClass().add("dock-divider");
                child.applyCss();
            }
        }
        super.layoutChildren();
    }
}
