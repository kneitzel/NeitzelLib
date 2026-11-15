package de.neitzel.fx.docking;

import javafx.geometry.Side;
import lombok.Getter;
import lombok.Setter;

/** JavaBean definition of a named initial tab group, usable in ordinary FXML. */
@Getter @Setter
public final class DockGroupDefinition {
    private String destinationId;
    private Side tabSide = Side.TOP;
    private double preferredWidth = 200;
    private double preferredHeight = 150;

    /** Creates a definition for FXML configuration. */
    public DockGroupDefinition() { }

    /** Creates a named destination. */
    public DockGroupDefinition(String destinationId) {
        this.destinationId = destinationId;
    }
}
