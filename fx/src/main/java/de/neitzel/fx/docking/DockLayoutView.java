package de.neitzel.fx.docking;

import javafx.geometry.Side;
import java.util.List;

/** Immutable overview published after a completed layout change. */
public record DockLayoutView(List<Window> windows) {
    public DockLayoutView { windows = List.copyOf(windows); }
    public record Window(DockWindowId id, boolean main, List<Area> areas) {
        public Window { areas = List.copyOf(areas); }
    }
    /** Preferred side content extent; -1 means that dimension follows the available space. */
    public record Area(DockAreaId id, List<Group> groups, LayoutNode root,
                       double preferredWidth, double preferredHeight) {
        public Area { groups = List.copyOf(groups); }
        public Area(DockAreaId id, List<Group> groups, LayoutNode root) {
            this(id, groups, root, -1, -1);
        }
        public Area(DockAreaId id, List<Group> groups) {
            this(id, groups, groups.isEmpty() ? null : groups.getFirst());
        }
    }
    public sealed interface LayoutNode { }
    public record Split(javafx.geometry.Orientation orientation, double position,
                        LayoutNode first, LayoutNode second) implements LayoutNode { }
    public record Group(DockGroupId id, Side tabSide, List<DockableId> dockableIds,
                        DockableId selectedDockableId) implements LayoutNode {
        public Group { dockableIds = List.copyOf(dockableIds); }
    }
}
