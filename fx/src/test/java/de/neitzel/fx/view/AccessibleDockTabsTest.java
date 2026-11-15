package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Observes the public accessibility attributes and visible presentation of actual tab headers. */
class AccessibleDockTabsTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @Test
    void anIconOnlyTabKeepsItsAccessibleNameWhenBoundRenamedFloatedAndReturned() throws Exception {
        FxThread.onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 760, 480));
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            Label icon = new Label("▦");
            Dockable project = new Dockable(new DockableId("project"), new TextArea("Project files"));
            project.setIcon(icon);
            SimpleStringProperty name = new SimpleStringProperty("Project files");
            project.accessibleNameProperty().bind(name);
            manager.open(project, DockTarget.area("editors"));
            main.show();
            try {
                assertPresentation(project, icon, "Project files", true);
                name.set("Project outline");
                assertPresentation(project, icon, "Project outline", true);
                manager.move(project.getId(), DockTarget.floating());
                assertPresentation(project, icon, "Project outline", true);
                manager.move(project.getId(), DockTarget.returnToMain());
                assertPresentation(project, icon, "Project outline", true);
                assertEquals("", project.getTitle());
                project.setTitle("Visible title");
                assertPresentation(project, icon, "Visible title", false);
                assertEquals("Project outline", project.getAccessibleName());
            } finally {
                for (Window window : List.copyOf(Window.getWindows())) if (!existing.contains(window)) window.hide();
            }
        });
    }

    @Test void theCloseButtonsAccessibleActionNameUsesTheCapturedApplicationBundle() throws Exception {
        FxThread.onFx(() -> {
            var existing = List.copyOf(Window.getWindows());
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 760, 480));
            DockingOptions options = new DockingOptions();
            options.setResourceBundle(new java.util.ListResourceBundle() {
                @Override protected Object[][] getContents() {
                    return new Object[][] {{"dock.action.close", "Fermer le document"}};
                }
            });
            DockingManager manager = DockingManager.attach(main, pane, options);
            Dockable notes = new Dockable(new DockableId("notes"), new TextArea("Notes"));
            notes.setTitle("Notes");
            manager.open(notes, DockTarget.area("editors"));
            main.show();
            try {
                for (boolean floating : new boolean[]{false, true}) {
                    if (floating) manager.move(notes.getId(), DockTarget.floating());
                    var root = notes.getContent().getScene().getRoot();
                    root.applyCss(); root.layout();
                    Node close = root.lookup(".dock-tab-header .tab-close-button");
                    assertNotNull(close);
                    assertEquals("Fermer le document", close.queryAccessibleAttribute(AccessibleAttribute.TEXT));
                }
            } finally {
                for (Window window : List.copyOf(Window.getWindows())) if (!existing.contains(window)) window.hide();
            }
        });
    }

    private static void assertPresentation(Dockable dockable, Node icon, String name, boolean iconOnly) {
        var root = dockable.getContent().getScene().getRoot();
        root.applyCss();
        root.layout();
        TabPane tabs = root.lookupAll(".tab-pane").stream().map(TabPane.class::cast)
                .filter(candidate -> candidate.getTabs().stream().anyMatch(tab -> tab.getContent() == dockable.getContent()))
                .findFirst().orElseThrow();
        Tab tab = tabs.getTabs().stream().filter(candidate -> candidate.getContent() == dockable.getContent())
                .findFirst().orElseThrow();
        Node header = tabs.lookup("#" + tab.getId());
        assertEquals(name, header.queryAccessibleAttribute(AccessibleAttribute.TEXT));
        assertSame(icon, tab.getGraphic());
        Labeled label = (Labeled) header.lookup(".tab-label");
        assertEquals(iconOnly ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT, label.getContentDisplay());
        assertTrue(header.getStyleClass().contains("dock-tab-header"));
    }
}
