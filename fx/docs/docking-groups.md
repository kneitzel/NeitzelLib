# Collapsible tab groups

A group shares its content size, tab side, minimum dimensions, splitting policy
and empty-space behavior across its tabs. Enable `collapsible` on an initial
`DockGroupDefinition` in Java or ordinary FXML. It defaults to `false`, so an
editor area keeps its content space and rejects collapse requests unless the
application explicitly enables them.

Clicking the selected visible tool tab collapses the group to its header rail.
The same headers and icons remain available. Clicking any collapsed tab opens
the group at its remembered size and selects that tab. Within a split, the
collapsed leaf keeps only its rail extent and its sibling receives the released
space. `Split.position()` retains the remembered expanded proportion during
that temporary allocation; reopening restores it. The split divider is hidden
while a branch is collapsed. Escape inside an open
collapsible group also collapses it. Keyboard focus returns to the selected
header; the active Dockable falls back to the most recently active visible
selected content, or becomes null. Other collapsed groups stay closed.

`setGroupCollapsed(groupId, boolean)` supplies the same state transition.
`activate(dockableId)` opens the Dockable's group before focusing its content.
A drag preview does not open a collapsed group: only the successful drop does;
Escape cancels the preview without changing the layout.

```java
manager.updateGroup(toolsId, DockGroupChanges.builder()
    .preferredWidth(260)
    .tabSide(Side.RIGHT)
    .collapsible(false)
    .build());
```

Only explicitly supplied settings change. In particular, `false` is a supplied
value, not an omitted setting. The manager validates the resulting definition
before applying anything, publishes one completed layout state, and returns a
rejection without partial changes for invalid dimensions. An empty or identical
patch returns `NoChange`. Disabling collapse opens a collapsed group as part of
the update. A missing group returns `TARGET_NOT_FOUND`; a pending close protects
its group from changes. Geometry changes also protect neighboring content that
would be resized while its close consent is pending; unrelated policy updates
remain possible. The initial definition supplied at attachment remains
independent of live updates.

`DockGroupChanges` covers tab side, preferred/minimum width and height,
persistence, empty visibility, empty-space release, collapsibility, display mode
and split directions. Each new split receives its own copy of its target's settings.
See [overlay and pinned groups](docking-overlays.md) for shared dimensions,
keyboard pinning and temporary display above the docked content.

Run `DockingMain` (or `--fxml`) and select Project. Click it again to collapse,
then click Changed files to reopen the same group. The toolbar also exposes
explicit collapse/open and shared-width updates. The `--splits` variants retain
these group actions in the nested editor demonstration.
