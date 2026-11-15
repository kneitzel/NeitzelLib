package de.neitzel.fx.docking;

/** Prepares fresh application content for a stored Dockable identity. */
@FunctionalInterface
public interface DockableResolver {
    DockableResolution resolve(DockableId id);
}
