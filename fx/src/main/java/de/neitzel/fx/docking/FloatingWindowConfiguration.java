package de.neitzel.fx.docking;

import javafx.scene.image.Image;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Appearance supplied before a managed window appears; its Stage and Scene remain manager-owned. */
public final class FloatingWindowConfiguration {
    @Getter @Setter private String title = "Docking";
    @Getter private final List<Image> icons = new ArrayList<>();
    @Getter private final List<String> stylesheets = new ArrayList<>();
}
