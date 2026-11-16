package de.neitzel.grid;

/**
 * Source stopped before materializing an unpaged result. Any snapshot/transaction has been
 * released.
 */
public final class AllRowsConfirmationRequired extends RuntimeException {
    private final long count;

    public AllRowsConfirmationRequired(long count) {
        super("Loading all " + count + " matching rows requires confirmation");
        if (count < 0) throw new IllegalArgumentException("Negative count");
        this.count = count;
    }

    public long count() {
        return count;
    }
}
