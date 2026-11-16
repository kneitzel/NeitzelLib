# data-grid foundations

Toolkit-independent contracts for opt-in DataGrid development. This module does not depend on JavaFX or a JDBC driver. Parent-provided compile/test tooling is inherited, including Lombok annotation processing; immutable public models are Java records.

- `FilterCondition` is open to custom immutable implementations. Built-ins provide literal text, exact decimal, missing/present and explicit checklist conditions. A custom type must declare capabilities separately; editor registration is in `fx`, codecs are in optional `gson`, and adapter compilation remains separate.
- `GridQuery` combines one condition per column with AND and ordered sort keys. The source must append its stable row-key tie-breaker and validate column types, precision, matching semantics and capabilities.
- `PageRequest.Paged` uses zero-based indexes; `All` is explicit. `PageResult` defensively copies lists and checks row identities/count consistency, allowing empty out-of-range pages for coordinator recovery.
- `GridSource` separates asynchronous result completion from best-effort cancellation. The coordinator must independently discard obsolete completions. Models inside `GridRow` remain application-owned; they are not deep-copied.
- `GridSetup` separates format and definition versions, default versus explicit widths, and All versus paging. It rejects persisted nonzero page indexes. Selection, credentials and warning approvals are deliberately absent.

`TextFilter.matches` uses Locale.ROOT lowercasing with no normalization; `%` and `_` are literal. `NumericFilter` accepts BigDecimal operands and compares numerically, avoiding double conversion. The mapping layer remains responsible for the declared integer/decimal domain and scale limits. `FilterValue.Decimal` canonicalizes scale so checklist membership treats 1.0 and 1.00 equally. Text checklist membership is exact.

`MemoryGridSource` now executes mapped local queries, and `GridCoordinator` manages guarded requests, All confirmation, stale-result rejection, recovery and publication. Definition migrations are available here and JSON codecs are in optional `gson`; SQL execution is available in the optional [data-grid-jdbc module](../data-grid-jdbc/README.md). No executor is created or shut down by these classes.

Run from the repository root: `./mvnw -pl data-grid test`.

## Local execution

`MemoryGridSource<K,T>` accepts a collection supplier, stable-key function and comparator, explicit `MemoryColumn<T>` scalar mappings, an application-owned executor and a maximum page size. Built-in mappings cover text, long integers, arbitrary-precision decimals and booleans; null mapping values mean missing. Per-column custom filter compilers explicitly opt into additional local conditions; unsupported types fail rather than being ignored. Numeric mapping constraints beyond the generic long/BigDecimal domains remain the application's responsibility.

A query captures collection membership, keys and mapped scalar values on the calling thread. The supplier/readers must provide a consistent view (for example, call from the FX thread for FX-owned models, or synchronize the snapshot). Filtering, sorting and slicing run on the supplied executor. This preserves scalar/count consistency across later model changes without reading FX-owned properties on a worker. Model objects in results are application-owned references, not deep copies. Snapshot capture is O(n) caller-thread work; use a source with its own background snapshot mechanism for very large mutable datasets.

Filters combine with AND. Text sort uses Locale.ROOT lowercase lexical comparison; nulls remain last in either direction. The key comparator provides the final tie-breaker and must distinguish unique keys. Data is never reordered in the source collection.

`query(query, QueryOptions)` is the guarded All protocol. Without attempt-scoped approval, a count above the configured threshold fails with `AllRowsConfirmationRequired` before sorting/materializing the unpaged result. The caller asks the person, then submits the same query with approval in a **fresh** snapshot. A changed query or later attempt needs fresh approval. Sources not overriding the guarded overload reject All by default. Do not fetch All first and then ask.

## Owner-thread coordinator

Create `GridCoordinator` on its owner thread and supply an executor that **queues** callbacks to that same thread (`Platform::runLater` for FX). Public mutators also run there. Observers should not throw. A publisher may reject a result before committing it, allowing the coordinator to preserve its prior displayed state. Observer-triggered requests are queued rather than applied reentrantly.

State exposes requested versus displayed query, result, phase and error. The coordinator validates page identity and declared exact-count support, attempts cancellation but independently ignores superseded successes/failures, and permits only one corrective page request per user attempt. A guard may approve/block edits asynchronously. All confirmation is asynchronous and never retains an execution snapshot while awaiting input.

Rejected guards and declined All warnings restore the displayed query. A replacement source is committed only when it successfully publishes a result; cancellation keeps the previous source. Retry uses the latest requested query/source. Close invalidates pending callbacks and drops retained results without closing application sources/executors. Source selection and loaded row identity are presentation responsibilities.

Collection changes do not automatically trigger requests in this slice. Call refresh explicitly from application mutation handlers; automatic collection subscriptions can be added without changing the query contract.

## Saved setups

`GridDefinition.prepare` applies explicit definition migrations and validates column types/order, widths and source support. `GridSetup.ColumnState` carries stable type IDs. JSON is provided separately by `de.neitzel.lib:gson`; `data-grid` does not depend on JavaFX or Gson. See the [saved setup guide](../fx/docs/saved-setups.md).
