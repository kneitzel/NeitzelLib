package de.neitzel.fx.docking;

/** Identity of a live tab group. */
public record DockGroupId(String value) {
    public DockGroupId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Group ID must not be blank");
    }
}
