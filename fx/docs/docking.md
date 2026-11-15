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
the attached layout. `DockingOptions` currently has no policy fields in this
first implementation slice.

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
The content instance remains owned by the application. Future docking slices
extend this API with movement, splits, close consent and restoration.

## Java and FXML example

The `fx-example` module includes `de.neitzel.fx.example.docking.DockingMain`.
Its Java-built layout and `--fxml` variant open the same editor and right-side
tool groups. It demonstrates unsaved editor text, tab selection and a global
Activate Notes action without MVVM. Build the reactor with Java 25, then launch
that main class using the example's runtime dependencies; no Designer build is
required.
