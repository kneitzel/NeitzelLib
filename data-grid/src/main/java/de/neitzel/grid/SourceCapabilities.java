package de.neitzel.grid;

/** Source-level page support; filter semantics are validated separately. */
public record SourceCapabilities(boolean exactTotal, boolean all, int maximumPageSize) {
    public SourceCapabilities {
        if (maximumPageSize <= 0) throw new IllegalArgumentException("Invalid maximum page size");
    }

    public void validate(PageRequest request) {
        java.util.Objects.requireNonNull(request);
        if (request instanceof PageRequest.All && !all)
            throw new IllegalArgumentException("Source does not support All");
        if (request instanceof PageRequest.Paged p && p.pageSize() > maximumPageSize)
            throw new IllegalArgumentException("Unsupported page size");
    }
}
