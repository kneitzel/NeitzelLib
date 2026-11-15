package de.neitzel.fx.docking;

import lombok.Getter;
import lombok.Setter;
import java.util.Optional;

/** Attachment options. Additional policies are introduced with their operations. */
@Getter @Setter
public final class DockingOptions {
    private PlacementRule placementRule = (dockable, target) -> Optional.empty();
    private DockTarget fallbackTarget;
    private java.util.function.Consumer<FloatingWindowConfiguration> floatingWindowCustomizer = configuration -> { };
}
