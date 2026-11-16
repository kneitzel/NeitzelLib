package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Overlay presentation is observed through the public manager and live JavaFX content. */
class OverlayDockGroupsTest {
    @BeforeAll
    static void startJavaFx() throws InterruptedException { FxThread.start(); }

    @Test
    void unpinningKeepsSharedContentSizeWhileCoveringInsteadOfReservingEditorSpace() throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            DockGroupDefinition tools = new DockGroupDefinition("tools");
            tools.setTabSide(Side.RIGHT);
            tools.setCollapsible(true);
            tools.setPreferredWidth(240);
            initial.setRight(tools);
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 800, 500));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            TextArea editor = new TextArea("Editor");
            manager.open(new Dockable(new DockableId("editor"), editor), DockTarget.area("editors"));
            Dockable project = new Dockable(new DockableId("project"), new TextArea("Project"));
            project.setTitle("Project");
            Dockable changes = new Dockable(new DockableId("changes"), new TextArea("Changed files"));
            changes.setTitle("Changed files");
            manager.open(project, DockTarget.area("tools"));
            manager.open(changes, DockTarget.area("tools"));
            TabPane tabs = tabs(pane, changes);
            double width = tabs.getWidth();
            double editorWidth = editor.getWidth();
            var publications = new java.util.ArrayList<DockLayoutView>();
            manager.layoutProperty().addListener((observable, old, current) -> publications.add(current));
            assertInstanceOf(ActionResult.Applied.class, manager.updateGroup(new DockGroupId("tools"),
                    DockGroupChanges.builder().mode(DockGroupMode.OVERLAY).build()));
            assertEquals(1, publications.size());
            assertEquals(DockGroupMode.OVERLAY, group(manager).mode());
            assertTrue(editor.getWidth() > editorWidth + 150, "Only the tool header rail reserves layout space");
            assertEquals(width, tabs.getWidth(), 1);
            assertTrue(editor.localToScene(editor.getLayoutBounds())
                    .intersects(changes.getContent().localToScene(changes.getContent().getLayoutBounds())));
            manager.activate(project.getId());
            assertEquals(width, tabs.getWidth(), 1);
            assertEquals(DockGroupMode.OVERLAY, group(manager).mode());
            assertSame(project.getContent(), tabs.getSelectionModel().getSelectedItem().getContent());
            assertInstanceOf(ActionResult.Applied.class, manager.updateGroup(new DockGroupId("tools"),
                    DockGroupChanges.builder().mode(DockGroupMode.DOCKED).build()));
            assertEquals(editorWidth, editor.getWidth(), 1);
            assertSame(tabs, tabs(pane, project));
        });
    }

    @Test
    void fxmlOverlayGroupsOpenOneAtATimeWithTheirRememberedSize() throws Exception {
        FxThread.onFx(() -> {
            String fxml = """
                    <?import de.neitzel.fx.docking.*?>
                    <DockPane xmlns:fx="http://javafx.com/fxml/1">
                      <initialLayout><DockLayout>
                        <center><DockGroupDefinition destinationId="editors"/></center>
                        <left><DockGroupDefinition destinationId="project" tabSide="LEFT" mode="OVERLAY"
                            collapsible="true" preferredWidth="230"/></left>
                        <right><DockGroupDefinition destinationId="changes" tabSide="RIGHT" mode="OVERLAY"
                            collapsible="true" preferredWidth="260"/></right>
                      </DockLayout></initialLayout>
                    </DockPane>
                    """;
            DockPane pane = new javafx.fxml.FXMLLoader().load(new java.io.ByteArrayInputStream(
                    fxml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 900, 500));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            assertTrue(group(manager, "project").collapsed());
            assertTrue(group(manager, "changes").collapsed());
            Dockable editor = new Dockable(new DockableId("editor"), new TextArea("Editor"));
            manager.open(editor, DockTarget.area("editors"));
            Dockable project = new Dockable(new DockableId("project"), new TextArea("Project"));
            Dockable changes = new Dockable(new DockableId("changes"), new TextArea("Changes"));
            manager.open(project, DockTarget.area("project"));
            assertFalse(group(manager, "project").collapsed());
            assertEquals(230, tabs(pane, project).getWidth(), 1);
            var events = new java.util.ArrayList<DockLayoutView>();
            manager.layoutProperty().addListener((observable, old, current) -> events.add(current));
            manager.open(changes, DockTarget.area("changes"));
            assertEquals(1, events.size());
            assertTrue(group(manager, "project").collapsed());
            assertFalse(group(manager, "changes").collapsed());
            assertFalse(group(manager, "editors").collapsed());
            assertEquals(260, tabs(pane, changes).getWidth(), 1);
            manager.activate(project.getId());
            assertFalse(group(manager, "project").collapsed());
            assertTrue(group(manager, "changes").collapsed());
            assertEquals(230, tabs(pane, project).getWidth(), 1);
        });
    }

    @Test
    void escapeReturnsToTheHeaderWhileAnOutsidePressKeepsTheClickedTargetFocus() throws Exception {
        FxThread.onFx(() -> {
            Fixture fixture = fixture();
            DockingManager manager = fixture.manager();
            manager.activate(fixture.project().getId());
            fixture.project().getContent().fireEvent(new javafx.scene.input.KeyEvent(
                    javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.ESCAPE,
                    false, false, false, false));
            assertTrue(group(manager, "project").collapsed());
            assertTrue(fixture.pane().getScene().getFocusOwner().getStyleClass().contains("tab"));
            manager.activate(fixture.project().getId());
            press(fixture.outside());
            assertTrue(group(manager, "project").collapsed(), "An outside control dismisses the overlay");
            assertSame(fixture.outside(), fixture.pane().getScene().getFocusOwner());
            assertEquals(DockGroupMode.OVERLAY, group(manager, "project").mode());
        });
    }

    @Test
    void theHeaderContextMenuPinsAndUnpinsTheWholeGroupUsingOnlyKeyboardEvents() throws Exception {
        FxThread.onFx(() -> {
            javafx.application.Platform.setImplicitExit(false);
            Fixture fixture = fixture();
            Stage stage = (Stage) fixture.pane().getScene().getWindow();
            javafx.scene.control.ContextMenu menu = null;
            try {
                stage.show();
                fixture.manager().activate(fixture.project().getId());
                TabPane tabs = tabs(fixture.pane(), fixture.project());
                var tab = tabs.getSelectionModel().getSelectedItem();
                javafx.scene.Node header = tabs.lookup("#" + tab.getId());
                menu = tab.getContextMenu();
                assertTrue(menu.getItems().stream().anyMatch(item -> item.getText().equals("Pin group")),
                        "The selected tab exposes a group pin command");
                double width = tabs.getWidth();
                for (DockGroupMode expected : new DockGroupMode[]{DockGroupMode.DOCKED, DockGroupMode.OVERLAY}) {
                    header.requestFocus();
                    javafx.geometry.Bounds bounds = header.localToScreen(header.getLayoutBounds());
                    header.fireEvent(new javafx.scene.input.ContextMenuEvent(
                            javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                            2, 2, bounds.getMinX() + 2, bounds.getMinY() + 2, true,
                            new javafx.scene.input.PickResult(header, 2, 2)));
                    assertTrue(menu.isShowing(), "A keyboard context-menu request reaches the tab header");
                    javafx.scene.Node popup = menu.getSkin().getNode();
                    popup.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                            "", "", javafx.scene.input.KeyCode.UP, false, false, false, false));
                    popup.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                            "", "", javafx.scene.input.KeyCode.ENTER, false, false, false, false));
                    assertEquals(expected, group(fixture.manager(), "project").mode());
                    assertEquals(width, tabs.getWidth(), 1);
                }
            } finally {
                if (menu != null) menu.hide();
                stage.hide();
            }
        });
    }

    @Test
    void pendingConsentProtectsTheCurrentOverlayWithoutPartiallyRevealingAnother() throws Exception {
        FxThread.onFx(() -> {
            Fixture fixture = fixture();
            DockingManager manager = fixture.manager();
            manager.activate(fixture.project().getId());
            var consent = new java.util.concurrent.CompletableFuture<CloseConsent>();
            fixture.project().setCloseCheck(request -> consent);
            manager.requestTabClose(fixture.project().getId());
            try {
                DockLayoutView before = manager.layout();
                ActionResult.Rejected rejected = assertInstanceOf(ActionResult.Rejected.class,
                        manager.activate(fixture.changes().getId()));
                assertEquals(DockFailure.Code.CLOSE_IN_PROGRESS, rejected.failure().code());
                assertEquals(before, manager.layout());
                Dockable newcomer = new Dockable(new DockableId("new"), new TextArea("New"));
                assertInstanceOf(ActionResult.Rejected.class, manager.open(newcomer, DockTarget.area("changes")));
                assertNull(newcomer.getContent().getParent());
                assertEquals(before, manager.layout());
                press(fixture.outside());
                assertEquals(before, manager.layout());
            } finally {
                consent.complete(CloseConsent.VETO);
            }
            assertInstanceOf(ActionResult.Applied.class, manager.activate(fixture.changes().getId()));
            assertTrue(group(manager, "project").collapsed());
            assertFalse(group(manager, "changes").collapsed());
            DockLayoutView before = manager.layout();
            assertInstanceOf(ActionResult.Rejected.class, manager.updateGroup(new DockGroupId("changes"),
                    DockGroupChanges.builder().collapsible(false).build()));
            assertEquals(before, manager.layout(), "Overlay plus collapse disabled is an invalid complete configuration");
            assertInstanceOf(ActionResult.Applied.class, manager.updateGroup(new DockGroupId("changes"),
                    DockGroupChanges.builder().mode(DockGroupMode.DOCKED).collapsible(false).build()));
        });
    }

    @Test
    void programmaticOpeningIsPerWindowButARealPressInAnotherManagedSceneDismissesTheOverlay() throws Exception {
        FxThread.onFx(() -> {
            javafx.application.Platform.setImplicitExit(false);
            Fixture fixture = fixture();
            DockingManager manager = fixture.manager();
            javafx.stage.Window floating = null;
            try {
                Dockable editor = manager.findDockable(new DockableId("editor")).orElseThrow();
                manager.move(editor.getId(), DockTarget.floating());
                floating = editor.getContent().getScene().getWindow();
                DockLayoutView.Group floatingGroup = manager.layout().windows().get(1).areas().getFirst().groups().getFirst();
                manager.updateGroup(floatingGroup.id(), DockGroupChanges.builder()
                        .collapsible(true).mode(DockGroupMode.OVERLAY).build());
                manager.activate(fixture.project().getId());
                assertFalse(group(manager, "project").collapsed());
                assertFalse(manager.layout().windows().get(1).areas().getFirst().groups().getFirst().collapsed(),
                        "Programmatic activation leaves another window's overlay open");
                press(editor.getContent());
                assertTrue(group(manager, "project").collapsed(), "A press outside the main Scene overlay dismisses it");
                assertFalse(manager.layout().windows().get(1).areas().getFirst().groups().getFirst().collapsed());
            } finally {
                if (floating != null) floating.hide();
            }
        });
    }

    @Test
    void unpinningAnotherOpenGroupMovesActiveFocusOutOfTheOverlayItReplaces() throws Exception {
        FxThread.onFx(() -> {
            Fixture fixture = fixture();
            DockingManager manager = fixture.manager();
            manager.updateGroup(new DockGroupId("changes"), DockGroupChanges.builder().mode(DockGroupMode.DOCKED).build());
            manager.activate(fixture.project().getId());
            assertSame(fixture.project(), manager.activeDockable());
            var events = new java.util.ArrayList<DockLayoutView>();
            manager.layoutProperty().addListener((observable, old, current) -> events.add(current));
            manager.updateGroup(new DockGroupId("changes"), DockGroupChanges.builder().mode(DockGroupMode.OVERLAY).build());
            assertEquals(1, events.size());
            assertTrue(group(manager, "project").collapsed());
            assertFalse(group(manager, "changes").collapsed());
            assertSame(fixture.changes(), manager.activeDockable(), "Active content cannot remain in the replaced closed overlay");
            assertSame(fixture.changes().getContent(), fixture.pane().getScene().getFocusOwner());
        });
    }

    private record Fixture(DockPane pane, DockingManager manager, Dockable project, Dockable changes,
                           javafx.scene.control.Button outside) { }

    private static Fixture fixture() {
        DockLayout layout = new DockLayout();
        layout.setCenter(new DockGroupDefinition("editors"));
        DockGroupDefinition project = new DockGroupDefinition("project");
        project.setTabSide(Side.LEFT);
        project.setCollapsible(true);
        project.setMode(DockGroupMode.OVERLAY);
        project.setPreferredWidth(230);
        layout.setLeft(project);
        DockGroupDefinition changes = new DockGroupDefinition("changes");
        changes.setTabSide(Side.RIGHT);
        changes.setCollapsible(true);
        changes.setMode(DockGroupMode.OVERLAY);
        changes.setPreferredWidth(260);
        layout.setRight(changes);
        DockPane pane = new DockPane();
        pane.setInitialLayout(layout);
        javafx.scene.control.Button outside = new javafx.scene.control.Button("Outside action");
        javafx.scene.layout.BorderPane root = new javafx.scene.layout.BorderPane(pane);
        root.setTop(outside);
        Stage stage = new Stage();
        stage.setScene(new Scene(root, 900, 500));
        DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
        manager.open(new Dockable(new DockableId("editor"), new TextArea("Editor")), DockTarget.area("editors"));
        Dockable projectTab = new Dockable(new DockableId("project"), new TextArea("Project"));
        Dockable changesTab = new Dockable(new DockableId("changes"), new TextArea("Changes"));
        manager.open(projectTab, DockTarget.area("project"));
        manager.open(changesTab, DockTarget.area("changes"));
        return new Fixture(pane, manager, projectTab, changesTab, outside);
    }

    private static void press(javafx.scene.Node target) {
        target.fireEvent(new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_PRESSED,
                0, 0, 0, 0, javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false,
                true, false, false, false, false, true, new javafx.scene.input.PickResult(target, 0, 0)));
    }

    private static DockLayoutView.Group group(DockingManager manager, String id) {
        return manager.layout().windows().getFirst().areas().stream().flatMap(area -> area.groups().stream())
                .filter(group -> group.id().equals(new DockGroupId(id))).findFirst().orElseThrow();
    }

    private static TabPane tabs(DockPane pane, Dockable dockable) {
        return pane.lookupAll(".tab-pane").stream().map(TabPane.class::cast)
                .filter(candidate -> candidate.getTabs().stream().anyMatch(tab -> tab.getContent() == dockable.getContent()))
                .findFirst().orElseThrow();
    }

    private static DockLayoutView.Group group(DockingManager manager) {
        return manager.layout().windows().getFirst().areas().stream()
                .filter(area -> area.id().equals(new DockAreaId("tools"))).findFirst().orElseThrow().groups().getFirst();
    }
}
