package de.neitzel.fx.docking;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Checks the complete data graph before any application resolver or live layout is touched. */
final class DockSnapshotValidation {
    private DockSnapshotValidation() { }

    static DockFailure validate(LayoutSnapshot snapshot, Consumer<LayoutSnapshot.Group> validateSettings) {
        if (snapshot.version() != LayoutSnapshot.CURRENT_VERSION) {
            return new DockFailure(DockFailure.Code.UNSUPPORTED_SNAPSHOT_VERSION, null,
                    "Unsupported layout snapshot version: " + snapshot.version(), null);
        }
        try {
            Set<DockWindowId> windows = new HashSet<>();
            Set<DockAreaId> aliases = new HashSet<>();
            Set<DockGroupId> groups = new HashSet<>();
            Set<DockableId> contents = new HashSet<>();
            Set<DockableId> selected = new HashSet<>();
            int mainWindows = 0;
            for (LayoutSnapshot.Window window : snapshot.windows()) {
                require(windows.add(Objects.requireNonNull(window.id())), "Duplicate window identity");
                if (window.main()) {
                    mainWindows++;
                    require(window.id().equals(new DockWindowId("main")) && window.bounds() == null,
                            "The main window uses its canonical identity and application-owned bounds");
                } else {
                    var bounds = Objects.requireNonNull(window.bounds(), "Floating bounds are required");
                    require(Double.isFinite(bounds.getMinX()) && Double.isFinite(bounds.getMinY())
                            && positive(bounds.getWidth()) && positive(bounds.getHeight()), "Invalid floating bounds");
                }
                Set<LayoutSnapshot.Placement> placements = new HashSet<>();
                for (LayoutSnapshot.Root root : window.roots()) {
                    require(placements.add(Objects.requireNonNull(root.placement())), "Duplicate root placement");
                    require(extent(root.preferredWidth()) && extent(root.preferredHeight()), "Invalid root extent");
                    require(!root.branch().aliases().isEmpty(), "A root requires a stable area alias");
                    validate(root.branch(), aliases, groups, contents, selected, validateSettings);
                }
            }
            require(mainWindows == 1 && snapshot.windows().getFirst().main(), "Exactly one first main window is required");
            require(snapshot.activeDockableId() == null || selected.contains(snapshot.activeDockableId()),
                    "Active Dockable must be a selected stored Dockable");
            snapshot.initialIntents().forEach((id, area) -> {
                require(contents.contains(id), "Initial intent refers to an unknown Dockable");
                Objects.requireNonNull(area);
            });
            snapshot.returnPositions().forEach((id, position) -> {
                require(contents.contains(id), "Return position refers to an unknown Dockable");
                require(position.target() instanceof DockTarget.Area || position.target() instanceof DockTarget.Group,
                        "A return position must identify an area or group");
                Objects.requireNonNull(position.areaId(), "Return area is required");
            });
            return null;
        } catch (RuntimeException invalid) {
            return new DockFailure(DockFailure.Code.INVALID_SNAPSHOT, null,
                    "Invalid layout snapshot: " + invalid.getMessage(), invalid);
        }
    }

    private static void validate(LayoutSnapshot.Branch branch, Set<DockAreaId> aliases, Set<DockGroupId> groups,
                                 Set<DockableId> contents, Set<DockableId> selected,
                                 Consumer<LayoutSnapshot.Group> validateSettings) {
        for (DockAreaId alias : branch.aliases()) require(aliases.add(alias), "Duplicate area alias");
        if (branch instanceof LayoutSnapshot.Split split) {
            Objects.requireNonNull(split.orientation(), "Split orientation is required");
            require(Double.isFinite(split.position()) && split.position() > 0 && split.position() < 1,
                    "Split proportion must lie between zero and one");
            validate(split.first(), aliases, groups, contents, selected, validateSettings);
            validate(split.second(), aliases, groups, contents, selected, validateSettings);
        } else {
            LayoutSnapshot.Group group = (LayoutSnapshot.Group) branch;
            require(groups.add(Objects.requireNonNull(group.id())), "Duplicate group identity");
            Objects.requireNonNull(group.originArea(), "Group origin is required");
            validateSettings.accept(group);
            require(!group.collapsed() || group.settings().collapsible(), "A noncollapsible group cannot be collapsed");
            require(positive(group.expandedWidth()) && positive(group.expandedHeight()), "Invalid remembered group size");
            for (DockableId id : group.tabs()) require(contents.add(id), "Duplicate Dockable identity");
            require(group.selected() == null || group.tabs().contains(group.selected()), "Selection refers to a missing tab");
            if (group.selected() != null) selected.add(group.selected());
        }
    }

    private static boolean positive(double value) { return Double.isFinite(value) && value > 0; }
    private static boolean extent(double value) { return value == -1 || positive(value); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
