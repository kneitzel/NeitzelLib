package de.neitzel.fx.docking;

/** Immutable pre-close state passed after every Dockable has consented. */
public record ShutdownRequest(LayoutSnapshot snapshot, ShutdownReason reason) {
    public ShutdownRequest {
        java.util.Objects.requireNonNull(snapshot, "snapshot");
        java.util.Objects.requireNonNull(reason, "reason");
    }
}
