package de.neitzel.fx.docking;

import lombok.Getter;
import lombok.Setter;
import java.util.Optional;

/** Attachment options. Additional policies are introduced with their operations. */
@Getter @Setter
public final class DockingOptions {
    /** String overrides for built-in labels, failure codes and application message keys; captured at attach. */
    private java.util.ResourceBundle resourceBundle;
    private PlacementRule placementRule = (dockable, target) -> Optional.empty();
    private DockTarget fallbackTarget;
    private java.util.function.Consumer<FloatingWindowConfiguration> floatingWindowCustomizer = configuration -> { };
}
