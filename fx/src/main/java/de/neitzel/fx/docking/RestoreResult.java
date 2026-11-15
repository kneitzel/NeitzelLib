package de.neitzel.fx.docking;

import java.util.List;

/** An applied restore reports missing identities; rejection separates its cause from cleanup diagnostics. */
public sealed interface RestoreResult {
    record Applied(List<DockableId> missingIds) implements RestoreResult {
        public Applied { missingIds = List.copyOf(missingIds); }
    }
    record Rejected(DockFailure failure, List<DockFailure> cleanupFailures) implements RestoreResult {
        public Rejected { cleanupFailures = List.copyOf(cleanupFailures); }
    }
}
