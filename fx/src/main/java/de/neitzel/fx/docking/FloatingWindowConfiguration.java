package de.neitzel.fx.docking;

import javafx.scene.control.MenuBar;
import javafx.scene.image.Image;
import javafx.stage.Window;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Appearance supplied before a managed window appears; its Stage and Scene remain manager-owned. */
public final class FloatingWindowConfiguration {
    /** Optional factory for a fresh menu bar. The window provides context for actions and dialogs. */
    @Getter @Setter private Function<Window, MenuBar> menuBarFactory;
    @Getter @Setter private String title = "Docking";
    @Getter private final List<Image> icons = new ArrayList<>();
    @Getter private final List<String> stylesheets = new ArrayList<>();
}
