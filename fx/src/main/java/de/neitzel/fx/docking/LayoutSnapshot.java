package de.neitzel.fx.docking;

import javafx.geometry.Orientation;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Side;
import java.util.List;
import java.util.Map;

/** Versioned immutable layout data; document contents and JavaFX Nodes remain application-owned. */
public record LayoutSnapshot(int version, List<Window> windows, DockableId activeDockableId,
                             Map<DockableId, ReturnPosition> returnPositions,
                             Map<DockableId, DockAreaId> initialIntents) {
    public static final int CURRENT_VERSION = 1;
    public LayoutSnapshot {
        windows = List.copyOf(windows);
        returnPositions = Map.copyOf(returnPositions);
        initialIntents = Map.copyOf(initialIntents);
    }

    static Window remapGroups(Window window, java.util.function.Function<DockGroupId, DockGroupId> remap) {
        return new Window(window.id(), window.main(), window.bounds(), window.roots().stream()
                .map(root -> new Root(root.placement(), root.preferredWidth(), root.preferredHeight(),
                        remapGroups(root.branch(), remap))).toList());
    }

    private static Branch remapGroups(Branch branch, java.util.function.Function<DockGroupId, DockGroupId> remap) {
        if (branch instanceof Split split) return new Split(split.aliases(), split.orientation(), split.position(),
                remapGroups(split.first(), remap), remapGroups(split.second(), remap));
        Group group = (Group) branch;
        return new Group(group.aliases(), remap.apply(group.id()), group.originArea(), group.settings(),
                group.collapsed(), group.expandedWidth(), group.expandedHeight(), group.tabs(), group.selected());
    }

    public enum Placement { CENTER, LEFT, RIGHT, TOP, BOTTOM }
    public record Window(DockWindowId id, boolean main, Rectangle2D bounds, List<Root> roots) {
        public Window { roots = List.copyOf(roots); }
    }
    public record Root(Placement placement, double preferredWidth, double preferredHeight, Branch branch) { }
    public sealed interface Branch {
        List<DockAreaId> aliases();
    }
    public record Split(List<DockAreaId> aliases, Orientation orientation, double position,
                        Branch first, Branch second) implements Branch {
        public Split { aliases = List.copyOf(aliases); }
    }
    public record Group(List<DockAreaId> aliases, DockGroupId id, DockAreaId originArea,
                        GroupSettings settings, boolean collapsed, double expandedWidth, double expandedHeight,
                        List<DockableId> tabs, DockableId selected) implements Branch {
        public Group { aliases = List.copyOf(aliases); tabs = List.copyOf(tabs); }
    }
    public record ReturnPosition(DockTarget target, DockAreaId areaId) { }
    public record GroupSettings(Side tabSide, DockGroupMode mode, double preferredWidth, double preferredHeight,
                                double minimumWidth, double minimumHeight, boolean persistent,
                                boolean visibleWhenEmpty, boolean releaseSpaceWhenEmpty, boolean collapsible,
                                SplitDirections splitDirections) {
        static GroupSettings capture(DockGroupDefinition definition) {
            return new GroupSettings(definition.getTabSide(), definition.getMode(), definition.getPreferredWidth(),
                    definition.getPreferredHeight(), definition.getMinimumWidth(), definition.getMinimumHeight(),
                    definition.isPersistent(), definition.isVisibleWhenEmpty(), definition.isReleaseSpaceWhenEmpty(),
                    definition.isCollapsible(), definition.getSplitDirections());
        }

        DockGroupDefinition definition(DockAreaId origin) {
            DockGroupDefinition definition = new DockGroupDefinition(origin.value());
            DockGroupChanges.builder().tabSide(tabSide).mode(mode).preferredWidth(preferredWidth)
                    .preferredHeight(preferredHeight).minimumWidth(minimumWidth).minimumHeight(minimumHeight)
                    .persistent(persistent).visibleWhenEmpty(visibleWhenEmpty).releaseSpaceWhenEmpty(releaseSpaceWhenEmpty)
                    .collapsible(collapsible).splitDirections(splitDirections).build().applyTo(definition);
            return definition;
        }
    }
}
