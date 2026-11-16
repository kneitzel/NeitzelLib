# Split groups and predefined destinations

A `DockGroupDefinition` is one named group. A `DockSplitDefinition` is a named
binary branch with `first`, `second`, an `Orientation` and an initial `position`
between zero and one. Either branch can itself be a split. Both definitions
implement `DockLayoutNode`, so Java and ordinary FXML describe the same tree.
Each initial destination name must be unique across the complete layout.

Move an open Dockable with `DockTarget.split(groupId, Side.RIGHT)` (or another
side). The manager checks the target and application placement policy before
allocating a new group identity. `SplitDirections` permits `ALL`, `NONE`,
`HORIZONTAL` (left/right), or `VERTICAL` (top/bottom). The new group starts with
the target's tab presentation, size constraints and splitting policy. The two
groups can subsequently be configured independently.

Group minimum dimensions default to 80 pixels each and are configurable through
`minimumWidth` and `minimumHeight`. Splitting requires both minima plus the
divider to fit. A dynamic split starts evenly. A drop near a disabled or
ineligible edge falls back to tab insertion when insertion is allowed; an
explicit API split request returns its rejection instead. Dropping a group's
only tab on its own edge makes no change.

When either branch loses its last Dockable, that split dissolves and its sibling
fills the area. Nested splits inside the surviving branch remain. The emptied
concrete group ID no longer resolves. The predefined area ID remains usable and
now names the surviving group or branch. An area containing multiple groups
chooses the most recently active eligible group, then the first eligible group
in visual order. Callers can still name a concrete group to control placement
precisely. Initially empty branches elsewhere in the layout are unaffected by
an unrelated move.

`DockLayoutView.Area.groups()` lists its leaf groups in visual order. `root()`
exposes the immutable hierarchy as `DockLayoutView.Group` and
`DockLayoutView.Split`, including the published divider proportion. Named nested
destinations appear as areas as well; one concrete group can consequently be
reachable through several area IDs.

Groups are persistent by default. Outside an emptied split, an empty persistent
group keeps its destination and initial space. Set `visibleWhenEmpty=false` to
hide its empty content, and `releaseSpaceWhenEmpty=true` to give that space back.
Releasing space also hides the empty content, regardless of `visibleWhenEmpty`.
An empty side destination remains available as a narrow drop target at that
edge; a hidden group that retains its space accepts drops in its reserved bounds.
The last occupied side extent is retained when content returns. A root with
`persistent=false` disappears after its last Dockable leaves, including its
destination IDs. These settings are captured at attachment and inherited by a
newly split group.

Run `DockingMain --splits` for a Java-defined nested editor layout, or
`DockingMain --splits --fxml` for its ordinary FXML equivalent. The explicit
split button, tab context menu and edge drops share the same split checks.
Move the Project tab away to release the tool area, then drag it back to the
right edge to fill its persistent target again. The ordinary `--fxml` mode and
Java default still start with one editor group.

The split tests exercise both emptied-branch directions, nested ordinary FXML,
minimum-size and direction rejections, and area fallback after a placement
policy changes. The companion docking example demonstrates these interactions
through public operations.

## Resize boundaries

Both split dividers and the inner boundaries of predefined side areas are
draggable. Horizontal resizing uses the left/right cursor; vertical resizing
uses the up/down cursor. The content follows the pointer while group minima
remain in force. A boundary covered by a pending close request cannot resize;
unrelated areas remain available.

The read-only layout keeps its previously published geometry during the drag.
Releasing the pointer publishes the completed size or proportion once.
`Area.preferredWidth()` holds a left/right area's remembered content width;
`preferredHeight()` holds a top/bottom area's remembered content height. The
other dimension is `-1`, meaning it follows the available space. The five-pixel
outer divider is separate from the content extent. A side area's remembered
extent survives replacement of its split branch and temporary hiding.

The surrounding region has style class `dock-edge-area`; its resize handle has
`dock-area-divider`. The ordinary JavaFX `split-pane-divider` style class applies
to split dividers. These handles do not replace or recreate the application
content when resizing.
