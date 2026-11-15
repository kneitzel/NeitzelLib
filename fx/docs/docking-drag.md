# Moving tabs within and between windows

Drag a tab header to another group's content area to append it. Dropping on
a tab strip inserts at the displayed marker, including when reordering within
the same group. The tab's text/icon bindings and its live content stay intact.
All four tab sides are supported.

Dropping near a content edge creates an adjacent group and previews the half
that will receive the tab. The target group's allowed split directions,
minimum dimensions and application placement rule determine whether the split
is available. A rejected edge split falls back to joining that group. Moving
the sole tab to its own group's edge is an explicit no-change operation.
The tab strip always retains its insertion behavior.

Persistent empty groups remain drop destinations. When they release their
space, a narrow drop indicator appears at their configured outer edge during
the gesture. Their content remains hidden until a tab is actually placed there.
An invisible group that keeps its space accepts drops in its reserved area.

The move preview uses the same `checkMove` rules as an application command.
Placement is checked again on release. A rejected location has no marker;
a move that would leave the tab where it is uses a muted marker. Escape
cancels a pending gesture. Releasing over another part of a managed window,
including its global toolbar, status area or title bar, leaves the Dockable in
place. That invalid managed-window location does not create a floating window.
Releasing outside every managed window creates a new floating window when the
same placement rules permit it. Main-window-only Dockables have no floating
destination.

Every tab also provides a **Move to** context submenu. It lists the currently
available groups and the selected content's current title. Rejected targets
are disabled with their reason; selecting an enabled item invokes the same
manager move operation and rechecks the destination.
The **Split** submenu offers the four directions for the tab's current group,
with unavailable directions disabled and explained. These explicit commands
request a split without the pointer gesture's join fallback.

The preview is mouse-transparent and has CSS class `dock-drop-indicator`.
A floating preview uses `dock-floating-preview`, with a
`dock-floating-preview-frame` and `dock-floating-preview-label`.
Its bounds are fitted to a visible screen before display; it does not allocate
a managed window. The in-window indicator's `no-change` pseudo-class identifies a preview that leaves the current
placement intact. Applications may style its fill and stroke using JavaFX CSS.

## Window routing and input delivery

The interaction uses ordinary JavaFX mouse pressed/dragged/released events and
their screen coordinates to resolve a destination across the same manager's
main and floating windows. It does not start the JavaFX Dragboard protocol or
exchange serialized content between applications. The original Node remains
live throughout the transfer; empty source groups and floating windows are
removed as part of the completed operation.

Insertion markers appear on tab strips; split previews show the receiving half
of a content area. A muted marker means no placement change. A floating preview
shows the proposed new window outside managed windows. Invalid destinations
have no accepted placement preview. Previewing a collapsed overlay rail does
not open it; a successful drop opens the group in its remembered mode and
shared size and selects the incoming tab. The previous overlay in that window
collapses. Escape clears the gesture and preview without changing layout or
content visibility.

The gesture regression tests that fire JavaFX events at visible tab headers
verify destination rules and public layout state. They do not establish native
mouse delivery across windows. `NativeDockDragTest` exercises real AWT Robot
mouse gestures and keyboard input on a desktop. It checks Main → new Floating
→ Main and Floating → Floating, preview without commit, invalid managed-window
drops, Escape for both in-window and floating previews, main-window-only rejection,
and a cross-window drop into a collapsed overlay. After each transfer, it types without a focus-repair click
and verifies the exact text at the retained selection/caret. Scene focus or
`focusedProperty` alone is not its acceptance criterion.

On 2026-11-15 these three native scenarios passed on macOS 27.2, Java 25 and
JavaFX 27.0.1, with zero failures, errors or skips. They retained the original
editor Nodes and selection/caret, and delivered actual text without a repair
click, including after invalid drops and cancellation. The overlay scenario
retained its configured 240-pixel group width and opened only on successful drop.
Other operating systems have not been qualified by this run.

The native test is opt-in because it moves the desktop pointer and sends real
keystrokes. Run it with exclusive access to the desktop and no parallel JavaFX
GUI tests:

```sh
./mvnw -pl fx -am -Dtest=NativeDockDragTest \
  -Dneitzellib.nativeDragTest=true \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

It releases held keys/buttons, restores the pointer and closes its windows in
cleanup. Without the opt-in flag it is explicitly skipped. Execution results
qualify the tested OS/JDK/JavaFX combination; synthetic-event tests and a green
compile do not establish native delivery on other platforms.

## Desktop demonstration

Launch `de.neitzel.fx.example.docking.NativeDragDockingMain` from `fx-example`.
It starts Notes in the main window, Readme in a separate floating window and a
collapsed Project overlay at the left. Project is restricted to the main
window. The main toolbar and status area are deliberately invalid drop targets.
No MVVM setup is required.

1. Notes starts with `cd` selected in `abcdef`. Drag its tab onto Readme's content
   area, release, and type `X` without clicking the content. The first line
   becomes `abXef`; Readme remains unchanged.
2. Drag Notes onto the main editor area and type `Y` immediately. The text
   becomes `abXYef`. Move Readme back into its own window with **Separate Readme
   window** whenever another floating destination is needed.
3. Drag Notes outside all managed windows. Inspect the floating preview, then
   release to create its own window. Type immediately at the existing caret.
4. Drag Notes from that floating window onto the main toolbar or status area.
   Release: placement stays unchanged. Repeat toward a valid target but press
   Escape before releasing: the preview disappears and the tab stays put.
5. Drop on a tab-strip insertion marker to reorder tabs. Drop near a content
   edge to split, or drag the sole tab toward its own group edge to inspect the
   muted no-change preview. Unavailable splits fall back to joining the group.
6. Drag Notes onto the collapsed Project rail. It stays collapsed during the
   preview, then opens after a successful drop with the same overlay mode and
   remembered width. Continue typing without another click.
7. Show Project and try to drag its tab outside managed windows or into Readme's
   floating window. The main-window-only restriction rejects those destinations.

The buttons are setup/recovery commands; dragging uses the same move checks and
live contents. Closing the demonstration's main Stage hides its floating
windows; application-wide shutdown coordination is supplied by its own slice.
