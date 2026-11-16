package de.neitzel.grid;

import java.util.Objects;

/** Sort by mapped column, with missing values last in either direction. */
public record SortKey(String columnId, Direction direction) {
    public enum Direction {
        ASCENDING,
        DESCENDING
    }

    public SortKey {
        Checks.id(columnId);
        Objects.requireNonNull(direction);
    }
}
