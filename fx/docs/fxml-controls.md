# Controls in FXML

Every public visual control supplied by `fx` can be declared in ordinary FXML.
`FXMLLoader` constructs the view without a database, DNS connection or injection
context. The optional `ViewLoader` applies NeitzelLib binding declarations.

| Control | FXML configuration | Data and lifetime |
| --- | --- | --- |
| `de.neitzel.fx.view.DataGrid` | Columns, typed attributes, widths, filters, grouping, page size and setup identity | Bind `items` or `source`; explicitly load after configuration, close with the view |
| `de.neitzel.fx.view.Table` | `collection`, `row`, `Column` declarations | Existing ViewLoader collection/Component rows and `selection` binding |
| `de.neitzel.fx.control.StatusBar` | `leftItems`, `rightItems` containing ordinary Nodes | Bind the properties of the contained controls |
| `de.neitzel.fx.docking.DockPane` | `initialLayout`, `DockLayout`, group/split definitions | Attach a DockingManager to open application-owned content |
| `de.neitzel.fx.control.NonNegativeIntegerSpinner` | `minimum`, `maximum`, `initialValue` constructor attributes | Bind writable `number`, which shares the value factory's property |
| `javafx.scene.control.ToolBar` | Standard `items` and `orientation` | Standard JavaFX control, deliberately reused by the library |

Supporting objects such as coordinators, sources, setup stores, popup hosts,
result renderers, theme managers and tray integrations are application/runtime
objects, rather than controls to insert in a scene graph.

## DataGrid

```xml
<?import de.neitzel.fx.view.*?>
<?import javafx.scene.layout.VBox?>

<VBox xmlns:fx="http://javafx.com/fxml/1">
    <fx:define>
        <BindingList>
            <DirectBinding control="people" property="items"
                           path="people" direction="view"/>
        </BindingList>
    </fx:define>
    <DataGrid fx:id="people" keyAttribute="id" pageSize="50"
              maximumPageSize="250" groupingEnabled="true" groupBy="department"
              groupsCollapsed="false" setupId="people" setupVersion="1">
        <columns>
            <DataGridColumn id="name" header="Name" width="200"
                            checklist="SOURCE_WIDE"/>
            <DataGridColumn id="department" header="Department" width="180"/>
            <DataGridColumn id="age" header="Age" kind="INTEGER"/>
        </columns>
    </DataGrid>
</VBox>
```

Initialize the model's `List<Person> people` before loading. Bean and Java record
rows are supported. After `ViewLoader.load`, call the injected grid's `load()` or
`loadAsync()` on the FX thread. The data binding shares the model's observable
list. Add, remove, replace, permute or use an extractor update on that list to
request a refresh immediately, invalidating older pending results. The existing
query (filters/sort/page) is retained; use `setAll` for a single batch update. Replacing `grid.items` detaches
the old list. Rows are snapshots: mutating a field without an observable update
requires `refresh()`.

Configure before the first runtime operation. `initialize()` completes setup
without querying, allowing an application to attach persistence before calling
`loadAsync()`. Nothing loads in the constructor, and FXML without data displays
column headers in a query-free preview. State is null until initialization.

For ordinary `FXMLLoader`, provide the list in its namespace and use
`items="$people"`, or assign/bind `itemsProperty()` after loading. The same
explicit loading and closing contract applies. `DirectBinding` requires
`ViewLoader`; the usual collection-binding limitations in the main reference
still apply, including replacing model attributes through the ViewModel.

A local grid requires a stable unique row key: `keyAttribute="id"` reads a public
bean getter or record accessor. Alternatively supply `keyFactory` and
`keyComparator` in Java before initialization. Keys must be comparable when no
comparator is supplied. Duplicate/null keys fail the query visibly. The local
grid owns its virtual-thread executor and releases it on `close()`.

For an external queried grid, bind `source` instead of `items`. Column IDs must
match the source mappings. The source and executor remain application-owned.
Changing an initialized source starts a request using the current filters/sort
and first page, as with the programmatic interface. External checklist filters
need an explicitly supplied `candidates` provider; local sources supply one.

Column defaults: `attribute` uses `id`, `kind="TEXT"`, width 150, minimum 80,
maximum 1200, fixed position -1 (movable), filterable true, checklist disabled.
Kinds are `TEXT`, `INTEGER`, `DECIMAL`, `BOOLEAN`. Text and numeric filter editors
are built in; boolean values can render/group and use programmatic conditions,
but have no built-in editor. Set `filterable="false"` for display-only columns.
`groupAttribute` optionally separates stable group values from displayed/filter
values; `groupLabels` maps those values to display labels without changing group
identity. `type` supplies a stable schema type for saved setups; the default is
`neitzel.<kind>.v1`. Keep type IDs and setup versions stable across upgrades.

The default row factory creates labels. Supply a Java `rowFactory` for custom
nodes, styles, tooltips or actions; use `DataGridColumn.getValue(row)` to share
the FXML mappings. FXML can also reference factories using `$factory` namespace
values. Selection is available through the stable read-only
`selectedKeyProperty()` and `selectedElementProperty()`; call `select(key)` to
change it. Existing programmatic constructor and queried-grid behavior remain
supported.

## Bounded integer spinner

```xml
<?import de.neitzel.fx.control.NonNegativeIntegerSpinner?>
<NonNegativeIntegerSpinner xmlns:fx="http://javafx.com/fxml/1"
                           fx:id="quantity" minimum="0" maximum="100"
                           initialValue="1"/>
```

Use `<DirectBinding control="quantity" property="number" path="quantity"
direction="both"/>` in a ViewLoader binding list. Valid typing commits
immediately; invalid typing and bounds follow [bounded integer inputs](integer-spinners.md).
The original `IntegerSpinners.nonNegative(...)` factory remains available.

See [bars](bars.md), [docking](docking.md) and the main [FXML reference](../README.md)
for the existing controls. The host designer needs the updated library on its
classpath; this feature does not replace a separately pinned Designer runtime.
