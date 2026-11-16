# Transactional FXML filters

`DataGrid` filtering is opt-in. Existing `Table` FXML, checklist and comparison behavior is unchanged. The new API uses one immutable condition per column, with AND across columns. Editors create drafts; only the popup host accepts or cancels them. SQL and persistence do not belong in a controller.

```java
grid.configureFilter("name", FilterColumn.text());
grid.configureFilter("quantity", FilterColumn.number(
    Locale.GERMANY, true, BigDecimal.ZERO, BigDecimal.valueOf(Integer.MAX_VALUE)));
grid.setFilterPopupOptions(new FilterPopup.Options(FilterPopup.Outside.CANCEL, true, true));
```

Types are in `de.neitzel.fx.grid.filter`; conditions and candidate contracts are in `de.neitzel.grid`. Each configured header gets an accessible vector filter button which does not sort the column. Standalone `TableResults.setFilterActions` delegates the same button to an application-supplied handler.

## Built-in editor

`BuiltInFilterEditor.fxml` and its controller implement text, numbers, missing/present and checklist modes. Clear filter removes the condition; an empty checklist instead matches nothing. Switching modes modifies only the current draft. Each opening creates a fresh controller, initialized from the committed condition.

Text is literal: `%`, `_`, empty equality and intentional spaces are preserved. Other text operations reject empty operands. Case-insensitive matching is the default; construct a `FilterColumn` with `caseSensitive=true` to override it.

Numeric parsing uses the configured locale and exact `BigDecimal`. Grouping separators are deliberately rejected rather than silently interpreted; use the locale's decimal separator. Integer columns reject fractions and signed-64-bit overflow, and configured minimum/maximum bounds enforce narrower types. NaN, infinity, reversed ranges and out-of-domain values fail with a visible explanation. No rounding occurs.

## Checklist providers

The grid default is `DISABLED`. A column can inherit it or select `DISABLED`, `CURRENT_PAGE`, or `SOURCE_WIDE`. The developer chooses the scope; it is not an end-user setting. Enabled columns must have an explicit provider, including when inheriting an enabled grid default.

```java
// Attach explicitly; the source is never implicitly inferred from loaded UI rows.
var sourceWide = memorySource.candidateProvider(value -> value == null ? "Missing" : display(value));
grid.configureFilter("name", FilterColumn.text()
    .withChecklist(FilterColumn.Checklist.SOURCE_WIDE, sourceWide));

// For current-page values, supply typed Candidate entries from the displayed result.
var currentPage = FilterCandidates.currentPage(() -> candidatesFromDisplayedPage());
grid.configureFilter("name", FilterColumn.text()
    .withChecklist(FilterColumn.Checklist.CURRENT_PAGE, currentPage));
```

`currentPage` copies supplied entries when the editor opens. `MemoryGridSource.candidateProvider` captures scalar values then evaluates other columns' filters on its executor, excluding the edited column's condition. Provider sessions accept a context, search text and offset/limit. Results carry typed values and independent labels; a null candidate value explicitly means missing. Distinctness never depends on the label.

Source-wide providers must declare that scope and implement search/batching; unsupported scopes show an explanation and cannot be accepted. Comparison modes remain usable. No fallback to the loaded page occurs. Select all shown and Uncheck shown affect only displayed candidates. Off-page/off-search selections survive searches, batches and failures. Async results after a newer search or disposal are ignored. A failure is displayed with Retry and preserves the draft; it is never treated as an empty successful result.

## Custom FXML controllers and capabilities

Implement `FilterEditor`: supply a detached fresh `Parent`, a read-only validation message, `draft()` and `close()`. Load FXML with `FXMLLoader` inside a registration factory; applications can supply their own controller factory/injection. `draft()` returns an immutable `FilterCondition` or null for Clear, and throws an actionable validation exception for invalid input. It must not modify a grid or execute SQL. Release external listeners and pending lookup work in `close()`.

```java
var editors = FilterEditors.builtIns();
editors.register(new FilterEditors.Registration(
    "app.custom", new FilterCapabilities(true, false, Set.of()),
    Set.of("app.custom"), context -> MyFxmlEditor.load(context)));
grid.setFilterEditors(editors);
```

Configure a column with that editor ID. Registration declares emitted condition IDs plus independent local, persistence and adapter capabilities. The popup checks emitted IDs and the active source's execution capability ID; the source then validates the concrete condition. An editor declaration alone never makes a condition executable. Memory columns need a matching custom compiler in `MemoryColumn.customFilters`. External sources override `filterExecutionId()` and `validateFilter()`; the default visibly rejects undeclared support. `DataGrid.setFilters` also validates through the source before changing requested state.

The built-in registry declares local evaluation and persistence through the optional Gson module; see [saved setups](saved-setups.md). Use `FilterEditors.builtIns(Set.of("jdbc-h2"))` to explicitly declare the qualified H2 adapter in an application; source validation still checks each concrete condition. The no-argument registry retains local-only declarations. See the [H2 demonstration](../../fx-example/docs/product-grid.md). Duplicate editor IDs and missing registrations fail explicitly.

## Popup completion and disposal

`FilterPopup` hosts any editor with independent optional OK/Cancel actions and Clear filter. Enter accepts; Escape cancels. Open ComboBox/DatePicker choosers get their own Enter/Escape first. Custom nonstandard nested popups implement `nestedPopupShowing` and `closeNestedPopup`.

Outside dismissal defaults to Cancel and can be configured to Accept. Invalid or unsupported outside-accept keeps/reopens the draft with an error and preserves the committed condition. Opener re-click uses the same policy. Explicit completion requests focus back to the opener; outside completion leaves focus with the clicked target. Owner hiding or detaching the opener cancels and disposes the session. `close()` is idempotent and always rolls back. Cleanup failures are exposed by `FilterPopup.disposalFailureProperty` after the popup has released its window listeners and content.

These are the implemented policies. The user waived the remaining native/manual qualification for this popup implementation. Native OS input routing, placement and other-application dismissal remain unverified; handler tests do not establish them. See the workspace implementation ticket and the probe below.

## Reproducible native qualification

After running tests including `FilterEditorsTest`, from the repository root:

```sh
python3 fx/scripts/filter-popup-probe.py --manual
# Optional automated OS-input attempt; it fails rather than substituting synthetic events:
python3 fx/scripts/filter-popup-probe.py
```

The manual probe opens the production popup/editor plus a DatePicker to qualify custom nested controls. Check real typing + Enter (with actions visible and hidden), Escape rollback, ComboBox selection and first-Escape handling, DatePicker selection and first-Escape handling, second Escape, both outside policies, invalid outside-accept, opener re-click, focus return versus outside target focus, owner disposal, repeated reopening, other-application dismissal, and placement near screen edges. Close the window to end the probe. The remaining manual qualification was waived by the user; this launcher remains available for future native evidence.

CSS hooks: `filter-button`, `filter-icon`, `filter-popup`, `filter-editor`, `filter-actions`, `filter-validation`. The popup includes owner-scene stylesheets at open. [Shared themes](themes.md) propagate live through the owner manager and include resolved popup text-contrast checks.
