# Docking

Docking lives in the `fx` artifact and works with ordinary JavaFX Nodes and a
standard FXMLLoader. Neither a ViewLoader nor an injection context is needed.
A loaded NeitzelLib View root can also be used as content.

## Open content

Create a `DockLayout` with named `DockGroupDefinition` destinations, assign it
to a `DockPane`, and embed that pane in your main Stage's Scene. Then call
`DockingManager.attach(stage, pane, new DockingOptions())`. Attachment is allowed
before showing the Stage. A pane and main Stage can each have one active manager.
The initial definition is captured; later changes to the definition do not alter
the attached layout. The manager also captures the placement rule from
`DockingOptions`; replacing that option later does not replace the live policy.

A Dockable consists of a stable `DockableId` and the existing content Node.
Use `open(dockable, DockTarget.area("editors"))` to open it. Opening an ID that
is already open activates the existing Dockable and keeps its placement and
content. The title and icon expose ordinary JavaFX properties for bindings.
Groups can place their tab strip at any JavaFX `Side`; a blank title with an
icon provides a compact icon tab.

Call operations on the JavaFX Application Thread. `ActionResult` distinguishes
`Applied`, `NoChange` and `Rejected`; the latter carries a machine-readable
`DockFailure`. Attachment errors throw with a diagnostic because no manager
exists yet to return an action result.

## Observe and activate

`layout()` and the read-only `layoutProperty()` expose immutable windows,
areas and groups, including the selected Dockable and tab order. Empty named
destinations are included. A selected-tab change publishes its completed state.
`findDockable(id)` reads an open instance without activating or creating it.
`activeDockable()` and its read-only property supply the target for global
application actions. Moving keyboard focus to a global button preserves it.

`activate(id)` selects the Dockable and requests focus within its content.
The content instance remains owned by the application. A supplied
`setFocusTarget` chooses a Node within the content; otherwise the manager tries
the previously focused descendant. Future slices add splits, close consent
and restoration.

## Move and check placement

The [pointer and context-menu guide](docking-drag.md) describes drag previews,
relative insertion and cancellation in the main window.

`move(id, target)` transfers the same content instance to a named area or a
concrete group. An area's default insertion position is the end. Group targets
also accept `TabPosition.before(id)` and `after(id)` for exact ordering.

```java
var destination = DockTarget.group(toolsGroupId, TabPosition.before(projectId));
MoveCheck check = manager.checkMove(notesId, destination);
if (check instanceof MoveCheck.Allowed) {
    ActionResult result = manager.move(notesId, destination);
    // Handle Applied, NoChange or Rejected in application presentation code.
}
```

The check reports the currently resolved insertion, no change or a rejection.
It does not mutate the layout. Execution resolves and checks again, so menus
must handle a later rejection. Missing groups and missing reference tabs have
distinct failure codes. One successful move publishes its completed layout
once and selects, activates and focuses the destination content.

Set `DockingOptions.setPlacementRule` before attachment to supply a policy.
It receives the Dockable and concrete proposed destination, returning an empty
Optional to permit it or a DockFailure to reject it. The rule must be
side-effect-free. Opening and moving use the same policy; a thrown policy
exception becomes a diagnostic rejection without a layout change.

## Lazy content and rejected opens

`open(id, factory, target)` calls the factory on the FX thread only when the
ID is not already open. Reopening an ID activates its existing content, even
when the caller specifies a different destination. Use move to change placement.

If the manager-created content is rejected, its `onDiscarded` callback releases
resources exactly once for that preparation. A directly supplied Dockable
stays with the caller on rejection. A failed discard is reported through the
original rejection: its diagnostic cause retains any original cause and lists
the cleanup exception as a suppressed exception. Moving does not discard content.
Factory load failures and mismatched IDs are rejected with diagnostics; a
factory cannot discard an instance that is already open.

## Java and FXML example

The `fx-example` module includes `de.neitzel.fx.example.docking.DockingMain`.
Its Java-built layout and `--fxml` variant open the same editor and right-side
tool groups. It demonstrates unsaved editor text, tab selection, activation and
movement without MVVM. Build the reactor with Java 25, then launch
that main class using the example's runtime dependencies; no Designer build is
required.
