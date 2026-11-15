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

This milestone restores snapshots containing the main window. Floating-window
data is captured, but restoring floating hosts currently returns
`UNSUPPORTED_SNAPSHOT_FEATURE` before invoking the resolver; ticket 14 adds that
capability. Partial restoration for missing IDs is supplied by ticket 13.
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

The example's **Capture layout** command saves layout data only. Reloaded document
text comes from its application source; document persistence is a separate concern.
