package de.neitzel.grid;

import java.util.*;

/** Pure current-page projection. Capture all eligible scalar values before publishing a page. */
public final class GroupProjection {
    private GroupProjection() {}

    public record Value(FilterValue key, String label) {
        public Value {
            Objects.requireNonNull(label);
        }
    }

    public record Captured<K, T>(GridRow<K, T> row, Map<String, Value> values) {
        public Captured {
            Objects.requireNonNull(row);
            values = Map.copyOf(values);
        }
    }

    public record Snapshot<K, T>(List<Captured<K, T>> rows, Map<String, GroupColumn<T>> columns) {
        public Snapshot {
            rows = List.copyOf(rows);
            columns = Map.copyOf(columns);
        }
    }

    public record Node<K, T>(
            GroupPath path, String label, List<Node<K, T>> children, List<GridRow<K, T>> rows) {
        public Node {
            children = List.copyOf(children);
            rows = List.copyOf(rows);
        }

        public int count() {
            return rows.size();
        }
    }

    public static <K, T> Snapshot<K, T> capture(
            List<GridRow<K, T>> rows, Collection<GroupColumn<T>> columns) {
        Map<String, GroupColumn<T>> mappings = new LinkedHashMap<>();
        for (var c : columns)
            if (mappings.putIfAbsent(c.id(), c) != null)
                throw new IllegalArgumentException("Duplicate group mapping");
        List<Captured<K, T>> captured = new ArrayList<>();
        for (var row : rows) {
            Map<String, Value> values = new LinkedHashMap<>();
            for (var c : columns) {
                FilterValue key = c.read().apply(row.model());
                c.validate(key);
                values.put(c.id(), new Value(key, c.label().apply(key)));
            }
            captured.add(new Captured<>(row, values));
        }
        return new Snapshot<>(captured, mappings);
    }

    public static <K, T> List<Node<K, T>> project(Snapshot<K, T> snapshot, List<String> order) {
        if (new HashSet<>(order).size() != order.size()
                || !snapshot.columns().keySet().containsAll(order))
            throw new IllegalArgumentException("Unknown/repeated grouping column");
        return build(snapshot.rows(), order, 0, null);
    }

    private static <K, T> List<Node<K, T>> build(
            List<Captured<K, T>> rows, List<String> order, int depth, GroupPath parent) {
        if (depth == order.size()) return List.of();
        String id = order.get(depth);
        Map<FilterValue, List<Captured<K, T>>> buckets = new LinkedHashMap<>();
        for (var row : rows)
            buckets.computeIfAbsent(row.values().get(id).key(), ignored -> new ArrayList<>())
                    .add(row);
        List<FilterValue> keys = new ArrayList<>(buckets.keySet());
        keys.sort(GroupColumn::compare);
        List<Node<K, T>> nodes = new ArrayList<>();
        for (var key : keys) {
            var bucket = buckets.get(key);
            String label = bucket.getFirst().values().get(id).label();
            if (bucket.stream().anyMatch(r -> !r.values().get(id).label().equals(label)))
                throw new IllegalArgumentException("Inconsistent label for group value in " + id);
            GroupPath path =
                    parent == null
                            ? new GroupPath(List.of(new GroupPath.Segment(id, key)))
                            : parent.child(id, key);
            nodes.add(
                    new Node<>(
                            path,
                            label,
                            build(bucket, order, depth + 1, path),
                            bucket.stream().map(Captured::row).toList()));
        }
        return List.copyOf(nodes);
    }

    public static <K, T> List<GroupPath> paths(List<Node<K, T>> nodes) {
        List<GroupPath> paths = new ArrayList<>();
        for (var node : nodes) {
            paths.add(node.path());
            paths.addAll(paths(node.children()));
        }
        return paths;
    }
}
