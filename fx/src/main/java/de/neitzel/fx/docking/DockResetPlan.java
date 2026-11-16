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
            DockFailure failure = DockCandidatePlacement.place(candidate, dockable, intended == null ? null : DockTarget.area(intended), check);
            if (failure != null) {
                if (fallback == null) return failure;
                fallbackContents.add(dockable);
            }
        }
        // Home placements establish reference tabs before relative fallback positions are resolved.
        for (Dockable dockable : fallbackContents) {
            DockFailure failure = DockCandidatePlacement.place(candidate, dockable, fallback, check);
            if (failure != null) return failure;
        }
        // Relative fallback positions can shift earlier insertions; validate the completed plan too.
        for (DockingManager.Group group : candidate.groups()) {
            List<DockableId> order = candidate.tabs(group.id());
            for (int index = 0; index < order.size(); index++) {
                DockableId id = order.get(index);
                Dockable dockable = contents.stream().filter(content -> content.getId().equals(id)).findFirst().orElseThrow();
                MoveCheck checked = check.apply(dockable, new ResolvedDockTarget.Insertion(group.id(), index));
                if (checked instanceof MoveCheck.Rejected rejected) return DockCandidatePlacement.affected(rejected.failure(), dockable.getId());
            }
        }
        return null;
    }

}
