package de.neitzel.grid;

import java.util.*;

/** Stable typed identity, independent of labels, page position and row identity. */
public record GroupPath(List<Segment> segments) {
    public record Segment(String columnId, FilterValue value) {
        public Segment {
            Checks.id(columnId);
        }
    }

    public GroupPath {
        segments = List.copyOf(segments);
        if (segments.isEmpty()) throw new IllegalArgumentException("Empty group path");
        Set<String> seen = new HashSet<>();
        for (var segment : segments)
            if (!seen.add(segment.columnId()))
                throw new IllegalArgumentException("Repeated group column");
    }

    public GroupPath child(String column, FilterValue value) {
        var next = new ArrayList<>(segments);
        next.add(new Segment(column, value));
        return new GroupPath(next);
    }
}
