package de.neitzel.fx.docking;

import java.util.List;
import java.util.function.BiFunction;

/** Checks a concrete or area fallback against a detached plan before changing its ID order. */
final class DockCandidatePlacement {
    private DockCandidatePlacement() { }

    static DockFailure place(DockLayoutCandidate candidate, Dockable dockable, DockTarget target,
                                     BiFunction<Dockable, ResolvedDockTarget, MoveCheck> check) {
        List<DockingManager.Group> destinations = switch (target) {
            case null -> List.of();
            case DockTarget.Area area -> candidate.groups(area.id());
            case DockTarget.Group concrete -> candidate.groups().stream().filter(group -> group.id().equals(concrete.id())).toList();
            default -> List.of();
        };
        DockFailure firstFailure = null;
        for (DockingManager.Group group : destinations) {
            List<DockableId> order = candidate.tabs(group.id());
            int index = order.size();
            if (target instanceof DockTarget.Group concrete) {
                DockableId reference = switch (concrete.position()) {
                    case TabPosition.Before before -> before.id();
                    case TabPosition.After after -> after.id();
                    case TabPosition.Append ignored -> null;
                };
                if (reference != null) {
                    index = order.indexOf(reference);
                    if (index < 0) return new DockFailure(DockFailure.Code.REFERENCE_TAB_NOT_FOUND,
                            dockable.getId(), "Fallback reference tab is not in its destination", null);
                    if (concrete.position() instanceof TabPosition.After) index++;
                }
            }
            MoveCheck result = check.apply(dockable, new ResolvedDockTarget.Insertion(group.id(), index));
            if (result instanceof MoveCheck.Allowed) {
                candidate.place(dockable.getId(), group.id(), index);
                candidate.select(group.id(), dockable.getId());
                return null;
            }
            if (firstFailure == null && result instanceof MoveCheck.Rejected rejected) {
                firstFailure = affected(rejected.failure(), dockable.getId());
            }
        }
        return firstFailure == null ? new DockFailure(DockFailure.Code.TARGET_NOT_FOUND,
                dockable.getId(), "No eligible destination exists", null) : firstFailure;
    }

    static DockFailure affected(DockFailure failure, DockableId id) {
        return new DockFailure(failure.code(), id, failure.message(), failure.cause(), failure.messageKey());
    }
}
