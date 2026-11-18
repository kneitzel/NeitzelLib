# Opt-in queried Table results

`Table.openResults` is a presentation integration point for the DataGrid coordinator. It does not replace the existing ViewLoader/FXML collection Table. Call it on a fresh Table on the JavaFX application thread; a Table cannot switch between modes or be opened twice.

```java
Table table = new Table();
TableResults<Long, Person> renderer = table.openResults(
    List.of(new ResultColumn("name", "Name", 180, 80, 400, -1)),
    person -> {
        Label name = new Label(person.getName());
        HBox root = new HBox(name);
        return new ResultRow(root, Map.of("name", name), () -> { });
    });
renderer.show(List.of(new GridRow<>(person.getId(), person)));
```

Factories may supply freshly bound FXML Components as HBox roots. They must map every column ID to a distinct direct-child Region. Any unmapped children are trailing action controls. Supply a disposer to release application bindings/listeners; the renderer removes its own click handler. Never return an existing attached root or reuse a row between results. No Component is rebound to a new model.

The renderer preserves supplied row order and does no local filtering, sorting, fetching or edit observation. The caller validates query results and handles guard approval before calling `show`. It stages new rows before replacing old ones. If construction or mapping validation fails, staged rows are disposed and the old page remains. Factory code that throws before returning a row owns cleanup of its partial construction.

`setColumnOrder` validates complete stable-ID membership and fixed positions before updating headers and fields. `setColumnWidth` accepts default/explicit intent; it returns the clamped applied width so the coordinator can report adjustments. Row roots must have zero padding (put padding inside mapped fields). Rows use zero inter-field spacing to align with result headers; action controls keep their intrinsic size. The result integration now also supplies native resize/reorder and grouping support
when composed by DataGrid; the stable programmatic operations remain available. Header requests can be delegated through `setOnSortRequest`; the renderer never sorts locally.

`select(key)` retains an off-page key; the read-only loaded selected element becomes null until that key returns. Row clicks and blank-body clicks update this separate result selection. The old `Table.selectionProperty()` remains the legacy path and is not the result-mode selection API.

`close()` is idempotent, removes rows/handlers and disposes row resources. Disposal failures do not undo an already published page; observe `disposalFailureProperty()` to surface them. Explicit close is necessary; attaching/removing a Table from a Scene does not infer application ownership.

This is a rendering foundation, not the complete DataGrid. [Local DataGrid](data-grid.md) now supplies request coordination and local source execution. [FXML popup editors](filter-editors.md), [setup codecs](saved-setups.md), and [themes](themes.md) are implemented. DataGrid exposes both programmatic layout operations and native resize/reorder interactions over this result renderer.

[DataGrid layout/grouping](data-grid-layout.md) documents the component-preserving
projection path. Local grouping must not call `show` to recreate current-page
editors; `show` remains the guarded replacement/publication boundary.
