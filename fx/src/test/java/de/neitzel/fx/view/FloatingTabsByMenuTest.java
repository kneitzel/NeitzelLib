package de.neitzel.fx.view;

import de.neitzel.fx.docking.DockGroupDefinition;
import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Floating commands are exercised through visible tab menus and public layout state. */
class FloatingTabsByMenuTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @Test
    void tabMenusFloatAndReturnTheSameEditorWithoutLosingItsSelection() throws Exception {
        FxThread.onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 760, 420));
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            TextArea content = new TextArea("Unsaved notes survive a transfer");
            Dockable notes = new Dockable(new DockableId("notes"), content);
            notes.setTitle("Notes");
            AtomicInteger closed = new AtomicInteger();
            notes.setOnClosed(closed::incrementAndGet);
            manager.open(notes, DockTarget.area("editors"));
            main.show();
            ContextMenu menu = null;
            try {
                pane.applyCss();
                pane.layout();
                content.selectRange(8, 13);
                menu = tab(content).getContextMenu();
                menu.show(main, main.getX() + 80, main.getY() + 80);
                MenuItem floatItem = item(menu, "Float");
                assertFalse(floatItem.isDisable());
                assertEquals(1, manager.layout().windows().size(), "Opening a menu must not create a window");
                menu.hide();
                floatItem.fire();

                assertEquals(2, manager.layout().windows().size());
                Window floating = content.getScene().getWindow();
                assertNotSame(main, floating);
                assertTrue(floating.isShowing());
                assertSame(content, manager.findDockable(notes.getId()).orElseThrow().getContent());
                assertEquals("Unsaved notes survive a transfer", content.getText());
                assertEquals("notes", content.getSelectedText());
                assertSame(content, content.getScene().getFocusOwner());

                menu = tab(content).getContextMenu();
                menu.show(floating, floating.getX() + 80, floating.getY() + 80);
                MenuItem returnItem = item(menu, "Return to main");
                assertFalse(returnItem.isDisable());
                menu.hide();
                returnItem.fire();

                assertEquals(1, manager.layout().windows().size());
                assertFalse(floating.isShowing());
                assertSame(main, content.getScene().getWindow());
                assertEquals("notes", content.getSelectedText());
                assertSame(content, main.getScene().getFocusOwner());
                assertSame(notes, manager.activeDockable());
                assertEquals(0, closed.get(), "Moving an editor must not close its resources");
            } finally {
                if (menu != null) menu.hide();
                List.copyOf(Window.getWindows()).stream().filter(window -> !existing.contains(window)).forEach(Window::hide);
            }
        });
    }

    @Test
    void theMoveMenuOffersAnotherFloatingWindowsGroup() throws Exception {
        FxThread.onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 760, 420));
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            TextArea notesContent = new TextArea("Unsaved notes");
            TextArea readmeContent = new TextArea("Readme");
            Dockable notes = new Dockable(new DockableId("notes"), notesContent);
            notes.setTitle("Notes");
            Dockable readme = new Dockable(new DockableId("readme"), readmeContent);
            readme.setTitle("Readme");
            manager.open(notes, DockTarget.area("editors"));
            manager.open(readme, DockTarget.area("editors"));
            main.show();
            ContextMenu menu = null;
            try {
                manager.move(notes.getId(), DockTarget.floating());
                manager.move(readme.getId(), DockTarget.floating());
                Window source = notesContent.getScene().getWindow();
                Window destination = readmeContent.getScene().getWindow();
                notesContent.selectRange(8, 13);
                menu = tab(notesContent).getContextMenu();
                menu.show(source, source.getX() + 80, source.getY() + 80);
                Menu destinations = (Menu) item(menu, "Move to");
                MenuItem readmeTarget = destinations.getItems().stream()
                        .filter(candidate -> candidate.getText().contains("Readme")).findFirst().orElseThrow();
                assertFalse(readmeTarget.isDisable());
                menu.hide();
                readmeTarget.fire();
                assertFalse(source.isShowing());
                assertEquals(2, manager.layout().windows().size());
                assertSame(destination, notesContent.getScene().getWindow());
                assertEquals("notes", notesContent.getSelectedText());
                assertSame(notesContent, destination.getScene().getFocusOwner());
                assertSame(notes, manager.activeDockable());
            } finally {
                if (menu != null) menu.hide();
                List.copyOf(Window.getWindows()).stream().filter(window -> !existing.contains(window)).forEach(Window::hide);
            }
        });
    }

    @Test
    void aMainWindowOnlyTabExplainsItsDisabledFloatingCommands() throws Exception {
        FxThread.onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 760, 420));
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            TextArea projectContent = new TextArea("Project");
            Dockable project = new Dockable(new DockableId("project"), projectContent);
            project.setTitle("Project");
            project.setMainWindowOnly(true);
            Dockable readme = new Dockable(new DockableId("readme"), new TextArea("Readme"));
            readme.setTitle("Readme");
            manager.open(project, DockTarget.area("editors"));
            manager.open(readme, DockTarget.area("editors"));
            main.show();
            ContextMenu menu = null;
            try {
                manager.move(readme.getId(), DockTarget.floating());
                pane.applyCss();
                pane.layout();
                var before = manager.layout();
                menu = tab(projectContent).getContextMenu();
                menu.show(main, main.getX() + 80, main.getY() + 80);
                MenuItem floatItem = item(menu, "Float");
                assertTrue(floatItem.isDisable());
                assertTrue(floatItem.getText().contains("main window"));
                Menu destinations = (Menu) item(menu, "Move to");
                MenuItem floatingTarget = destinations.getItems().stream()
                        .filter(candidate -> candidate.getText().contains("Readme")).findFirst().orElseThrow();
                assertTrue(floatingTarget.isDisable());
                assertTrue(floatingTarget.getText().contains("main window"));
                assertEquals(before, manager.layout(), "Opening the menu only checks destinations");
                assertSame(main, projectContent.getScene().getWindow());
            } finally {
                if (menu != null) menu.hide();
                List.copyOf(Window.getWindows()).stream().filter(window -> !existing.contains(window)).forEach(Window::hide);
            }
        });
    }

    private static Tab tab(TextArea content) {
        return content.getScene().getRoot().lookupAll(".tab-pane").stream().map(TabPane.class::cast)
                .flatMap(tabs -> tabs.getTabs().stream()).filter(tab -> tab.getContent() == content)
                .findFirst().orElseThrow();
    }

    private static MenuItem item(ContextMenu menu, String label) {
        MenuItem item = menu.getItems().stream().filter(candidate -> candidate.getText().startsWith(label))
                .findFirst().orElse(null);
        assertNotNull(item, "Expected tab command: " + label);
        return item;
    }
}
