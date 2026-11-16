# Reference docking workspace

The `fx-example` package `de.neitzel.fx.example.docking.workspace` combines the
public docking workflows in one application. `ReferenceWorkspace` builds the
same initial layout from Java or ordinary FXML. The default editors use plain
JavaFX; optional MVVM changes the Notes editor adapter, not the docking API.

## Build and launch

From the NeitzelLib checkout with Java 25, build without installing over local
runtime artifacts:

```sh
./mvnw -pl fx-example -am -DskipVersions=true -Dversions.skip=true package
workspace_dir="$(mktemp -d)"
java --enable-native-access=ALL-UNNAMED \
  -cp 'fx-example/target/classes:fx-example/target/libs/*' \
  de.neitzel.fx.example.docking.workspace.ReferenceWorkspaceMain \
  --store="$workspace_dir/workspace.json"
```

The default loads ordinary FXML and uses plain JavaFX editors. Append `java` to
construct the same layout in Java, or `mvvm` to use the optional MVVM Notes
adapter; both arguments can be combined. The explicit `--store` path determines
the only workspace file the example reads or replaces. Restart with that same
path to restore the saved arrangement and document text. Malformed or unsupported
application data produces an explanatory window and leaves the file unchanged.

## Follow the application boundary

The constructor receives a Stage, the Java/FXML and MVVM choices, a
`WorkspaceStore`, and optional previously loaded `StoredWorkspace` data. The
application loads file data before constructing the workspace, attaches its
DockingManager on the JavaFX thread, and restores into the empty manager before
showing the main Stage. Floating windows prepared by restore appear after it.

The library's `LayoutSnapshot` describes arrangement and identities. The
example's `StoredWorkspace` adds a separate map of document titles and text.
`WorkspaceJson` defines the application's versioned JSON representation;
`WorkspaceStore` writes a temporary file and replaces the selected workspace
file. None of those serialization types belong to the `fx` API. The example
adds Gson as its own dependency; docking does not require it.

| Part | Responsibility |
| --- | --- |
| `ReferenceWorkspace` | Layout startup, real controls, editor creation, diagnostics and shutdown callbacks |
| `StoredWorkspace` | Immutable application data: layout plus independent document records |
| `WorkspaceStore` | Reading and replacing the selected application file |
| `WorkspaceJson` | Application-specific tags and adapters for layout values |
| `DockingManager` | Placement, windows, groups, focus, consent and completed layout publication |

## Exercise the combined workflows

The initial workspace has central editors, Project on the left, Changes in a
right overlay, and a persistent empty bottom destination. Global toolbar and
status controls remain outside the DockPane; each editor keeps its own local
bar when moved. Drag tab headers to move, split or float content, drag dividers
to resize, and use the tab context menu to return floating content. Click an
active side symbol to collapse its whole group; another symbol reopens that
group with the selected content. Reset rearranges the current editor Nodes and
keeps their unsaved text.

The application's placement and missing-document controls make restoration
outcomes visible in its status and event log. Missing IDs permit partial
restoration. A changed placement rule uses only the configured eligible
fallback; an unresolvable placement rejects the whole preparation. The example
reports that failure and opens fresh default content in the initial layout. A close veto leaves every affected
editor and floating window open and prevents the shutdown-save callback.

Quit and manager replacement use the same all-consent workflow. Only after
every Dockable allows closing does the application receive the complete
pre-close snapshot. A failed file save keeps that workflow pending so Retry
can try again or Cancel can leave the workspace open. The manager's close scope
covers the save wait. On successful manager replacement the Stage remains open,
old application resources are released and a fresh manager attaches to the same
DockPane. Restoration creates fresh editor adapters from the newly saved data.
**Reload saved** uses this same save-before-replacement workflow, so it protects
current edits instead of silently reverting them to an older file.

## Ownership, threading and lifetime

Read and write files outside the JavaFX thread. Capture document text and call
manager operations on that thread, then pass immutable data to the I/O worker.
Consent and save stages can finish asynchronously; the library resumes close
processing on the JavaFX thread. Do not block that thread waiting for a future.

The application owns editor resources. Moving, floating, returning and resetting
retain the same content Node and do not call cleanup callbacks. Actual close
calls `onClosed`; a failed factory or restore preparation calls `onDiscarded`
for newly adopted, unused content. An already attached foreign Node stays with
its existing owner. A callback failure is diagnostic and does not stop other
required cleanup.

Optional MVVM uses an already loaded View root with ordinary docking callbacks.
The example releases its own bindings/listeners. It does not promise eviction
of the existing MVVM identity cache or a general LoadedView disposal mechanism;
see [MVVM lifetime](architecture.md) and the [editor integration](../../fx-example/docs/docking-mvvm.md).

The FXML path is ordinary FXMLLoader input. Designer authoring, transfers between
independent managers/processes and JavaFX Dragboard interoperability are outside
this implementation. Native input has to be qualified on each target desktop;
see the [drag](docking-drag.md) and [keyboard](docking-accessibility.md) guides for
the executed platform checks.

## Verified integration

On 2026-11-16, the complete affected reactor passed `verify` with Java 25,
JavaFX 27.0.1 and macOS 27.2. All 329 `fx` tests and 34 `fx-example` tests ran
without failures or skips. The reference application contributes five
Java/FXML/storage/MVVM workflow cases, three actual file-boundary cases and one
native desktop case. The latter drags Notes into a new window, replaces selected
text, returns it through the real keyboard context menu and types again without
a focus-repair click. It also observes every consent before cleanup, a veto,
a pending/failed save and successful persistence before final closure.

To rerun that native application case, use an exclusive desktop session:

```sh
./mvnw -pl fx-example -am -DskipVersions=true -Dversions.skip=true \
  -Dtest=NativeReferenceWorkspaceTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dneitzellib.nativeWorkspaceTest=true test
```

The suite asserts exact text, retained Node identities, visible outcomes and
saved application data. The Changes list is illustrative content, not a file
watcher. Windows/Linux native delivery and full screen-reader behavior are not
established by this macOS qualification.
