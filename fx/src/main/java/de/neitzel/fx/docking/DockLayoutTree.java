package de.neitzel.fx.docking;

import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.Cursor;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Owns the visual split hierarchy and the stable predefined destinations into it. */
final class DockLayoutTree {
    private sealed interface Branch permits Leaf, Split {
        Node node();
        List<DockingManager.Group> groups();
        DockLayoutView.LayoutNode view();
    }

    private record Leaf(DockingManager.Group group) implements Branch {
        @Override public Node node() { return ((DockGroupPane) group.tabs()).surface(); }
        @Override public List<DockingManager.Group> groups() { return List.of(group); }
        @Override public DockLayoutView.LayoutNode view() {
            Tab selected = group.tabs().getSelectionModel().getSelectedItem();
            return new DockLayoutView.Group(group.id(), group.tabs().getSide(),
                    group.tabs().getTabs().stream().map(tab -> (DockableId) tab.getUserData()).toList(),
                    selected == null ? null : (DockableId) selected.getUserData(),
                    group.tabs() instanceof DockGroupPane pane && pane.isCollapsed(), group.definition().getMode());
        }
    }

    private static final class Split implements Branch {
        private Branch first;
        private Branch second;
        private final SplitPane pane = new SplitPane();
        private double position = .5;
        private boolean dragging;
        private boolean collapsedAllocation;
        Split(Branch first, Branch second, Orientation orientation) {
            this.first = first;
            this.second = second;
            pane.setOrientation(orientation);
        }
        @Override public Node node() { return pane; }
        @Override public List<DockingManager.Group> groups() {
            var groups = new ArrayList<>(first.groups());
            groups.addAll(second.groups());
            return List.copyOf(groups);
        }
        @Override public DockLayoutView.LayoutNode view() {
            return new DockLayoutView.Split(pane.getOrientation(), position, first.view(), second.view());
        }
    }

    private static final class Root {
        private Branch branch;
        private final Consumer<Node> place;
        Root(Branch branch, Consumer<Node> place) { this.branch = branch; this.place = place; }
    }

    private final Runnable geometryChanged;
    private final java.util.function.Predicate<DockGroupId> canResize;

    DockLayoutTree(Runnable geometryChanged, java.util.function.Predicate<DockGroupId> canResize) {
        this.geometryChanged = geometryChanged;
        this.canResize = canResize;
    }

    private final Map<DockAreaId, Branch> areas = new LinkedHashMap<>();
    private final List<Root> roots = new ArrayList<>();

    void addArea(DockLayoutNode definition, Consumer<Node> place,
                 java.util.function.Function<DockGroupDefinition, DockingManager.Group> groupFactory) {
        Branch branch = build(definition, groupFactory);
        roots.add(new Root(branch, place));
        render(branch);
        place.accept(branch.node());
    }

    private Branch build(DockLayoutNode definition,
                         java.util.function.Function<DockGroupDefinition, DockingManager.Group> groupFactory) {
        DockAreaId id = new DockAreaId(definition.getDestinationId());
        areas.put(id, null);
        Branch branch;
        if (definition instanceof DockGroupDefinition group) {
            branch = new Leaf(groupFactory.apply(group));
        } else {
            DockSplitDefinition splitDefinition = (DockSplitDefinition) definition;
            Split split = split(build(splitDefinition.getFirst(), groupFactory),
                    build(splitDefinition.getSecond(), groupFactory), splitDefinition.getOrientation());
            split.position = splitDefinition.getPosition();
            branch = split;
        }
        areas.put(id, branch);
        return branch;
    }

    List<DockingManager.Group> groups(DockAreaId id) {
        Branch branch = areas.get(id);
        return branch == null ? List.of() : branch.groups();
    }

    void split(DockingManager.Group target, DockingManager.Group added, Side side) {
        Branch old = find(target.id());
        Branch leaf = new Leaf(added);
        Split split = side == Side.LEFT || side == Side.TOP
                ? split(leaf, old, side.isVertical() ? Orientation.HORIZONTAL : Orientation.VERTICAL)
                : split(old, leaf, side.isVertical() ? Orientation.HORIZONTAL : Orientation.VERTICAL);
        detach();
        replace(old, split);
        render();
    }

    private Split split(Branch first, Branch second, Orientation orientation) {
        Split split = new Split(first, second, orientation);
        split.pane.getStyleClass().add("dock-split");
        split.pane.addEventFilter(MouseEvent.ANY, event -> {
            Node divider = divider(event.getPickResult().getIntersectedNode(), split.pane);
            if (divider == null && !split.dragging) return;
            if (divider != null) {
                divider.setCursor(orientation == Orientation.HORIZONTAL ? Cursor.H_RESIZE : Cursor.V_RESIZE);
                if (!divider.getStyleClass().contains("dock-divider")) divider.getStyleClass().add("dock-divider");
            }
            boolean geometryEvent = event.getEventType() == MouseEvent.MOUSE_PRESSED
                    || event.getEventType() == MouseEvent.MOUSE_DRAGGED || event.getEventType() == MouseEvent.MOUSE_RELEASED;
            if (geometryEvent && split.collapsedAllocation) {
                event.consume();
                return;
            }
            if (geometryEvent && split.groups().stream().anyMatch(group -> !canResize.test(group.id()))) {
                if (split.dragging) split.pane.setDividerPositions(split.position);
                split.dragging = false;
                event.consume();
                return;
            }
            if (event.getEventType() == MouseEvent.MOUSE_PRESSED && event.getButton() == MouseButton.PRIMARY) {
                split.dragging = true;
            } else if (event.getEventType() == MouseEvent.MOUSE_RELEASED && split.dragging) {
                split.dragging = false;
                double position = split.pane.getDividers().getFirst().getPosition();
                if (Double.compare(split.position, position) != 0) {
                    split.position = position;
                    geometryChanged.run();
                }
            }
        });
        return split;
    }

    private static Node divider(Node node, SplitPane pane) {
        Node divider = null;
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof SplitPane) return current == pane ? divider : null;
            if (current.getStyleClass().contains("split-pane-divider")) divider = current;
        }
        return null;
    }

    boolean geometryAffects(DockGroupId id, java.util.function.Predicate<DockingManager.Group> protectedGroup) {
        for (Root root : roots) {
            Branch affected = geometryBranch(root.branch, null, id);
            if (affected != null) return affected.groups().stream().anyMatch(protectedGroup);
        }
        return false;
    }

    private static Branch geometryBranch(Branch branch, Branch parent, DockGroupId id) {
        if (branch instanceof Leaf leaf) {
            if (!leaf.group.id().equals(id)) return null;
            return parent == null ? leaf : parent;
        }
        Split split = (Split) branch;
        Branch found = geometryBranch(split.first, split, id);
        return found == null ? geometryBranch(split.second, split, id) : found;
    }

    boolean normalizationAffects(DockGroupId source, DockGroupId destination,
                                 java.util.function.Predicate<DockingManager.Group> protectedGroup) {
        if (find(source).groups().getFirst().tabs().getTabs().size() != 1) return false;
        return roots.stream().anyMatch(root -> normalizationAffects(root.branch, source, destination, protectedGroup));
    }

    private static boolean normalizationAffects(Branch branch, DockGroupId source, DockGroupId destination,
                                                java.util.function.Predicate<DockingManager.Group> protectedGroup) {
        if (!(branch instanceof Split split) || find(branch, source) == null) return false;
        if ((emptyAfterMove(split.first, source, destination) || emptyAfterMove(split.second, source, destination))
                && split.groups().stream().anyMatch(protectedGroup)) return true;
        return normalizationAffects(split.first, source, destination, protectedGroup)
                || normalizationAffects(split.second, source, destination, protectedGroup);
    }

    private static boolean emptyAfterMove(Branch branch, DockGroupId source, DockGroupId destination) {
        return branch.groups().stream().allMatch(group -> group.tabs().getTabs().size()
                - (group.id().equals(source) ? 1 : 0) + (group.id().equals(destination) ? 1 : 0) == 0);
    }

    List<DockGroupId> normalize(DockGroupId source) {
        Branch emptied = find(source);
        if (!empty(emptied)) return List.of();
        detach();
        List<DockGroupId> removed = new ArrayList<>();
        for (var iterator = roots.iterator(); iterator.hasNext();) {
            Root root = iterator.next();
            boolean affected = includes(root.branch, emptied);
            root.branch = normalize(root.branch, emptied, removed);
            if (affected && root.branch instanceof Leaf leaf && empty(leaf)
                    && !leaf.group.definition().isPersistent()) {
                areas.entrySet().removeIf(entry -> entry.getValue() == leaf);
                removed.add(leaf.group.id());
                iterator.remove();
            }
        }
        render();
        return List.copyOf(removed);
    }

    private Branch normalize(Branch branch, Branch emptied, List<DockGroupId> removed) {
        if (!(branch instanceof Split split) || !includes(branch, emptied)) return branch;
        split.first = normalize(split.first, emptied, removed);
        split.second = normalize(split.second, emptied, removed);
        boolean firstEmpty = empty(split.first);
        boolean secondEmpty = empty(split.second);
        if (!firstEmpty && !secondEmpty) return split;
        Branch survivor = firstEmpty && !secondEmpty ? split.second : split.first;
        Branch discarded = survivor == split.first ? split.second : split.first;
        discarded.groups().forEach(group -> removed.add(group.id()));
        areas.replaceAll((id, destination) -> destination == split || includes(discarded, destination)
                ? survivor : destination);
        return survivor;
    }

    private static boolean includes(Branch branch, Branch target) {
        if (branch == target) return true;
        return branch instanceof Split split && (includes(split.first, target) || includes(split.second, target));
    }

    private static boolean empty(Branch branch) {
        return branch.groups().stream().allMatch(group -> group.tabs().getTabs().isEmpty());
    }

    /** Reuses the live overlay dimensions when its group starts reserving space again. */
    void restoreGroupSize(DockGroupId id, double width, double height) {
        for (Root root : roots) {
            restoreGroupSize(nearestSplit(root.branch, id, Orientation.HORIZONTAL), id, width);
            restoreGroupSize(nearestSplit(root.branch, id, Orientation.VERTICAL), id, height);
        }
    }

    private static Split nearestSplit(Branch branch, DockGroupId id, Orientation orientation) {
        if (!(branch instanceof Split split) || find(branch, id) == null) return null;
        Branch child = find(split.first, id) != null ? split.first : split.second;
        Split nested = nearestSplit(child, id, orientation);
        return nested != null ? nested : split.pane.getOrientation() == orientation ? split : null;
    }

    private static void restoreGroupSize(Split split, DockGroupId id, double extent) {
        if (split == null || extent <= 0) return;
        boolean horizontal = split.pane.getOrientation() == Orientation.HORIZONTAL;
        double span = horizontal ? split.pane.getWidth() : split.pane.getHeight();
        if (span <= 0) return;
        Node divider = split.pane.lookupAll(".split-pane-divider").stream()
                .filter(node -> divider(node, split.pane) == node).findFirst().orElse(null);
        double dividerExtent = divider == null ? 6 : horizontal
                ? divider.getLayoutBounds().getWidth() : divider.getLayoutBounds().getHeight();
        boolean first = find(split.first, id) != null;
        double inset = horizontal ? first ? split.pane.getInsets().getLeft() : split.pane.getInsets().getRight()
                : first ? split.pane.getInsets().getTop() : split.pane.getInsets().getBottom();
        double position = (extent + inset + dividerExtent / 2) / span;
        if (!first) position = 1 - position;
        position = Math.max(0, Math.min(1, position));
        if (Math.abs(position - split.position) * span < 1) return;
        split.position = position;
        if (!split.collapsedAllocation) split.pane.setDividerPositions(position);
    }

    void captureGeometry() {
        for (Root root : roots) allocateCollapsed(root.branch);
        for (Root root : roots) captureGeometry(root.branch);
    }

    private static void allocateCollapsed(Branch branch) {
        if (!(branch instanceof Split split)) return;
        boolean firstCollapsed = DockGroupPane.allCollapsed(split.first.node());
        boolean secondCollapsed = DockGroupPane.allCollapsed(split.second.node());
        boolean allocate = firstCollapsed || secondCollapsed;
        SplitPane.setResizableWithParent(split.first.node(), !firstCollapsed);
        SplitPane.setResizableWithParent(split.second.node(), !secondCollapsed);
        if (allocate) {
            split.collapsedAllocation = true;
            split.dragging = false;
            boolean horizontal = split.pane.getOrientation() == Orientation.HORIZONTAL;
            double span = horizontal ? split.pane.getWidth() : split.pane.getHeight();
            double firstExtent = horizontal ? split.first.node().minWidth(-1) : split.first.node().minHeight(-1);
            double secondExtent = horizontal ? split.second.node().minWidth(-1) : split.second.node().minHeight(-1);
            if (span > 0) {
                double position = firstCollapsed && secondCollapsed ? firstExtent / (firstExtent + secondExtent)
                        : firstCollapsed ? (firstExtent + 4) / span : 1 - (secondExtent + 4) / span;
                split.pane.setDividerPositions(Math.max(0, Math.min(1, position)));
            }
        } else if (split.collapsedAllocation) {
            split.collapsedAllocation = false;
            split.pane.setDividerPositions(split.position);
        }
        split.pane.layout();
        for (Node node : split.pane.lookupAll(".split-pane-divider")) {
            if (divider(node, split.pane) == node) node.setVisible(!allocate);
        }
        allocateCollapsed(split.first);
        allocateCollapsed(split.second);
    }

    private static void captureGeometry(Branch branch) {
        if (branch instanceof Split split) {
            if (!split.dragging && !split.collapsedAllocation && split.pane.getWidth() > 0 && split.pane.getHeight() > 0
                    && !split.pane.getDividers().isEmpty()) {
                split.position = split.pane.getDividers().getFirst().getPosition();
            }
            captureGeometry(split.first);
            captureGeometry(split.second);
        }
    }

    List<DockLayoutView.Area> view() {
        return areas.entrySet().stream().map(entry -> new DockLayoutView.Area(entry.getKey(),
                entry.getValue().groups().stream().map(group -> (DockLayoutView.Group) new Leaf(group).view()).toList(),
                entry.getValue().view())).toList();
    }

    private Branch find(DockGroupId id) {
        for (Root root : roots) {
            Branch found = find(root.branch, id);
            if (found != null) return found;
        }
        throw new IllegalArgumentException("Group is not in the layout: " + id);
    }

    private static Branch find(Branch branch, DockGroupId id) {
        if (branch instanceof Leaf leaf) return leaf.group.id().equals(id) ? leaf : null;
        Split split = (Split) branch;
        Branch found = find(split.first, id);
        return found == null ? find(split.second, id) : found;
    }

    private void replace(Branch old, Branch replacement) {
        for (Root root : roots) root.branch = replace(root.branch, old, replacement);
        areas.replaceAll((id, branch) -> branch == old ? replacement : branch);
    }

    private static Branch replace(Branch branch, Branch old, Branch replacement) {
        if (branch == old) return replacement;
        if (branch instanceof Split split) {
            split.first = replace(split.first, old, replacement);
            split.second = replace(split.second, old, replacement);
        }
        return branch;
    }

    private void detach() {
        for (Root root : roots) {
            root.place.accept(null);
            detach(root.branch);
        }
    }

    private static void detach(Branch branch) {
        if (branch instanceof Split split) {
            split.pane.getItems().clear();
            detach(split.first);
            detach(split.second);
        }
    }

    private void render() {
        for (Root root : roots) {
            render(root.branch);
            root.place.accept(root.branch.node());
        }
    }

    private static void render(Branch branch) {
        if (branch instanceof Split split) {
            render(split.first);
            render(split.second);
            split.pane.getItems().setAll(split.first.node(), split.second.node());
            split.pane.setDividerPositions(split.position);
        }
    }
}
