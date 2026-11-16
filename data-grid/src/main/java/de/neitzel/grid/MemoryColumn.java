package de.neitzel.grid;

import java.util.*;
import java.util.function.*;

/**
 * Explicit scalar mapping; custom compilers validate their condition and produce local evaluators.
 */
public record MemoryColumn<T>(
        String id,
        Kind kind,
        Function<T, FilterValue> read,
        Map<String, Function<FilterCondition, Predicate<FilterValue>>> customFilters) {
    public enum Kind {
        TEXT,
        INTEGER,
        DECIMAL,
        BOOLEAN
    }

    public MemoryColumn(String id, Kind kind, Function<T, FilterValue> read) {
        this(id, kind, read, Map.of());
    }

    public MemoryColumn {
        Checks.id(id);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(read);
        customFilters = Map.copyOf(customFilters);
        customFilters.keySet().forEach(Checks::id);
    }

    void validate(FilterValue value) {
        if (value == null) return;
        boolean valid =
                switch (kind) {
                    case TEXT -> value instanceof FilterValue.Text;
                    case INTEGER -> value instanceof FilterValue.IntegerValue;
                    case DECIMAL -> value instanceof FilterValue.Decimal;
                    case BOOLEAN -> value instanceof FilterValue.BooleanValue;
                };
        if (!valid) throw new IllegalArgumentException("Value type does not match column " + id);
    }
}
