package de.neitzel.fx.docking;

import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.scene.control.Tab;

/** Keeps icon-only accessibility text separate from the application's visible title. */
final class DockTabPresentation {
    private final Tab tab;
    private final Dockable dockable;
    private final String closeName;
    private final StringBinding text;
    private final InvalidationListener appearanceChanged = observable -> refreshClass();

    private DockTabPresentation(Tab tab, Dockable dockable, String closeName) {
        this.tab = tab;
        this.dockable = dockable;
        this.closeName = closeName;
        text = Bindings.createStringBinding(this::headerText,
                dockable.titleProperty(), dockable.iconProperty(), dockable.accessibleNameProperty());
        tab.textProperty().bind(text);
        tab.getStyleClass().add("dock-tab-header");
        refreshClass();
        dockable.titleProperty().addListener(appearanceChanged);
        dockable.iconProperty().addListener(appearanceChanged);
    }

    static void bind(Tab tab, Dockable dockable, String closeName) {
        tab.getProperties().put(DockTabPresentation.class, new DockTabPresentation(tab, dockable, closeName));
    }

    static void updateHeader(Tab tab, javafx.scene.Node header) {
        if (header == null) return;
        if (tab.getProperties().get(DockTabPresentation.class) instanceof DockTabPresentation presentation) {
            javafx.scene.Node close = header.lookup(".tab-close-button");
            if (close != null) close.setAccessibleText(presentation.closeName);
        }
    }

    static void unbind(Tab tab) {
        if (tab.getProperties().remove(DockTabPresentation.class) instanceof DockTabPresentation presentation) {
            presentation.dockable.titleProperty().removeListener(presentation.appearanceChanged);
            presentation.dockable.iconProperty().removeListener(presentation.appearanceChanged);
            tab.textProperty().unbind();
            presentation.text.dispose();
        }
    }

    private boolean iconOnly() {
        return dockable.getIcon() != null && (dockable.getTitle() == null || dockable.getTitle().isBlank());
    }

    private String headerText() {
        if (!iconOnly()) return dockable.getTitle();
        String name = dockable.getAccessibleName();
        return name == null || name.isBlank() ? dockable.getTitle() : name;
    }

    private void refreshClass() {
        if (iconOnly()) {
            if (!tab.getStyleClass().contains("dock-icon-only")) tab.getStyleClass().add("dock-icon-only");
        } else tab.getStyleClass().remove("dock-icon-only");
    }
}
