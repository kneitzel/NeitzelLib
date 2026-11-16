package de.neitzel.fx.docking;

/** A named initial destination, containing either a group or a binary split. */
public sealed interface DockLayoutNode permits DockGroupDefinition, DockSplitDefinition {
    String getDestinationId();
    void setDestinationId(String id);
}
