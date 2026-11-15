package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Keyboard events enter through focused public controls and invoke the visible menu. */
class KeyboardDockActionsTest {
    @BeforeAll static void startJavaFx() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @Test void shiftF10OnTheSelectedTabOpensItsMenuAndFloatRestoresTheEditorFocus() throws Exception {
        FxThread.onFx(() -> {
            var existing = List.copyOf(Window.getWindows());
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 760, 460));
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            TextArea editor = new TextArea("abcdef");
            Dockable notes = new Dockable(new DockableId("notes"), editor);
            notes.setTitle("Notes");
            manager.open(notes, DockTarget.area("editors"));
            main.show();
            try {
                pane.applyCss(); pane.layout();
                editor.selectRange(2, 4);
                TabPane tabs = (TabPane) pane.lookup(".dock-group");
                tabs.requestFocus();
                ContextMenu menu = tabs.getSelectionModel().getSelectedItem().getContextMenu();
                key(tabs, KeyCode.F10, true);
                assertTrue(menu.isShowing(), "Shift+F10 opens the selected docking tab's menu");
                for (int index = 0; index < 4; index++) key(menu.getSkin().getNode(), KeyCode.DOWN, false);
                key(menu.getSkin().getNode(), KeyCode.ENTER, false);
                assertNotSame(main, editor.getScene().getWindow());
                assertSame(editor, editor.getScene().getFocusOwner());
                assertEquals("cd", editor.getSelectedText());
                assertSame(notes, manager.activeDockable());
            } finally {
                for (Window window : List.copyOf(Window.getWindows())) if (!existing.contains(window)) window.hide();
            }
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"Pin group", "Close"})
    void commandsThatKeepContentOpenRestoreItsFocus(String action) throws Exception {
        FxThread.onFx(() -> {
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            DockGroupDefinition tools = new DockGroupDefinition("tools");
            tools.setCollapsible(true);
            pane.getInitialLayout().setRight(tools);
            Stage main = new Stage();
            main.setScene(new Scene(pane, 760, 460));
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            TextArea editor = new TextArea("Unsaved changes");
            Dockable notes = new Dockable(new DockableId("notes"), editor);
            notes.setTitle("Notes");
            java.util.concurrent.atomic.AtomicInteger checks = new java.util.concurrent.atomic.AtomicInteger();
            notes.setCloseCheck(request -> {
                checks.incrementAndGet();
                return java.util.concurrent.CompletableFuture.completedFuture(CloseConsent.VETO);
            });
            manager.open(notes, DockTarget.area("tools"));
            main.show();
            try {
                pane.applyCss(); pane.layout();
                TabPane tabs = (TabPane) pane.lookupAll(".dock-group").stream()
                        .filter(node -> ((TabPane) node).getTabs().stream().anyMatch(tab -> tab.getContent() == editor))
                        .findFirst().orElseThrow();
                tabs.requestFocus();
                ContextMenu menu = tabs.getSelectionModel().getSelectedItem().getContextMenu();
                key(tabs, KeyCode.CONTEXT_MENU, false);
                assertTrue(menu.isShowing());
                int count = action.equals("Pin group") ? 6 : 3;
                for (int index = 0; index < count; index++) key(menu.getSkin().getNode(), KeyCode.DOWN, false);
                key(menu.getSkin().getNode(), KeyCode.ENTER, false);
                assertSame(notes, manager.findDockable(notes.getId()).orElseThrow());
                if (action.equals("Pin group")) assertEquals(DockGroupMode.OVERLAY,
                        manager.layout().windows().getFirst().areas().get(1).groups().getFirst().mode());
                else assertEquals(1, checks.get(), "Close uses the application's ordinary consent callback");
                assertSame(editor, editor.getScene().getFocusOwner(), "The surviving editor can receive typing");
            } finally { main.hide(); }
        });
    }

    @Test void aLateCloseVetoDoesNotTakeFocusBackFromAnotherActivatedEditor() throws Exception {
        FxThread.onFx(() -> {
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            pane.getInitialLayout().setRight(new DockGroupDefinition("tools"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 760, 460));
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            Dockable notes = new Dockable(new DockableId("notes"), new TextArea("Notes"));
            notes.setTitle("Notes");
            Dockable readme = new Dockable(new DockableId("readme"), new TextArea("Readme"));
            readme.setTitle("Readme");
            var consent = new java.util.concurrent.CompletableFuture<CloseConsent>();
            notes.setCloseCheck(request -> consent);
            manager.open(readme, DockTarget.area("tools"));
            manager.open(notes, DockTarget.area("editors"));
            main.show();
            try {
                pane.applyCss(); pane.layout();
                TabPane tabs = pane.lookupAll(".dock-group").stream().map(TabPane.class::cast)
                        .filter(candidate -> candidate.getTabs().stream().anyMatch(tab -> tab.getContent() == notes.getContent()))
                        .findFirst().orElseThrow();
                tabs.requestFocus();
                ContextMenu menu = tabs.getSelectionModel().getSelectedItem().getContextMenu();
                key(tabs, KeyCode.F10, true);
                for (int index = 0; index < 3; index++) key(menu.getSkin().getNode(), KeyCode.DOWN, false);
                key(menu.getSkin().getNode(), KeyCode.ENTER, false);
                assertNotNull(manager.closingScopeProperty().get());
                manager.activate(readme.getId());
                assertSame(readme.getContent(), main.getScene().getFocusOwner());
                consent.complete(CloseConsent.VETO);
                assertNull(manager.closingScopeProperty().get());
                assertSame(readme, manager.activeDockable());
                assertSame(readme.getContent(), main.getScene().getFocusOwner(),
                        "Completion must respect the user's intervening focus choice");
                assertTrue(manager.findDockable(notes.getId()).isPresent());
            } finally { consent.complete(CloseConsent.VETO); main.hide(); }
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"Move", "Split"})
    void submenuNavigationMovesOrSplitsTheSelectedEditor(String action) throws Exception {
        FxThread.onFx(() -> {
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            pane.getInitialLayout().setRight(new DockGroupDefinition("tools"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 1000, 600));
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            TextArea editor = new TextArea("abcdef");
            Dockable notes = new Dockable(new DockableId("notes"), editor);
            notes.setTitle("Notes");
            Dockable readme = new Dockable(new DockableId("readme"), new TextArea("Readme"));
            readme.setTitle("Readme");
            Dockable project = new Dockable(new DockableId("project"), new TextArea("Project"));
            project.setTitle("Project");
            manager.open(readme, DockTarget.area("editors"));
            manager.open(project, DockTarget.area("tools"));
            manager.open(notes, DockTarget.area("editors"));
            main.show();
            try {
                pane.applyCss(); pane.layout();
                editor.selectRange(2, 4);
                TabPane tabs = pane.lookupAll(".dock-group").stream().map(TabPane.class::cast)
                        .filter(candidate -> candidate.getTabs().stream().anyMatch(tab -> tab.getContent() == editor))
                        .findFirst().orElseThrow();
                tabs.requestFocus();
                ContextMenu menu = tabs.getSelectionModel().getSelectedItem().getContextMenu();
                key(tabs, KeyCode.F10, true);
                key(menu.getSkin().getNode(), KeyCode.DOWN, false);
                if (action.equals("Split")) key(menu.getSkin().getNode(), KeyCode.DOWN, false);
                key(menu.getSkin().getNode(), KeyCode.RIGHT, false);
                ContextMenu submenu = Window.getWindows().stream()
                        .filter(window -> window instanceof ContextMenu candidate && candidate != menu && candidate.isShowing())
                        .map(ContextMenu.class::cast).findFirst().orElseThrow();
                if (action.equals("Move")) key(submenu.getSkin().getNode(), KeyCode.DOWN, false);
                key(submenu.getSkin().getNode(), KeyCode.ENTER, false);
                if (action.equals("Move")) {
                    var toolGroup = manager.layout().windows().getFirst().areas().get(1).groups().getFirst();
                    assertEquals(List.of(project.getId(), notes.getId()), toolGroup.dockableIds());
                } else {
                    var editorArea = manager.layout().windows().getFirst().areas().getFirst();
                    assertInstanceOf(DockLayoutView.Split.class, editorArea.root());
                    assertEquals(2, editorArea.groups().size());
                    assertEquals(List.of(notes.getId()), editorArea.groups().getFirst().dockableIds());
                }
                assertSame(notes, manager.activeDockable());
                assertSame(editor, editor.getScene().getFocusOwner());
                assertEquals("cd", editor.getSelectedText());
            } finally {
                for (Window window : List.copyOf(Window.getWindows())) if (window instanceof ContextMenu) window.hide();
                main.hide();
            }
        });
    }

    @Test void contentControlsKeepTheirOwnKeyboardContextMenus() throws Exception {
        FxThread.onFx(() -> {
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 760, 460));
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            TextArea editor = new TextArea("Application editor");
            ContextMenu editorMenu = new ContextMenu(new javafx.scene.control.MenuItem("Inspect editor"));
            editor.setContextMenu(editorMenu);
            java.util.concurrent.atomic.AtomicBoolean receivedShortcut = new java.util.concurrent.atomic.AtomicBoolean();
            editor.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.F10 && event.isShiftDown()) receivedShortcut.set(true);
            });
            Dockable notes = new Dockable(new DockableId("notes"), editor);
            notes.setTitle("Notes");
            manager.open(notes, DockTarget.area("editors"));
            main.show();
            try {
                pane.applyCss(); pane.layout();
                editor.requestFocus();
                ContextMenu tabMenu = ((TabPane) pane.lookup(".dock-group"))
                        .getSelectionModel().getSelectedItem().getContextMenu();
                key(editor, KeyCode.F10, true);
                assertTrue(receivedShortcut.get(), "Docking does not consume content keyboard shortcuts");
                assertFalse(tabMenu.isShowing());
                var bounds = editor.localToScreen(editor.getBoundsInLocal());
                editor.fireEvent(new javafx.scene.input.ContextMenuEvent(
                        javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED, 4, 4,
                        bounds.getMinX() + 4, bounds.getMinY() + 4, true,
                        new javafx.scene.input.PickResult(editor, 4, 4)));
                assertTrue(editorMenu.isShowing());
                assertFalse(tabMenu.isShowing());
                assertEquals("Inspect editor", editorMenu.getItems().getFirst().getText());
            } finally { editorMenu.hide(); main.hide(); }
        });
    }

    private static void key(Node target, KeyCode code, boolean shift) {
        target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, false, false, false));
    }
}
