package de.neitzel.grid;

import java.util.*;

/**
 * Immutable query. Filters combine with AND; sort keys include the source's stable-key tie-breaker.
 */
public record GridQuery(List<FilterCondition> filters, List<SortKey> sort, PageRequest page) {
    public GridQuery {
        filters = List.copyOf(filters);
        sort = List.copyOf(sort);
        Objects.requireNonNull(page);
        Set<String> ids = new HashSet<>();
        for (var f : filters) {
            Checks.id(f.columnId());
            Checks.id(f.typeId());
            if (!ids.add(f.columnId()))
                throw new IllegalArgumentException("Multiple filters for " + f.columnId());
        }
        ids.clear();
        for (var key : sort)
            if (!ids.add(key.columnId())) throw new IllegalArgumentException("Duplicate sort key");
    }
}
