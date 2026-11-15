package de.neitzel.fx.docking;

/** Identity of a window managed by a docking manager. */
public record DockWindowId(String value) {
    public DockWindowId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Window ID must not be blank");
    }
}
