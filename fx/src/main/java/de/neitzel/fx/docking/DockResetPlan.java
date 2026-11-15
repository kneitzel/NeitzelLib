package de.neitzel.fx.docking;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.function.BiFunction;

/** Plans initial destinations using IDs only; live content is untouched until installation. */
final class DockResetPlan {
    private DockResetPlan() { }

    static DockFailure prepare(DockLayoutCandidate candidate, List<Dockable> contents,
                               Map<DockableId, DockAreaId> initialIntents, DockTarget configuredFallback,
                               BiFunction<Dockable, ResolvedDockTarget, MoveCheck> check) {
        DockTarget fallback = configuredFallback;
        if (fallback == null && !candidate.window.roots().isEmpty()) {
            List<DockAreaId> aliases = candidate.window.roots().getFirst().branch().aliases();
            if (!aliases.isEmpty()) fallback = DockTarget.area(aliases.getFirst());
        }
        List<Dockable> fallbackContents = new ArrayList<>();
        for (Dockable dockable : contents) {
            DockAreaId intended = initialIntents.get(dockable.getId());
            DockFailure failure = place(candidate, dockable, intended == null ? null : DockTarget.area(intended), check);
            if (failure != null) {
                if (fallback == null) return failure;
                fallbackContents.add(dockable);
            }
        }
        // Home placements establish reference tabs before relative fallback positions are resolved.
        for (Dockable dockable : fallbackContents) {
            DockFailure failure = place(candidate, dockable, fallback, check);
            if (failure != null) return failure;
        }
        // Relative fallback positions can shift earlier insertions; validate the completed plan too.
        for (DockingManager.Group group : candidate.groups()) {
            List<DockableId> order = candidate.tabs(group.id());
            for (int index = 0; index < order.size(); index++) {
                DockableId id = order.get(index);
                Dockable dockable = contents.stream().filter(content -> content.getId().equals(id)).findFirst().orElseThrow();
                MoveCheck checked = check.apply(dockable, new ResolvedDockTarget.Insertion(group.id(), index));
                if (checked instanceof MoveCheck.Rejected rejected) return affected(rejected.failure(), dockable.getId());
            }
        }
        return null;
    }

    private static DockFailure place(DockLayoutCandidate candidate, Dockable dockable, DockTarget target,
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
                            dockable.getId(), "Reset fallback reference tab is not in its destination", null);
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
                dockable.getId(), "No eligible reset destination exists", null) : firstFailure;
    }

    private static DockFailure affected(DockFailure failure, DockableId id) {
        return new DockFailure(failure.code(), id, failure.message(), failure.cause(), failure.messageKey());
    }
}
