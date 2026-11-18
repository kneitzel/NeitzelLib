package de.neitzel.grid;

import java.util.*;

/** Owner-thread expansion history retaining scalar paths, never rows/models. */
public final class GroupExpansion {
    private final LinkedHashMap<GroupPath, Boolean> choices = new LinkedHashMap<>();
    private Set<GroupPath> current = Set.of();
    private boolean defaultCollapsed;
    private int inactiveLimit;

    public GroupExpansion(boolean defaultCollapsed, int inactiveLimit) {
        configure(defaultCollapsed, inactiveLimit);
    }

    public void configure(boolean collapsed, int limit) {
        if (limit < 0) throw new IllegalArgumentException("Negative expansion history capacity");
        defaultCollapsed = collapsed;
        inactiveLimit = limit;
        trim();
    }

    public boolean expanded(GroupPath path) {
        return choices.getOrDefault(path, !defaultCollapsed);
    }

    public void set(GroupPath path, boolean expanded) {
        choices.remove(path);
        choices.put(path, expanded);
        trim();
    }

    public void current(Collection<GroupPath> paths) {
        current = Set.copyOf(paths);
        for (var path : paths)
            if (choices.containsKey(path)) {
                boolean choice = choices.remove(path);
                choices.put(path, choice);
            }
        trim();
    }

    public List<GroupingSetup.Choice> snapshot() {
        return choices.entrySet().stream()
                .map(e -> new GroupingSetup.Choice(e.getKey(), e.getValue()))
                .toList();
    }

    public void restore(List<GroupingSetup.Choice> saved) {
        choices.clear();
        saved.forEach(c -> choices.put(c.path(), c.expanded()));
        trim();
    }

    public void reset() {
        choices.clear();
    }

    private void trim() {
        int inactive = (int) choices.keySet().stream().filter(p -> !current.contains(p)).count();
        for (var iterator = choices.keySet().iterator();
                inactive > inactiveLimit && iterator.hasNext(); ) {
            if (!current.contains(iterator.next())) {
                iterator.remove();
                inactive--;
            }
        }
    }
}
