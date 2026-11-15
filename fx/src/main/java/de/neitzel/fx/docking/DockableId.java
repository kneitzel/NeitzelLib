package de.neitzel.fx.docking;

/** Stable application identity of one Dockable. */
public record DockableId(String value) {
    public DockableId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Dockable ID must not be blank");
    }
}
