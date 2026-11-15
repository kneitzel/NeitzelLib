package de.neitzel.fx.docking;

/** Concrete placement checked against the current layout, without allocating IDs. */
public sealed interface ResolvedDockTarget {
    record Split(DockGroupId groupId, javafx.geometry.Side side) implements ResolvedDockTarget { }
    record Insertion(DockGroupId groupId, int index) implements ResolvedDockTarget { }
}
