package de.neitzel.fx.docking;

import java.util.Objects;

/** A destination for a Dockable. */
public sealed interface DockTarget {
    static DockTarget area(String id) { return new Area(new DockAreaId(id)); }
    static DockTarget area(DockAreaId id) { return new Area(id); }
    record Area(DockAreaId id) implements DockTarget {
        public Area { Objects.requireNonNull(id, "id"); }
    }
}
