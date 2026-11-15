package de.neitzel.fx.docking;

/** Machine-readable rejection with optional affected identity and technical cause. */
public record DockFailure(Code code, DockableId dockableId, String message, Throwable cause) {
    public enum Code { SPLIT_DISABLED, SPLIT_TOO_SMALL, TARGET_NOT_FOUND, REFERENCE_TAB_NOT_FOUND, DOCKABLE_NOT_FOUND, CONTENT_ALREADY_ATTACHED,
        PLACEMENT_REJECTED, PLACEMENT_CHECK_FAILED, ID_MISMATCH, FACTORY_FAILED,
        CLOSE_IN_PROGRESS, CLOSE_CHECK_FAILED, CLEANUP_FAILED }
}
