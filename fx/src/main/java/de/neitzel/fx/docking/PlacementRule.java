package de.neitzel.fx.docking;

import java.util.Optional;

/** Application placement policy; evaluating it must not change the layout. */
@FunctionalInterface
public interface PlacementRule {
    /** Empty permits placement; a failure rejects it with an application explanation. */
    Optional<DockFailure> check(Dockable dockable, ResolvedDockTarget destination);
}
