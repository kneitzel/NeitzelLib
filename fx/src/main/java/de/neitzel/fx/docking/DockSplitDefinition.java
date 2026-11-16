package de.neitzel.fx.docking;

import javafx.geometry.Orientation;
import lombok.Getter;
import lombok.Setter;

/** Java/FXML definition of two adjacent groups or nested branches. */
@Getter @Setter
public final class DockSplitDefinition implements DockLayoutNode {
    private String destinationId;
    private Orientation orientation = Orientation.HORIZONTAL;
    private double position = .5;
    private DockLayoutNode first;
    private DockLayoutNode second;
}
