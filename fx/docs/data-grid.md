# Local DataGrid and request coordination

For direct declarations, columns and data bindings in FXML, see [FXML controls](fxml-controls.md).

`DataGrid<K,T>` composes the existing Table result renderer with paging controls and a toolkit-independent coordinator. All UI methods are called on the JavaFX application thread. The legacy Table FXML/collection path is unchanged.

```java
var source = new MemoryGridSource<Long, Person>(
    () -> people, Person::getId, Long::compare,
    List.of(new MemoryColumn<>("name", MemoryColumn.Kind.TEXT,
        person -> person.getName() == null ? null : new FilterValue.Text(person.getName()))),
    applicationExecutor, 250);
var grid = new DataGrid<Long, Person>(
    List.of(new ResultColumn("name", "Name", 180, 80, 400, -1)),
    person -> {
        Label name = new Label(person.getName());
        return new ResultRow(new HBox(name), Map.of("name", name), () -> { });
    }, source);
grid.setAllWarningThreshold(1000);
// Set optional edit guard / custom confirmation handler before the first request.
grid.load();
```

Use `de.neitzel.grid` for shared models/source types and `de.neitzel.fx.view` for DataGrid/result rendering. The application owns the executor and closes the grid when its view is disposed. No query runs in the constructor.

## Operations

- Vertical separators mark column boundaries in headers and data rows, follow resizing/reordering, and use the active theme border color. Group headings span the columns.
- First/Previous/Next/Last use vector navigation icons with tooltips and accessible page names. Page-size selection, Refresh and Retry are supplied. Page sizes offer 25/50/100/250 within source limits plus explicit All when supported. The initial page size is 50, bounded by source limits.
- Header clicks alternate ascending/descending sort for that mapped column; `setSort(null)` clears user sorting. Sorting/filter/page-size changes request the first page (All remains All with fresh consent checks).
- `setFilters`, `setSort`, `setPageRequest`, `setSource`, `load`, `refresh`, and `retry` delegate to the coordinator; returned rows are never locally filtered again.
- `stateProperty` exposes requested versus displayed query, result, phase and error. Loading/failed rows stay visible but disabled; selected loaded data becomes unavailable while those rows are stale. Query/navigation controls remain usable.
- `selectionMode` is configurable in Java or FXML: `NONE`, `SINGLE` (default), or `MULTIPLE`, using `RowSelectionMode`. Changing to NONE clears selection; changing to SINGLE keeps the lead key. Selection mode is application configuration and is not persisted in saved setups.
- `selectedKeysProperty` is a read-only observable list retaining off-page identities. `selectedElementsProperty` contains selected models only while their rows are visible and interactive. `selectedKeyProperty` / `selectedElementProperty` expose the lead key/model for compatibility. Single-item actions should require exactly one selected key and one loaded element.
- `select(key)` replaces selection (`null` clears); `selectKeys(keys)` replaces a multiple selection, with the last key as lead. Click replaces, Ctrl/Cmd-click toggles, Shift-click selects a range in visible grouped order, Ctrl/Cmd-Shift-click adds a range. Arrow/Home/End keys move through visible rows; Shift extends, Ctrl/Cmd moves focus, Space selects, Ctrl/Cmd-Space toggles, Ctrl/Cmd-A selects visible rows, Escape clears. Embedded editors keep their own keys. Rows have default selection colors even without an application theme.
- A successfully replaced source clears all selection. `confirmedDeleted(key)` removes only that key and refreshes; absence from a page alone does not imply deletion. Collapsed groups retain their keys but expose no actionable elements for hidden rows.
- Empty results disable navigation. Unknown totals are displayed as unknown and disable Last. A disappeared page receives one bounded correction; subsequent invalidation becomes an error with Retry.

## Guards, warnings and supersession

`setEditGuard(query -> CompletionStage<Boolean>)` lets the application save/discard/block unfinished edits. `setAllConfirmation((query, count) -> CompletionStage<Boolean>)` can replace the default nonblocking Continue/Cancel alert. Neither dialog handling nor guard completion runs inside a source transaction. Superseded answers cannot publish or restart old requests.

The default All warning is based on the source's count in its execution snapshot, before unpaged rows are returned. Exactly the threshold does not warn. Cancel restores the prior displayed query/page. A subsequent approved attempt takes a fresh source snapshot; approval is not stored in setup data or reused for later query changes. The coordinator requires a source that implements the guarded All overload, not just a flag claiming All support.

The application must provide a serial owner-thread callback executor when using the neutral coordinator directly. DataGrid always uses Platform.runLater and keeps row publication on the FX thread. Requests triggered by state/selection observers are queued after publication.

## Lifecycle and remaining slices

`close` cancels the active request, invalidates pending callbacks/alerts, closes the renderer and releases displayed rows. It does not close application-owned infrastructure. Observe `disposalFailureProperty` for row cleanup failures.

FXML filter editors and the opt-in popup host are now available; see [filter editors](filter-editors.md), including the recorded native qualification limitation. [JSON setup restore](saved-setups.md) is available through the optional Gson module. [Shared themes and window propagation](themes.md) are available opt-in. The optional [JDBC adapter and H2 example](../../fx-example/docs/product-grid.md) now demonstrate external execution. The default alert is ordinary JavaFX; native interaction/theme qualification remains part of those acceptance gates. Row mappings, programmatic order/width changes and cleanup retain the foundation guide's constraints in [table-results](table-results.md).

## Interactive layout and grouping

Column resize/reorder, grouping chips, disclosure and keyboard Column settings
are now opt-in DataGrid features. Application-name-based saved setups are an
optional attachment using the existing versioned setup model. See the
[layout, grouping and persistence guide](data-grid-layout.md) for mappings,
component lifetime, startup/recovery, storage concurrency and shutdown.
