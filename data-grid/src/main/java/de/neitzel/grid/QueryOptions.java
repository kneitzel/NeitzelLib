package de.neitzel.grid;

/** Per-attempt execution consent. Never persist this with a setup or reuse across user requests. */
public record QueryOptions(long allWarningThreshold, boolean allApproved) {
    public QueryOptions {
        if (allWarningThreshold < 0)
            throw new IllegalArgumentException("Negative All warning threshold");
    }

    public static QueryOptions defaults() {
        return new QueryOptions(1000, false);
    }
}
