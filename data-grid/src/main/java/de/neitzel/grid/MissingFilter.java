package de.neitzel.grid;

/** Explicit missing/present test; empty strings are present. */
public record MissingFilter(String columnId, boolean missing) implements FilterCondition {
    public MissingFilter {
        Checks.id(columnId);
    }

    public String typeId() {
        return "neitzel.missing";
    }

    public boolean matches(Object value) {
        return (value == null) == missing;
    }
}
