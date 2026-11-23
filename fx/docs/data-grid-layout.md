# DataGrid layout, grouping and saved application setups

These features extend the opt-in queried DataGrid. Standalone collection/FXML
Table behavior is unchanged. Use Java 25 and call grid operations on the JavaFX
application thread.

## Group a returned page

Declare group values explicitly; never derive them from a rendered cell. A text
mapping is convenient for labels, while the full constructor supports integers,
exact decimals and booleans using `FilterValue` and `MemoryColumn.Kind`.

```java
// grid already has stable ResultColumn IDs and a source, as in data-grid.md.
grid.configureGrouping(
    List.of(GroupColumn.<Person>text("name", Person::name),
            GroupColumn.<Person>text("type", Person::type)),
    true,                  // developer enables grouping
    List.of("type"),       // outer-to-inner default grouping columns
    true,                  // unseen groups start collapsed
    1000);                 // inactive expansion choices retained
```

`GroupColumn`, `GroupPath`, `GroupingSetup` and the projection/history models live
in `de.neitzel.grid`. All eligible values/labels are captured when a page publishes,
even for columns not currently grouped. `setGroupingColumns(List.of("type","name"))`
rearranges existing page components using those snapshots and issues no query.
`regroup()` explicitly recaptures group values after an application data update;
it does not change page membership. `refresh()` instead queries the source.

Text equality is exact, including case and whitespace; missing and empty text
are distinct. Decimal 1.0 and 1.00 share identity. Groups sort ascending, text
ignoring case with an exact tie-breaker, booleans false then true, missing last.
Rows within a leaf group retain their source order. Custom domains must map to
one declared scalar type; changing identity/normalization semantics requires a
schema migration. Labels never define identity. Invalid mappings fail visibly
without replacing the prior grouping.

Grouping applies only to the loaded page. The same value may recur on another
page; counts include collapsed data rows and exclude group headings. Explicit All
mode still uses the existing guard/confirmation protocol and retains all returned
row components. Collapsing it does not remove that memory cost.

## Interactions and row ownership

Drag a column's right boundary to preview a bounded width; release commits that
column and Escape restores its previous sizing intent. Drag a header label to
move a column, or into the grouping area to add a chip. Insertion feedback and
fixed-position constraints apply; invalid/outside drops cancel without sorting.
Drag chips to reorder grouping levels, and use × to remove a grouping level.
A header click sorts and the separate filter icon opens its editor. The Grid
setup menu offers keyboard-operable Column settings.

The arrow at the far right of the header opens a checklist of all columns.
Select a column to toggle its visibility. A column header's context menu offers
ascending and descending sorting, clearing that column's sort, filtering and
grouping where configured, resetting the column width, and hiding the column.
The arrow remains available when every column is hidden.

Applications can use `setColumnVisible(id, visible)`, `isColumnVisible(id)`,
and `columnVisibilityRevisionProperty()` to observe changes from either the UI or code.
Visibility applies to the open grid, including later pages. Hiding a column
preserves its order, width, filters, sorting, and grouping, and leaves existing
row components and drafts in place. Saved setups currently store the column
order and widths.

Each group has a disclosure triangle. Expansion follows the typed ordered path,
not its label or page number. Parent collapse preserves child choices. Current
paths are retained along with the configured LRU history of inactive explicit
choices; evicted paths return to the default. `setExpanded(path, boolean)` and
`resetExpansion()` provide programmatic equivalents. `setGroupingEnabled(false)`
hides grouped presentation without erasing valid dormant choices.

Reorder/regroup/collapse preserve current-page component identity and drafts.
Actual page replacement keeps the existing guard/staging/disposal rules. A hidden
selected record retains its key but has no actionable `selectedElement`. Expansion
never re-enables stale rows. If collapse/reset hides focus, focus moves to a visible
group heading. No historical rows are retained merely for expansion history.

## Attach optional persistence

`fx` does not depend on Gson. Add `de.neitzel.lib:gson` at the same library version
when using the supplied JSON/file integration. The neutral custom-store contracts
remain available without Gson.

```java
// Once per application, not per tab. No files/directories are created here.
var configuration = GridSetupFiles.forApplication("MyApplication");

// On each grid, after columns/grouping are configured, before the first query:
grid.configureSetups("people.records", 1,
    Map.of("name", "text.v1", "type", "text.v1"), Map.of());
var attachment = grid.attachPersistence(configuration, "people.records");
attachment.loadAsync(); // read -> validate/restore or defaults -> one initial query
// attachment.ready() completes after initialization or explicit recovery succeeds.
```

Factory: `de.neitzel.gson.grid.GridSetupFiles`. Service/store:
`de.neitzel.grid.GridApplicationConfiguration` and `GridSetupStore`. Attachment:
`de.neitzel.fx.view.GridPersistenceAttachment`. An attachment overload accepts a
context key. `GridSetupFiles.forApplication(name, path)` selects an explicit grid
storage directory; another overload accepts pre-registered `GridSetupJson` codecs.
Custom stores implement blocking load/revision/conditional-save; the service runs
those operations off the FX thread.

The default directory is `ApplicationDirectories.configurationDirectory(name)`
plus `grids`. Grid/context keys map to opaque safe filenames, with identity checked
inside each document. Existing application preferences are never migrated.
Never-attached grids perform no persistence I/O. Attachments can be closed and
replaced; the application service owns accepted saves after detachment.

A stable grid ID/context shares one stored setup, not live displays. Newly opened
grids restore the latest successfully saved snapshot. Open grids keep their own
state until `attachment.loadAsync()` or Reload saved setup. The newest committed
change replaces the whole saved snapshot; fields from different views are not
merged. Closing an unchanged older tab cannot overwrite newer choices.

## Defaults, formats and recovery

The existing GridSetup format is extended with grouping and typed expansion
choices. Version-one documents remain readable; absent new preferences inherit
current developer grouping defaults. Current version-two documents require their
complete schema. Definition/type migrations, fixed-position validation, width
clamping and custom filter codecs retain their existing strict behavior.
Unsupported or incompatible files fail visibly, never by silently dropping fields.

`configureDefaults(GridSetup)` may configure the full default query/layout without
fetching, after definitions/mappings and before attachment/load. Otherwise defaults
are captured at attachment. Defaults stay separate from the restored display.

Committed changes autosave after a configurable debounce (500 ms by default).
`committedRevisionProperty()` is the FX notification seam; previews, cancelled
operations, drafts and failed/pending queries do not become saves. Query preferences
are saved only after successful publication. Saving/encoding failures retain the
latest unsaved snapshot and last good file. The grid shows nonmodal settings
feedback with Retry/Reload/Reset/Overwrite actions, separate from query status.

If no saved file exists, developer defaults apply. An unreadable/incompatible file blocks startup restoration
and initial query until retry or explicit reset succeeds. Full Reset setup uses
current defaults and the existing edit guard/transactional publication before
accepting a replacement save. Reset expansion only clears expansion choices and
never queries or resets other preferences.

The file store uses process coordination, revision-checked writes and atomic
replacement. A cross-process conflict requires explicit Reload or Overwrite.
Reload retires its abandoned pending generation only after successful guarded
restoration and cannot discard a newer change. Overwrite is still conditional
against a freshly observed revision. Unsupported atomic filesystem operations
fail rather than publishing a partial file.

## Close and application shutdown

```java
// Tab close, on FX thread:
attachment.close();
grid.close();

// Application shutdown: asynchronous; do not block the FX thread.
configuration.flushAsync();
configuration.closeAsync(); // also flushes; failure leaves the service retryable
// Only after explicit user consent to quit without settings:
configuration.discardAndCloseAsync();
```

Chain/await completion stages in the application exit coordinator; the snippet
lists operations, not permission to ignore their completion. A failed flush must
be handled before exit. Close does not own external executors. Explicit discard
retires pending work but cannot undo a write already committed. Detaching a grid
stops observation/cancels its unfinished restore, not already accepted app saves.

## Qualification

Automated component/state, codec, lifecycle and real separate-process storage tests
are provided. For native interaction qualification after running DataGridGroupingTest:

```sh
python3 fx/scripts/data-grid-probe.py
# macOS bundle for native accessibility tooling:
python3 fx/scripts/data-grid-probe.py --app
```

This opens actual JavaFX DataGrid controls with fictional records and a resource/
query trace. Use native pointer/keyboard input to qualify the recorded acceptance
scenarios; it is not a browser mock and emits no synthetic OS input. It has no
application persistence or real DNS/network data. Runtime/platform evidence and
remaining limits are recorded in the Workspace implementation record.

For repeatable macOS held-input checks (Escape during a drag, invalid/outside
native drops and timed edge hovering), the separate opt-in fixture uses AWT
Robot for pointer/keyboard input and JavaFX Robot for capture. It operates only
its fictional-data window and owned popups; no application data or DNS is used.

```sh
./mvnw -pl fx -am -Dtest=DataGridNativeInteractionTest \
  -Dneitzellib.nativeGridTest=true -Dsurefire.failIfNoSpecifiedTests=false test
```

Without `neitzellib.nativeGridTest=true`, its five native tests are skipped.
On macOS the Java launcher and launching terminal or IDE need event-posting
permission in Privacy & Security → Accessibility. A permission-denied process
fails before posting input; that failure does not establish a gesture failure.
Restore any temporary permissions after testing. Keep the desktop idle: checks
stop if focus leaves their window and owned popups. Cleanup releases input and
disposes retained rows even on failure. Captures go to `fx/target/native-grid`.
Cancellation waits for the native drag-completion callback before releasing the
button or starting another gesture, including macOS's return animation.
