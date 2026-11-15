package de.neitzel.fx.docking;

/** Machine-readable rejection with optional affected identity and technical cause. */
public record DockFailure(Code code, DockableId dockableId, String message, Throwable cause, String messageKey) {
    /** Preserves the direct-message form for failures without an application resource key. */
    public DockFailure(Code code, DockableId dockableId, String message, Throwable cause) {
        this(code, dockableId, message, cause, null);
    }

    public enum Code { LAYOUT_CHANGE_IN_PROGRESS, RESTORE_REQUIRES_EMPTY_MANAGER, UNSUPPORTED_SNAPSHOT_VERSION, INVALID_SNAPSHOT, UNSUPPORTED_SNAPSHOT_FEATURE, DOCKABLE_LOAD_FAILED, MISSING_DOCKABLE, INVALID_GROUP_CONFIGURATION, COLLAPSE_DISABLED, SPLIT_DISABLED, SPLIT_TOO_SMALL, TARGET_NOT_FOUND, REFERENCE_TAB_NOT_FOUND, DOCKABLE_NOT_FOUND, CONTENT_ALREADY_ATTACHED,
        PLACEMENT_REJECTED, PLACEMENT_CHECK_FAILED, ID_MISMATCH, FACTORY_FAILED, MAIN_WINDOW_ONLY,
        CLOSE_IN_PROGRESS, CLOSE_CHECK_FAILED, CLEANUP_FAILED, INVALID_WINDOW_BOUNDS, WINDOW_CREATION_FAILED }
}
