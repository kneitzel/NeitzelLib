# Overlay and pinned tool groups

`DockGroupMode.OVERLAY` lets an expanded tool group cover the editor while only
its tab rail reserves space. `DOCKED` pins the group into the layout and reserves
its full extent. Mode and remembered dimensions belong to the whole group;
selecting another tab does not change them. Overlay mode requires a collapsible
group, and initial overlay groups start collapsed.

Configure an ordinary JavaFX/FXML group with `mode="OVERLAY"` and
`collapsible="true"`. The same configuration is available through
`DockGroupDefinition.setMode`. For a live group, use the public atomic update:

```java
manager.updateGroup(groupId, DockGroupChanges.builder()
        .mode(DockGroupMode.DOCKED)
        .build());
```

Opening an overlay, selecting one of its tabs or activating a contained Dockable
closes the previous overlay in the same window. Pinned groups stay open. Clicking
outside an expanded overlay within a managed JavaFX Scene collapses it; Escape
from its content also collapses it and returns focus to its header. This is not
a global desktop mouse hook: clicks in other applications are outside that
observation boundary. A pending close protects affected groups against these
conflicting changes.

Dragging toward a collapsed overlay rail shows a drop preview without opening
the group. Escape cancels that preview. A successful drop opens the target,
activates the moved content, preserves its group mode and size, and collapses
the previous overlay in that window. These behaviors are verified for all four
edge directions through live JavaFX controls and public manager state.

Each tab's context menu provides **Pin group**, also available through keyboard
context-menu navigation. The action changes the entire group. An invalid
combination, such as an overlay that cannot collapse, is rejected without
changing the layout.

## Runnable FXML example

Launch `de.neitzel.fx.example.docking.OverlayDockingMain` in the `fx-example`
module. It loads `OverlayDocking.fxml` directly and uses no MVVM services. The
center editor remains docked; Project and Changes start as collapsed overlays
with remembered widths of 240 and 260 pixels.

1. Choose **Show Project**, then **Show Changes**. Only the most recent overlay
   stays expanded. The status line reports both groups.
2. Click inside the editor or press Escape inside an overlay to collapse it.
3. Show Project and choose **Pin Project**. It now reserves layout space.
   Showing Changes leaves the pinned Project group expanded.
4. Choose **Unpin Project** to return it to overlay mode without changing the
   content instance or remembered width.
5. Focus a tool-tab header, open its context menu with the platform keyboard
   context-menu command, and select **Pin group**.
6. Drag a tab onto the opposite overlay rail. Cancel one preview with Escape;
   complete the next drop to inspect selection, overlay arbitration and size.

Build the example and its actual-FXML integration check without installing
runtime artifacts:

```sh
./mvnw -pl fx-example -am -Dtest=OverlayDockingExampleTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
