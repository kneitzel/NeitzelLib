package de.neitzel.fx.docking;

import java.util.List;
import java.util.Objects;

/** Immutable description of the Dockables covered by the running close request. */
public record CloseScope(Kind kind, List<DockableId> dockableIds) {
    public CloseScope {
        Objects.requireNonNull(kind, "kind");
        dockableIds = List.copyOf(dockableIds);
    }

    /** The entry point that requested consent. */
    public enum Kind { TAB }
}
