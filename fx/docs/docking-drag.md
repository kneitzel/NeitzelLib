# Moving tabs in the main window

Drag a tab header to another group's content area to append it. Dropping on
a tab strip inserts at the displayed marker, including when reordering within
the same group. The tab's text/icon bindings and its live content stay intact.
All four tab sides are supported.

The move preview uses the same `checkMove` rules as an application command.
Placement is checked again on release. A rejected location has no marker;
a move that would leave the tab where it is uses a muted marker. Escape
cancels a pending gesture. Releasing over another part of the managed window,
outside the docking groups, leaves the Dockable in place.

Every tab also provides a **Move to** context submenu. It lists the currently
available groups and the selected content's current title. Rejected targets
are disabled with their reason; selecting an enabled item invokes the same
manager move operation and rechecks the destination.

The preview is mouse-transparent and has CSS class `dock-drop-indicator`.
Its `no-change` pseudo-class identifies a preview that leaves the current
placement intact. Applications may style its fill and stroke using JavaFX CSS.

This slice covers the main window. Splitting, floating, cross-window pointer
delivery and edge overlays extend these gestures in their respective tickets.
The gesture regression tests fire JavaFX events at visible tab headers and
observe the public manager state. Those tests do not by themselves establish
native mouse delivery across windows; that has a separate desktop acceptance
sequence.
