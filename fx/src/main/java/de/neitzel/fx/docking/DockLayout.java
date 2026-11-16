package de.neitzel.fx.docking;

import lombok.Getter;
import lombok.Setter;

/** Initial named destinations surrounding a central group. */
@Getter @Setter
public final class DockLayout {
    private DockLayoutNode center;
    private DockLayoutNode left;
    private DockLayoutNode right;
    private DockLayoutNode top;
    private DockLayoutNode bottom;
}
