package de.neitzel.fx.docking;

import javafx.stage.Window;

import java.util.Objects;

/** Consent context, including the actual hosting window to use as dialog owner. */
public record CloseRequest(CloseScope scope, Window owner) {
    public CloseRequest {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(owner, "owner");
    }
}
