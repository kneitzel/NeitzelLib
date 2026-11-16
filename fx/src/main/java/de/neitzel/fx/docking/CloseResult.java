package de.neitzel.fx.docking;

import java.util.List;

/** Final close outcome, kept separate from consent and resource-release errors. */
public sealed interface CloseResult {
    record Closed(List<DockFailure> cleanupFailures) implements CloseResult {
        public Closed { cleanupFailures = List.copyOf(cleanupFailures); }
    }

    record NoChange() implements CloseResult { }

    record Vetoed(List<DockableId> vetoingIds, List<DockFailure> checkFailures) implements CloseResult {
        public Vetoed {
            vetoingIds = List.copyOf(vetoingIds);
            checkFailures = List.copyOf(checkFailures);
        }
    }

    record Canceled() implements CloseResult { }

    record Rejected(DockFailure failure) implements CloseResult { }
}
