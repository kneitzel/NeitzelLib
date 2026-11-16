package de.neitzel.fx.docking;

import javafx.scene.Scene;

import java.util.Objects;

/** Adds scoped defaults before application styles, without replacing the application's theme. */
final class DockStyles {
    private static final String DEFAULTS = Objects.requireNonNull(DockStyles.class.getResource("docking.css")).toExternalForm();

    private DockStyles() { }

    static void install(Scene scene) {
        if (scene != null && !scene.getStylesheets().contains(DEFAULTS)) scene.getStylesheets().addFirst(DEFAULTS);
    }
}
