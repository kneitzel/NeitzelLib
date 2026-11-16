package de.neitzel.fx.docking;

import java.util.Objects;
import javafx.geometry.Side;

/** A destination for a Dockable. */
public sealed interface DockTarget {
    static DockTarget area(String id) { return new Area(new DockAreaId(id)); }
    static DockTarget area(DockAreaId id) { return new Area(id); }
    static DockTarget group(DockGroupId id, TabPosition position) { return new Group(id, position); }
    static DockTarget group(DockGroupId id) { return group(id, TabPosition.append()); }
    static DockTarget split(DockGroupId id, Side side) { return new Split(id, side); }
    static DockTarget floating() { return new Floating(null); }
    static DockTarget floating(javafx.geometry.Rectangle2D bounds) {
        return new Floating(Objects.requireNonNull(bounds, "bounds"));
    }
    static DockTarget returnToMain() { return new ReturnToMain(); }
    record Floating(javafx.geometry.Rectangle2D bounds) implements DockTarget { }
    record ReturnToMain() implements DockTarget { }
    record Split(DockGroupId id, Side side) implements DockTarget {
        public Split { Objects.requireNonNull(id, "id"); Objects.requireNonNull(side, "side"); }
    }
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
