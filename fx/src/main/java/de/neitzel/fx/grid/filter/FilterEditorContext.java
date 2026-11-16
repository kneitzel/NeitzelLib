package de.neitzel.fx.grid.filter;

import de.neitzel.grid.*;

import java.util.*;

public record FilterEditorContext(
        String columnId,
        FilterColumn configuration,
        FilterCondition committed,
        List<FilterCondition> otherFilters,
        FilterColumn.Checklist checklist) {
    public FilterEditorContext {
        Objects.requireNonNull(columnId);
        Objects.requireNonNull(configuration);
        Objects.requireNonNull(checklist);
        otherFilters = List.copyOf(otherFilters);
        if (checklist == FilterColumn.Checklist.INHERIT)
            throw new IllegalArgumentException("Resolve checklist default first");
        if (checklist != FilterColumn.Checklist.DISABLED && configuration.candidates() == null)
            throw new IllegalArgumentException(
                    "Enabled checklist requires a provider for " + columnId);
        if (committed != null && !columnId.equals(committed.columnId()))
            throw new IllegalArgumentException("Wrong committed column");
        if (otherFilters.stream().anyMatch(c -> c.columnId().equals(columnId)))
            throw new IllegalArgumentException("Own filter in candidate context");
    }
}
