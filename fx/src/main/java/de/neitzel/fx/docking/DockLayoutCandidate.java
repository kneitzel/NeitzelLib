package de.neitzel.fx.docking;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/** Detached controls and ID-only placement plans shared by restore and live reset. */
final class DockLayoutCandidate {
    final LayoutSnapshot.Window window;
    final DockLayoutTree tree;
    private final Map<DockGroupId, DockingManager.Group> groups = new LinkedHashMap<>();
    private final Map<DockGroupId, List<DockableId>> orders = new LinkedHashMap<>();
    private final Map<DockGroupId, DockableId> selections = new LinkedHashMap<>();

    DockLayoutCandidate(LayoutSnapshot.Window window, Function<LayoutSnapshot.Group, DockingManager.Group> factory,
                        Runnable geometryChanged, Predicate<DockGroupId> canResize) {
        this.window = window;
        tree = new DockLayoutTree(geometryChanged, canResize);
        for (LayoutSnapshot.Root root : window.roots()) {
            tree.addSnapshot(root.branch(), node -> { }, state -> {
                DockingManager.Group group = factory.apply(state);
                groups.put(group.id(), group);
                orders.put(group.id(), new ArrayList<>(state.tabs()));
                selections.put(group.id(), state.selected());
                return group;
            });
        }
    }

    List<LayoutSnapshot.Root> roots() {
        Map<DockAreaId, javafx.geometry.Side> edges = new LinkedHashMap<>();
        window.roots().stream().filter(root -> root.placement() != LayoutSnapshot.Placement.CENTER)
                .forEach(root -> root.branch().aliases().forEach(alias -> edges.put(alias,
                        javafx.geometry.Side.valueOf(root.placement().name()))));
        return tree.snapshotRoots(edges).stream().map(root -> {
            LayoutSnapshot.Root original = window.roots().stream()
                    .filter(candidate -> candidate.placement() == root.placement()).findFirst().orElseThrow();
            return new LayoutSnapshot.Root(root.placement(), original.preferredWidth(), original.preferredHeight(), root.branch());
        }).toList();
    }

    void normalize() {
        for (DockGroupId id : List.copyOf(groups.keySet())) {
            if (!orders.containsKey(id) || !orders.get(id).isEmpty()) continue;
            for (DockGroupId removed : tree.normalize(id, group -> orders.get(group.id()).isEmpty())) {
                groups.remove(removed);
                orders.remove(removed);
                selections.remove(removed);
            }
        }
    }

    List<DockingManager.Group> groups() { return List.copyOf(groups.values()); }
    List<DockingManager.Group> groups(DockAreaId area) { return tree.groups(area); }
    List<DockableId> tabs(DockGroupId group) { return List.copyOf(orders.get(group)); }
    DockableId selected(DockGroupId group) { return selections.get(group); }
    void select(DockGroupId group, DockableId selected) { selections.put(group, selected); }
    void place(DockableId id, DockGroupId group, int index) {
        orders.values().forEach(order -> order.remove(id));
        List<DockableId> order = orders.get(group);
        order.add(Math.max(0, Math.min(order.size(), index)), id);
    }
}
