package de.neitzel.grid;

/** Explicit offset paging or unpaged execution. All is never encoded as a magic page size. */
public sealed interface PageRequest {
    /** Zero-based page index. UI page labels may be one-based. */
    record Paged(long pageIndex, int pageSize) implements PageRequest {
        public Paged {
            if (pageIndex < 0 || pageSize <= 0) throw new IllegalArgumentException("Invalid page");
            Math.multiplyExact(pageIndex, (long) pageSize);
        }

        public long offset() {
            return Math.multiplyExact(pageIndex, (long) pageSize);
        }
    }

    record All() implements PageRequest {}
}
