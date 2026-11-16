package de.neitzel.grid;

import java.util.*;

/** A materialized result with exact filtered total or an explicit next-page indication. */
public record PageResult<K, T>(
        PageRequest request, List<GridRow<K, T>> rows, OptionalLong total, boolean hasNext) {
    public PageResult {
        Objects.requireNonNull(request);
        rows = List.copyOf(rows);
        Objects.requireNonNull(total);
        Set<K> keys = new HashSet<>();
        for (var row : rows)
            if (!keys.add(row.key())) throw new IllegalArgumentException("Duplicate row key");
        if (total.isPresent() && total.getAsLong() < 0)
            throw new IllegalArgumentException("Negative total");
        if (request instanceof PageRequest.Paged p) {
            if (rows.size() > p.pageSize()) throw new IllegalArgumentException("Too many rows");
            if (total.isPresent()) {
                long remaining = Math.max(0, total.getAsLong() - p.offset());
                if (rows.size() != Math.min(remaining, p.pageSize())
                        || hasNext != (remaining > p.pageSize()))
                    throw new IllegalArgumentException("Rows/count/navigation disagree");
            } else if (hasNext && rows.size() != p.pageSize())
                throw new IllegalArgumentException("Next page requires a full page");
        } else if (hasNext || (total.isPresent() && total.getAsLong() != rows.size()))
            throw new IllegalArgumentException("Invalid unpaged result");
    }
}
