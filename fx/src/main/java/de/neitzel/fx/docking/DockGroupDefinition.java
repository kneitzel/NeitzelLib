package de.neitzel.fx.docking;

import javafx.geometry.Side;
import lombok.Getter;
import lombok.Setter;

/** JavaBean definition of a named initial tab group, usable in ordinary FXML. */
@Getter @Setter
public final class DockGroupDefinition implements DockLayoutNode {
    private String destinationId;
    private Side tabSide = Side.TOP;
    private double preferredWidth = 200;
    private double preferredHeight = 150;
    private double minimumWidth = 80;
    private double minimumHeight = 80;
    private boolean persistent = true;
    private boolean collapsible;
    private boolean visibleWhenEmpty = true;
    private boolean releaseSpaceWhenEmpty;
    private SplitDirections splitDirections = SplitDirections.ALL;

    /** Creates a definition for FXML configuration. */
    public DockGroupDefinition() { }

    /** Creates a named destination. */
    public DockGroupDefinition(String destinationId) {
        this.destinationId = destinationId;
    }
}
