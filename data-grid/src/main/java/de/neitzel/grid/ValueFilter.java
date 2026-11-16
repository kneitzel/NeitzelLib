package de.neitzel.grid;

import java.util.Set;

/**
 * Explicit allowed values. An empty set with includeMissing=false matches nothing, not everything.
 */
public record ValueFilter(String columnId, Set<FilterValue> values, boolean includeMissing)
        implements FilterCondition {
    public ValueFilter {
        Checks.id(columnId);
        values = Set.copyOf(values);
    }

    public String typeId() {
        return "neitzel.values";
    }

    public boolean matches(FilterValue value) {
        return value == null ? includeMissing : values.contains(value);
    }
}
