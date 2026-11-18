package de.neitzel.grid;

import java.util.*;

/** Committed grouping preferences. Choices are ordered least-recently to most-recently used. */
public record GroupingSetup(List<String> columns, List<Choice> choices) {
    public record Choice(GroupPath path, boolean expanded) {
        public Choice {
            Objects.requireNonNull(path);
        }
    }

    public GroupingSetup {
        columns = List.copyOf(columns);
        choices = List.copyOf(choices);
        Set<String> ids = new HashSet<>();
        for (String id : columns) {
            Checks.id(id);
            if (!ids.add(id)) throw new IllegalArgumentException("Repeated grouping column");
        }
        Set<GroupPath> paths = new HashSet<>();
        for (var choice : choices)
            if (!paths.add(choice.path()))
                throw new IllegalArgumentException("Repeated expansion choice");
    }

    public static GroupingSetup empty() {
        return new GroupingSetup(List.of(), List.of());
    }
}
