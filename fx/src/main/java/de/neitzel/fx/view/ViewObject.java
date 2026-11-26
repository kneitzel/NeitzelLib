package de.neitzel.fx.view;

import java.util.Objects;

/**
 * An instance and the action its View invokes once on release. Use an empty action when the
 * instance has no cleanup. The action is responsible for any container-specific destruction.
 */
public record ViewObject<T>(T instance, Runnable release) {

    public ViewObject {
        Objects.requireNonNull(instance, "View object");
        Objects.requireNonNull(release, "View object release action");
    }
}
