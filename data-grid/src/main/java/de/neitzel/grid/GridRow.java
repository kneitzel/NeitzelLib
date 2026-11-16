package de.neitzel.grid;

import java.util.Objects;

/**
 * Row identity is independent of page position. The model is application-owned, not deep-copied.
 */
public record GridRow<K, T>(K key, T model) {
    public GridRow {
        Objects.requireNonNull(key);
        Objects.requireNonNull(model);
    }
}
