package de.neitzel.fx.docking;

/** Stable identity of an application's predefined destination. */
public record DockAreaId(String value) {
    public DockAreaId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Area ID must not be blank");
    }
}
