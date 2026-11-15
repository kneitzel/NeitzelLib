package de.neitzel.fx.docking;

import javafx.geometry.Side;
import java.util.List;

/** Immutable overview published after a completed layout change. */
public record DockLayoutView(List<Window> windows) {
    public DockLayoutView { windows = List.copyOf(windows); }
    public record Window(DockWindowId id, boolean main, List<Area> areas) {
        public Window { areas = List.copyOf(areas); }
    }
    public record Area(DockAreaId id, List<Group> groups) {
        public Area { groups = List.copyOf(groups); }
    }
    public record Group(DockGroupId id, Side tabSide, List<DockableId> dockableIds,
                        DockableId selectedDockableId) {
        public Group { dockableIds = List.copyOf(dockableIds); }
    }
}
