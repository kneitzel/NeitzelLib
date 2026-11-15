# Quit, save and replace a docking workspace

`requestMainWindowClose()` asks every Dockable in the manager, including contents
in floating windows and collapsed groups. Every close check runs even if an
earlier one vetoes. Nothing closes until all checks allow the operation. A veto
returns `CloseResult.Vetoed` and skips the shutdown-save callback.

Configure `DockingOptions.beforeShutdown` before attaching the manager. After
unanimous consent, the callback receives a `ShutdownRequest` with the complete
immutable pre-close `LayoutSnapshot` and a `ShutdownReason`: `MAIN_WINDOW` or
`MANAGER_ONLY`. Application document saving still belongs to the application and
its close checks; the snapshot contains arrangement data, not editor text.

```java
DockingOptions options = new DockingOptions();
options.setBeforeShutdown(request -> saveLayoutWithRetry(request.snapshot()));
DockingManager manager = DockingManager.attach(stage, pane, options);
```

Here `saveLayoutWithRetry` is an application function returning a
`CompletionStage<ShutdownDecision>`. Keep that stage pending while saving or
retrying. The manager leaves all contents and windows open and holds the close
scope throughout that wait. Complete with `PROCEED` after a successful save, or
`CANCEL` to keep the workspace open. Throwing from the callback or completing its
stage exceptionally returns `Rejected` with `SHUTDOWN_CALLBACK_FAILED` and the
original cause; it does not close contents. The callback runs on the JavaFX
thread, and the returned stage may complete on a worker thread.

After `PROCEED`, the manager detaches contents and calls every `onClosed` callback.
Cleanup failures are reported in `Closed.cleanupFailures()` and do not turn an
already completed close into a rollback. The close scope remains active through
resource cleanup.

## Main-window ownership

The default `MainWindowCloseMode.AUTOMATIC` connects the Stage's close control to
the manager and hides the main Stage only after a successful main-window close.
Route an application's Quit command through `requestMainWindowClose()` as well.

With `APPLICATION_COORDINATED`, the application owns its close-request handler
and decides when to hide the main Stage after `Closed`. Consume the close event
while consent and saving are pending:

```java
stage.setOnCloseRequest(event -> {
    event.consume();
    manager.requestMainWindowClose().thenAccept(result -> {
        if (result instanceof CloseResult.Closed) stage.hide();
    });
});
```

Choose the mode before `attach`; changing the original options afterward does
not change the attached manager.

## Replacing the embedded workspace

`requestManagerClose()` uses the same checks and save workflow with reason
`MANAGER_ONLY`. On success it closes Dockables and floating windows, removes the
manager's handlers and releases the pane and Stage for another attachment. The
main Stage stays open. A veto, cancellation or failed save leaves the current
manager usable.

```java
manager.requestManagerClose().thenAccept(result -> {
    if (result instanceof CloseResult.Closed) {
        DockingManager replacement = DockingManager.attach(stage, pane, nextOptions);
        openNewWorkspaceContents(replacement);
    }
});
```

The replacement application creates or loads new contents. Use
[`resetLayout()`](../../fx-example/docs/docking-reset.md) when the intention is to
rearrange existing editors while retaining their Nodes and unsaved text.

## Quit during an existing floating-window close

A main-window request waits behind an already running floating-window close.
If the floating close succeeds, Quit asks only the remaining Dockables and then
captures their full pre-close snapshot. If it vetoes or fails, the waiting Quit
adopts that result without starting new checks or saving. A later explicit Quit
starts fresh checks. Repeated requests for the same running workflow share its
answer rather than invoking another application prompt.

The example makes this sequence visible:

1. Select **Delay Preview window consent** and click **Close Preview window**.
   Preview stays open while its one consent answer is pending.
2. Click **Quit workspace**. The status shows that Quit is waiting; Notes and
   Project have not been asked and no snapshot has been saved.
3. **Allow Preview** closes that independently approved window, then Quit asks
   Notes and Project and waits at the normal save controls. The snapshot now
   contains their one remaining main window.
4. Alternatively, **Veto Preview** leaves all three contents open and ends the
   waiting Quit with the same veto. Clicking **Quit workspace** again performs
   fresh checks and reaches the two-window save workflow.

The asynchronous consent controls remain usable while Quit waits. The public
scope property continues to describe the running floating close until the queue
can advance; a pending global save then owns the manager-wide scope.

## Runnable Quit/Replace example

`ShutdownDockingMain` loads `ShutdownDocking.fxml`: Notes occupies the center,
Project starts collapsed on the left, and Preview is floating. The example uses
ordinary JavaFX without MVVM.

1. Select **Preview vetoes last**, then **Replace workspace**. The log shows all
   three checks, no snapshot callback and no released contents.
2. Clear the veto and request **Quit workspace** or **Replace workspace**. The
   application displays the pre-close reason and the two-window snapshot.
3. **Retry save** fails while **Simulate save failure** is selected. The same
   pending request retains every editor. Clear that checkbox and retry, or use
   **Save successfully**, to complete with `PROCEED`.
4. **Cancel shutdown**, **Fail asynchronously** and **Throw before saving** show
   the distinct canceled and rejected paths without losing the current editors.
5. **Replace workspace** followed by successful saving attaches fresh contents
   to the same DockPane. **Next workspace** selects the ownership mode captured
   by that new attachment. In application-owned mode, a successful Quit leaves
   **Close application window** available for the caller's final action.

The example stores the saved snapshot in application-owned memory and deliberately
simulates failures; it performs no disk I/O. Connect the same callback to your
storage implementation for a real application.

After building local artifacts, launch from the NeitzelLib root:

```bash
java -cp 'fx-example/target/classes:fx-example/target/libs/*' \
  de.neitzel.fx.example.docking.ShutdownDockingMain
```

The public workflow tests exercise consent and save ordering, pending saves,
both ownership modes, cleanup, captured options and reattachment. Four example
integration cases drive the actual Java/FXML controls through veto, retry,
cancellation, failures, replacement, both main-close ownership modes and queued
Quit after delayed floating consent. The queue cases use ordinary JavaFX turns
to verify callback counts, retained editor identity and fresh checks after a
veto. All four example scenarios pass without failures, errors or skips.
