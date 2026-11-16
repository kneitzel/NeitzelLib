package de.neitzel.fx.docking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Owns prepared resolver results until they are either installed or discarded exactly once. */
record DockRestorePreparation(Map<DockableId, Dockable> contents, List<DockableId> missingIds,
                              boolean layoutAdjusted, RestoreResult.Rejected rejection) {
    static DockRestorePreparation resolve(DockLayoutCandidate candidate, DockableResolver resolver, DockTarget fallback,
                                          java.util.function.BiFunction<Dockable, ResolvedDockTarget, MoveCheck> placement) {
        return resolve(List.of(candidate), resolver, fallback, placement);
    }

    static DockRestorePreparation resolve(List<DockLayoutCandidate> candidates, DockableResolver resolver, DockTarget fallback,
                                          java.util.function.BiFunction<Dockable, ResolvedDockTarget, MoveCheck> placement) {
        Map<DockableId, Dockable> prepared = new LinkedHashMap<>();
        List<DockableId> missing = new ArrayList<>();
        var contentNodes = Collections.newSetFromMap(new IdentityHashMap<javafx.scene.Node, Boolean>());
        for (DockLayoutCandidate candidate : candidates) for (DockingManager.Group group : candidate.groups()) {
            for (DockableId id : candidate.tabs(group.id())) {
                DockableResolution resolution;
                try {
                    resolution = Objects.requireNonNull(resolver.resolve(id), "Resolver returned no resolution");
                } catch (RuntimeException failure) {
                    return rejected(prepared, new DockFailure(DockFailure.Code.DOCKABLE_LOAD_FAILED,
                            id, "Dockable loading failed", failure));
                }
                if (resolution instanceof DockableResolution.Failed failed) {
                    return rejected(prepared, new DockFailure(DockFailure.Code.DOCKABLE_LOAD_FAILED,
                            id, "Dockable loading failed", failed.cause()));
                }
                if (resolution instanceof DockableResolution.Missing) {
                    missing.add(id);
                    candidate.remove(id);
                    continue;
                }
                Dockable dockable = ((DockableResolution.Available) resolution).dockable();
                if (dockable == null) return rejected(prepared, new DockFailure(DockFailure.Code.DOCKABLE_LOAD_FAILED,
                        id, "Resolver returned an empty available result", null));
                if (dockable.getContent().getParent() != null || dockable.getContent().getScene() != null) {
                    return rejected(prepared, new DockFailure(DockFailure.Code.CONTENT_ALREADY_ATTACHED,
                            id, "Attached content retains its existing owner", null));
                }
                prepared.put(id, dockable);
                if (!id.equals(dockable.getId())) return rejected(prepared, new DockFailure(
                        DockFailure.Code.ID_MISMATCH, id, "Resolver returned a different Dockable identity", null));
                if (!contentNodes.add(dockable.getContent())) {
                    return rejected(prepared, new DockFailure(DockFailure.Code.CONTENT_ALREADY_ATTACHED,
                            id, "Resolved content is attached or shared by another prepared Dockable", null));
                }
            }
        }
        List<Dockable> fallbackContents = new ArrayList<>();
        for (DockLayoutCandidate candidate : candidates) for (DockingManager.Group group : candidate.groups()) {
            for (DockableId id : candidate.tabs(group.id())) {
                Dockable dockable = prepared.get(id);
                MoveCheck checked = placement.apply(dockable,
                        new ResolvedDockTarget.Insertion(group.id(), candidate.tabs(group.id()).indexOf(id)));
                if (checked instanceof MoveCheck.Rejected rejected) {
                    if (fallback == null) return rejected(prepared, DockCandidatePlacement.affected(rejected.failure(), id));
                    candidate.remove(id);
                    fallbackContents.add(dockable);
                }
            }
        }
        // Resolve fallbacks only after every surviving reference tab is known.
        for (Dockable dockable : fallbackContents) {
            DockFailure failure = DockCandidatePlacement.place(candidates.getFirst(), dockable, fallback, placement);
            if (failure != null) return rejected(prepared, failure);
        }
        // Relative insertions can shift earlier positions, so validate the final plan before installation.
        for (DockLayoutCandidate candidate : candidates) for (DockingManager.Group group : candidate.groups()) {
            List<DockableId> order = candidate.tabs(group.id());
            for (int index = 0; index < order.size(); index++) {
                DockableId id = order.get(index);
                MoveCheck checked = placement.apply(prepared.get(id), new ResolvedDockTarget.Insertion(group.id(), index));
                if (checked instanceof MoveCheck.Rejected rejected) {
                    return rejected(prepared, DockCandidatePlacement.affected(rejected.failure(), id));
                }
            }
        }
        return new DockRestorePreparation(Collections.unmodifiableMap(new LinkedHashMap<>(prepared)), List.copyOf(missing),
                !missing.isEmpty() || !fallbackContents.isEmpty(), null);
    }

    static DockRestorePreparation rejected(Map<DockableId, Dockable> prepared, DockFailure primary) {
        List<DockFailure> cleanup = new ArrayList<>();
        var discarded = Collections.newSetFromMap(new IdentityHashMap<Dockable, Boolean>());
        for (Dockable dockable : prepared.values()) {
            if (!discarded.add(dockable) || dockable.getOnDiscarded() == null) continue;
            try {
                dockable.getOnDiscarded().run();
            } catch (RuntimeException failure) {
                cleanup.add(new DockFailure(DockFailure.Code.CLEANUP_FAILED, dockable.getId(),
                        "Prepared Dockable cleanup failed", failure));
            }
        }
        return new DockRestorePreparation(Map.of(), List.of(), false, new RestoreResult.Rejected(primary, cleanup));
    }
}
