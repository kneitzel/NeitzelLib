package de.neitzel.grid;

import java.util.concurrent.CompletionStage;

/**
 * Toolkit-independent source. Implementations validate mappings/semantics and own per-request
 * resources.
 */
public interface GridSource<K, T> {
    SourceCapabilities capabilities();

    QueryExecution<K, T> query(GridQuery query);

    /** Stable execution capability ID: "local" or an adapter-specific registration ID. */
    default String filterExecutionId() {
        return "unspecified";
    }

    /** Validate concrete semantics before accepting an editor/programmatic draft. */
    default void validateFilter(FilterCondition condition) {
        throw new UnsupportedOperationException(
                "Source does not declare filter support: " + condition.typeId());
    }

    /**
     * Guarded execution. Sources supporting All must override and count in their execution snapshot
     * before materializing rows, failing with AllRowsConfirmationRequired when consent is needed.
     * The default deliberately rejects All rather than performing an unsafe fetch-first check.
     */
    default QueryExecution<K, T> query(GridQuery query, QueryOptions options) {
        java.util.Objects.requireNonNull(options);
        if (query.page() instanceof PageRequest.All) {
            var failure =
                    java.util.concurrent.CompletableFuture.<PageResult<K, T>>failedFuture(
                            new UnsupportedOperationException(
                                    "Source does not support guarded All execution"));
            return new QueryExecution<>() {
                public CompletionStage<PageResult<K, T>> result() {
                    return failure;
                }

                public void cancel() {}
            };
        }
        return query(query);
    }

    /**
     * Cancellation is best-effort; coordinators must reject superseded completions independently.
     */
    interface QueryExecution<K, T> {
        CompletionStage<PageResult<K, T>> result();

        void cancel();
    }
}
