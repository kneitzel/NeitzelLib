package de.neitzel.fx.docking;

import javafx.geometry.Side;
import lombok.Builder;
import lombok.Value;

import java.util.Objects;

/** Immutable partial update: null getters mean unspecified, while false is an explicit value. */
@Value @Builder
public class DockGroupChanges {
    Side tabSide;
    DockGroupMode mode;
    Double preferredWidth;
    Double preferredHeight;
    Double minimumWidth;
    Double minimumHeight;
    Boolean persistent;
    Boolean visibleWhenEmpty;
    Boolean releaseSpaceWhenEmpty;
    Boolean collapsible;
    SplitDirections splitDirections;

    /** Builder accepts primitive dimensions/flags and requires non-null enum settings. */
    public static class DockGroupChangesBuilder {
        public DockGroupChangesBuilder mode(DockGroupMode value) { mode = Objects.requireNonNull(value); return this; }
        public DockGroupChangesBuilder tabSide(Side value) { tabSide = Objects.requireNonNull(value); return this; }
        public DockGroupChangesBuilder preferredWidth(double value) { preferredWidth = value; return this; }
        public DockGroupChangesBuilder preferredHeight(double value) { preferredHeight = value; return this; }
        public DockGroupChangesBuilder minimumWidth(double value) { minimumWidth = value; return this; }
        public DockGroupChangesBuilder minimumHeight(double value) { minimumHeight = value; return this; }
        public DockGroupChangesBuilder persistent(boolean value) { persistent = value; return this; }
        public DockGroupChangesBuilder visibleWhenEmpty(boolean value) { visibleWhenEmpty = value; return this; }
        public DockGroupChangesBuilder releaseSpaceWhenEmpty(boolean value) { releaseSpaceWhenEmpty = value; return this; }
        public DockGroupChangesBuilder collapsible(boolean value) { collapsible = value; return this; }
        public DockGroupChangesBuilder splitDirections(SplitDirections value) { splitDirections = Objects.requireNonNull(value); return this; }
    }

    DockGroupChanges resolved(DockGroupDefinition current) {
        return builder()
                .mode(mode == null ? current.getMode() : mode)
                .tabSide(tabSide == null ? current.getTabSide() : tabSide)
                .preferredWidth(preferredWidth == null ? current.getPreferredWidth() : preferredWidth)
                .preferredHeight(preferredHeight == null ? current.getPreferredHeight() : preferredHeight)
                .minimumWidth(minimumWidth == null ? current.getMinimumWidth() : minimumWidth)
                .minimumHeight(minimumHeight == null ? current.getMinimumHeight() : minimumHeight)
                .persistent(persistent == null ? current.isPersistent() : persistent)
                .visibleWhenEmpty(visibleWhenEmpty == null ? current.isVisibleWhenEmpty() : visibleWhenEmpty)
                .releaseSpaceWhenEmpty(releaseSpaceWhenEmpty == null ? current.isReleaseSpaceWhenEmpty() : releaseSpaceWhenEmpty)
                .collapsible(collapsible == null ? current.isCollapsible() : collapsible)
                .splitDirections(splitDirections == null ? current.getSplitDirections() : splitDirections)
                .build();
    }

    void applyTo(DockGroupDefinition target) {
        if (mode != null) target.setMode(mode);
        if (tabSide != null) target.setTabSide(tabSide);
        if (preferredWidth != null) target.setPreferredWidth(preferredWidth);
        if (preferredHeight != null) target.setPreferredHeight(preferredHeight);
        if (minimumWidth != null) target.setMinimumWidth(minimumWidth);
        if (minimumHeight != null) target.setMinimumHeight(minimumHeight);
        if (persistent != null) target.setPersistent(persistent);
        if (visibleWhenEmpty != null) target.setVisibleWhenEmpty(visibleWhenEmpty);
        if (releaseSpaceWhenEmpty != null) target.setReleaseSpaceWhenEmpty(releaseSpaceWhenEmpty);
        if (collapsible != null) target.setCollapsible(collapsible);
        if (splitDirections != null) target.setSplitDirections(splitDirections);
    }
}
