package de.neitzel.fx.docking;

/** Resolving stored identity distinguishes available content, absence and a technical load failure. */
public sealed interface DockableResolution {
    record Available(Dockable dockable) implements DockableResolution { }
    record Missing() implements DockableResolution { }
    record Failed(Throwable cause) implements DockableResolution { }
}
