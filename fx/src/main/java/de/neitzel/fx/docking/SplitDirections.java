package de.neitzel.fx.docking;

import javafx.geometry.Side;

/** Directions in which a target group permits an adjacent split. */
public enum SplitDirections {
    NONE, HORIZONTAL, VERTICAL, ALL;

    public boolean allows(Side side) {
        return this == ALL || (this == HORIZONTAL && side.isVertical())
                || (this == VERTICAL && side.isHorizontal());
    }
}
