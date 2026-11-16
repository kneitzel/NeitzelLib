package de.neitzel.grid;

import java.util.Set;

/**
 * Independent registration capabilities; a translator must additionally validate each concrete
 * condition.
 */
public record FilterCapabilities(
        boolean localEvaluation, boolean persistence, Set<String> adapters) {
    public FilterCapabilities {
        adapters = Set.copyOf(adapters);
        adapters.forEach(Checks::id);
    }
}
