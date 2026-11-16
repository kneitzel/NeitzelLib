package de.neitzel.fx.docking;

import java.util.Objects;

/** Stable relative insertion position in a tab group. */
public sealed interface TabPosition {
    static TabPosition append() { return new Append(); }
    static TabPosition before(DockableId id) { return new Before(id); }
    static TabPosition after(DockableId id) { return new After(id); }
    record Append() implements TabPosition { }
    record Before(DockableId id) implements TabPosition {
        public Before { Objects.requireNonNull(id, "id"); }
    }
    record After(DockableId id) implements TabPosition {
        public After { Objects.requireNonNull(id, "id"); }
    }
}
