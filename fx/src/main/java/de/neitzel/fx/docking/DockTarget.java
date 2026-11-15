package de.neitzel.fx.docking;

import java.util.Objects;

/** A destination for a Dockable. */
public sealed interface DockTarget {
    static DockTarget area(String id) { return new Area(new DockAreaId(id)); }
    static DockTarget area(DockAreaId id) { return new Area(id); }
    static DockTarget group(DockGroupId id, TabPosition position) { return new Group(id, position); }
    static DockTarget group(DockGroupId id) { return group(id, TabPosition.append()); }
    record Area(DockAreaId id) implements DockTarget {
        public Area { Objects.requireNonNull(id, "id"); }
    }
    record Group(DockGroupId id, TabPosition position) implements DockTarget {
        public Group {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(position, "position");
        }
    }
}
