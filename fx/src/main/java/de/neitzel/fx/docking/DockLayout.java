package de.neitzel.fx.docking;

import lombok.Getter;
import lombok.Setter;

/** Initial named destinations surrounding a central group. */
@Getter @Setter
public final class DockLayout {
    private DockGroupDefinition center;
    private DockGroupDefinition left;
    private DockGroupDefinition right;
    private DockGroupDefinition top;
    private DockGroupDefinition bottom;
}
