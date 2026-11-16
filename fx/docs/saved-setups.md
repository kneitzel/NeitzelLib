# Saved DataGrid setups

Saved setups are opt-in and preserve the legacy Table path. The `data-grid` module owns immutable models, definition validation and migrations; `fx` stages layout/result publication; the existing optional `gson` module owns JSON encoding. Applications choose storage and save/load timing. The FX library does not depend on Gson.

## Configure and save

```java
grid.configureSetups("inventory.products", 1,
    Map.of("name", "text.v1", "quantity", "int32.v1"), Map.of());

GridSetupJson json = new GridSetupJson(); // de.neitzel.gson.grid; optional gson module
String document = json.encode(grid.snapshotSetup());
```

Use a stable definition ID and stable column type/schema identifiers. Labels and Java Attribute names are not identities. Every column needs a type. The existing two-argument `ColumnState(id,width)` constructor remains available for earlier model callers, but its `unspecified` type must be supplied before saving/restoring against a definition.

Snapshots include column order, default-versus-explicit widths, one user sort, committed conditions and page size or explicit All mode. They omit current page, selection, row data, credentials, unfinished drafts and All approval. Saving neither commits nor closes a filter editor and does not invoke the edit guard. Pending/failed queries do not become committed saved filters; snapshots use the last successfully displayed query, or the initial configuration before first success.

The complete JSON string is produced only if every filter can be encoded. No unsupported condition is omitted. Filesystem/database writes and atomic replacement of stored files remain application-owned.

## Restore

```java
// Configure before load(); constructor issues no query.
grid.restoreSetup(() -> json.decode(document));
```

The supplier overload presents decode errors through the same visible coordinator error state. It does not issue a default query. Alternatively pass an already decoded `GridSetup`.

Restoration validates format/definition versions, column identity/type, order constraints, source page limits and concrete filter support. New columns missing from a compatible document are appended in declaration order with default widths. Removed columns or changed types require migration. Fixed columns cannot move or be displaced by appended columns. Valid explicit widths are clamped to current limits; `restorationAdjustmentsProperty` reports these adjustments after successful publication. Negative, zero and non-finite widths are invalid.

Only after preparation succeeds does the asynchronous edit guard run. Guard denial retains any open filter draft. Approval cancels that draft and issues one initial query on the first page. The current layout and page remain displayed until both new rows and layout have been prepared successfully. Query or row-construction failure retains the previous committed setup/page and exposes an error. A cancelled draft is not resurrected on failure.

A newer query or layout operation supersedes a pending restore; late guard/query/confirmation results cannot install it. Selection keys survive restoration according to ordinary paging rules; selection is not read from a document. Filter lookup/editor validation and external-source validation remain independent.

All restores use the existing count-warning protocol with configurable threshold (default 1000): count first, release the attempt, ask Continue/Cancel, then start a fresh approved attempt. Consent is never saved. Cancel retains the previous setup. A newer request invalidates old consent. A failed restore returns requested settings to the previous committed configuration; retrying the saved candidate requires calling `restoreSetup` again.

## Explicit migrations

Supply `Map<Integer, UnaryOperator<GridSetup>>` to `configureSetups`. The key is the old definition version; each migration must return exactly the next version, retain the definition ID and supported format, and explicitly repair removed/renamed IDs, type changes and dependent filters/sorts. Migrations run consecutively until the configured version. Missing migration steps, future versions and output violating current constraints fail without querying. A migration cannot bypass source validation.

Appending a new column does not itself require a migration when the saved definition version remains compatible; if the developer advances the definition version, an explicit migration is required even if it simply confirms compatible existing state.

## JSON and custom filters

`GridSetupJson` format version 1 uses explicit objects and typed scalar values. Exact decimal operands and integer checklist values use strings to avoid conversion through floating point. Missing checklist values remain separate from empty strings. Strict parsing rejects duplicate/unknown properties, trailing input, incorrect scalar types and unsupported versions. No arbitrary Java object deserialization is used.

Implement and register `GridSetupJson.FilterCodec` for a custom stable filter type ID. Declare the write version, readable versions, explicit JSON payload and decoding logic. The codec must preserve the requested column/type identity. `readableVersions` defaults to the current write version; support older payloads only with explicit decoding/migration logic. Missing codecs and unsupported payload versions fail visibly. Codec registration is independent of editor availability and local/adapter execution; successful decoding does not establish that the active source supports the condition.

Built-in text, number, missing/present and checklist codecs are supplied. The built-in editor registrations now declare persistence support through this optional module. See the [JDBC demonstration](../../fx-example/docs/product-grid.md) and [shared themes](themes.md) for opt-in integration.
