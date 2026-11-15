# Application-owned layout snapshots

`DockingManager.snapshot()` returns immutable, versioned layout data from the
last completed publication. It contains window IDs and committed bounds,
canonical root placement, nested splits and proportions, stable area aliases,
group configuration, tab order and selection, and the active Dockable ID.
Remembered expanded dimensions are separate from preferred group settings, so
collapsed and overlaid groups can retain their effective size. Return positions
and initial placement intentions are also data.

A snapshot contains no JavaFX Nodes, controllers, ViewModels or document text.
The application chooses serialization, storage, migration and document loading.
Reading a snapshot does not commit an unfinished resize gesture.
Invoke manager operations on the JavaFX application thread; pass the immutable
snapshot to application storage separately.

## Restore before showing a fresh workspace

Load a `DockPane` using Java or ordinary FXML, attach it to the new main Stage,
and restore while its manager contains no Dockables. The captured initial layout
is the fallback if validation or content preparation fails.

```java
DockingManager manager = DockingManager.attach(stage, pane, options);
RestoreResult result = manager.restore(saved, id -> {
    try {
        return new DockableResolution.Available(loadDocument(id));
    } catch (DocumentLoadException failure) {
        return new DockableResolution.Failed(failure);
    }
});
stage.show();
```

Here `loadDocument` and `DocumentLoadException` are application code. A successful
resolver returns a fresh, unattached Dockable with the requested ID. Group and tab
order determine preparation order. Restore validates the snapshot and all
prepared contents before installing them, then publishes the completed layout.
Docked groups retain their collapsed state. Temporary overlays restore collapsed
while retaining overlay mode and remembered size.

`RestoreResult.Rejected` separates the primary `DockFailure` from cleanup
diagnostics. For example, `DockableResolution.Failed(cause)` produces
`DOCKABLE_LOAD_FAILED`. Already prepared available contents receive `onDiscarded`
once; a cleanup failure is reported separately and does not replace the original
cause. A rejected restore keeps the previous empty layout intact. It never uses
normal close consent or `onClosed` to discard unused prepared content.
An already attached resolver or factory result is rejected before the manager
adopts it. Contents belonging to this or another live manager receive neither
`onDiscarded` nor `onClosed` on that rejection. Fresh, unused prepared contents
remain subject to the discard contract.

Restore includes the main window and floating hosts with their saved return
destinations. The same window customizer configures new and restored hosts.
Prepared floating windows remain hidden until the main Stage is shown; failed
preparation never shows them. Customizers and placement checks finish before the
layout is installed.
Floating bounds outside the available displays are fitted to a current screen,
preserving the saved size as far as it fits. Return uses the saved main-window
destination, including persistent groups hidden while empty.

`DockableResolution.Missing` skips that identity and reports it in
`RestoreResult.Applied.missingIds()`. Empty split branches dissolve while stable
area aliases remain available; configured persistent empty side groups survive.
Available contents are checked against the application's current placement rule.
If a saved destination is rejected, restore tries the explicitly configured
`DockingOptions.fallbackTarget`, such as `DockTarget.area("primary")`. It does not
silently choose an arbitrary group. If any available content has no eligible
destination, the entire restore is rejected with that ID and all freshly prepared
contents are discarded; the live fallback layout remains unchanged.
An available Dockable that has become `mainWindowOnly` uses an explicitly
configured main-window fallback. Floating hosts emptied by Missing or fallback
are omitted entirely, including their customizer calls.
Use [live reset](../../fx-example/docs/docking-reset.md) to rearrange already open
contents without replacing their Nodes or losing their unsaved state.

## Runnable Java/FXML example

`SnapshotDockingMain` loads `SnapshotDocking.fxml`, which declares nested editor
splits and a right-hand overlay. The application retains the snapshot in memory
and creates a new empty workspace for each restoration attempt. Fresh TextAreas
come from the example's separate document source.

Build from the repository root using Java 25 without installing runtime artifacts:

```sh
./mvnw -pl fx-example -am package
java --enable-native-access=ALL-UNNAMED \
  -cp 'fx-example/target/classes:fx/target/classes:fx-example/target/libs/*' \
  de.neitzel.fx.example.docking.SnapshotDockingMain
```

1. Resize the editor splits, select tabs and open Project. Choose **Capture layout**.
2. Choose **Restore saved layout**. A new window restores the arrangement before
   it is shown; Project starts collapsed. The original window remains usable.
3. Choose **Try incompatible version**. The new window retains its initial empty
   fallback, and the status reports that no content was prepared.
4. Choose **Simulate load failure**. The status reports the original load cause,
   prepared and discarded content counts, and separate cleanup diagnostics.
5. Enable **Simulate cleanup failure** and repeat the failed load to see cleanup
   diagnostics alongside the unchanged primary failure.
6. Choose **Restore without Preview/Project**. The status names both missing IDs;
   the nested Preview split dissolves and the persistent empty Project side target
   remains. The original workspace and its unsaved text remain open.
7. Choose **Restore with changed rule**. In the supplied starting arrangement,
   Project is no longer permitted in the tools group and joins Notes in the
   configured editor fallback, `primary`. The status names that fallback.
8. Choose **Reject Project placement**. Project is rejected at both its saved
   destination and the fallback. The new window retains its empty starting layout;
   the status identifies Project and reports all four freshly prepared contents
   discarded. The original workspace remains untouched.
9. Choose **Prepare floating snapshot**. Preview and Project become two floating
   windows; Project's persistent side target hides while empty. The captured
   snapshot includes their return destinations.
10. Choose **Restore saved layout**. A fresh main window and two floating hosts
    appear. The status reports zero floating windows visible before main-window
    display and two afterward. Source and restored hosts use the same custom title.
11. Choose **Restore off-screen windows**. This changes only the saved floating
    positions to simulate unavailable monitors. The new hosts fit the available
    displays. In the new main window, **Return Project** brings the same content
    back to its preserved target and removes its empty floating host.
12. Choose **Restore partial floating**. Preview is missing; Project now requires
    the main window and uses the configured editor fallback. Only the new main
    window appears, with three contents. No empty floating hosts are created.
13. Choose **Abort floating customizer**. The second floating-window customizer
    throws. Neither prepared floating host appears, the new main window retains
    its empty fallback, and all four prepared contents are discarded.

The display and startup checks run against actual JavaFX windows on the current
macOS desktop. Off-screen saved bounds simulate monitor changes; physically
disconnecting monitors and Windows/Linux desktop startup have not been qualified
by these example tests.

The example's **Capture layout** command saves layout data only. Reloaded document
text comes from its application source; document persistence is a separate concern.
