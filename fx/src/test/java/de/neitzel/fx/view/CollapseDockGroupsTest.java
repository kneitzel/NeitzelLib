package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Group presentation is exercised through public operations and the rendered JavaFX surface. */
class CollapseDockGroupsTest {
    @BeforeAll
    static void startJavaFx() throws InterruptedException { FxThread.start(); }

    @Test
    void collapsingAnEdgeGroupKeepsItsHeadersAndReleasesItsContentSpace() throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            DockGroupDefinition tools = new DockGroupDefinition("tools");
            tools.setTabSide(Side.RIGHT);
            tools.setCollapsible(true);
            initial.setRight(tools);
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 800, 500));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            manager.open(new Dockable(new DockableId("editor"), new TextArea()), DockTarget.area("editors"));
            Dockable project = new Dockable(new DockableId("project"), new TextArea("Project"));
            project.setTitle("Project");
            project.setIcon(new Label("P"));
            manager.open(project, DockTarget.area("tools"));
            TabPane tabs = pane.lookupAll(".tab-pane").stream().map(TabPane.class::cast)
                    .filter(candidate -> candidate.getTabs().stream().anyMatch(tab -> tab.getContent() == project.getContent()))
                    .findFirst().orElseThrow();
            double expandedWidth = tabs.getWidth();
            assertInstanceOf(ActionResult.Applied.class, manager.setGroupCollapsed(new DockGroupId("tools"), true));
            DockLayoutView.Group group = manager.layout().windows().getFirst().areas().stream()
                    .filter(area -> area.id().equals(new DockAreaId("tools"))).findFirst().orElseThrow().groups().getFirst();
            assertTrue(group.collapsed());
            assertTrue(tabs.lookup(".tab-header-area").isVisible());
            assertFalse(tabs.lookup(".tab-content-area").isVisible());
            assertTrue(tabs.getWidth() < expandedWidth / 2, "The whole group releases its content width");
            assertEquals(new DockableId("editor"), manager.activeDockable().getId());
            assertInstanceOf(ActionResult.Applied.class, manager.setGroupCollapsed(new DockGroupId("tools"), false));
            assertSame(project.getContent(), pane.getScene().getFocusOwner(),
                    "Reopening the same selected tab restores keyboard focus into its content");
            assertTrue(visibleThroughParents(project.getContent()));
        });
    }

    @Test
    void clickingProjectThenChangedFilesCollapsesAndReopensTheSharedGroup() throws Exception {
        FxThread.onFx(() -> {
            Fixture fixture = fixture();
            TabPane tabs = fixture.tabs();
            double width = tabs.getWidth();
            javafx.scene.Node projectHeader = header(tabs, fixture.project().getId());
            click(projectHeader);
            assertTrue(group(fixture.manager()).collapsed());
            assertFalse(visibleThroughParents(fixture.project().getContent()), "The selected tab content is hidden, not just the first tab");
            assertSame(projectHeader, fixture.pane().getScene().getFocusOwner());
            assertEquals(new DockableId("editor"), fixture.manager().activeDockable().getId());
            click(header(tabs, fixture.changes().getId()));
            assertFalse(group(fixture.manager()).collapsed());
            assertSame(fixture.changes(), fixture.manager().activeDockable());
            assertTrue(visibleThroughParents(fixture.changes().getContent()));
            assertSame(fixture.changes().getContent(), tabs.getSelectionModel().getSelectedItem().getContent());
            assertEquals(width, tabs.getWidth(), 1);
        });
    }

    @Test
    void aGroupUpdateAppliesOnlyExplicitSettingsTogetherIncludingFalse() throws Exception {
        FxThread.onFx(() -> {
            Fixture fixture = fixture();
            DockingManager manager = fixture.manager();
            DockGroupId tools = new DockGroupId("tools");
            assertInstanceOf(ActionResult.Rejected.class,
                    manager.setGroupCollapsed(new DockGroupId("editors"), true));
            manager.setGroupCollapsed(tools, true);
            var changes = new java.util.ArrayList<DockLayoutView>();
            manager.layoutProperty().addListener((observable, old, current) -> changes.add(current));
            assertInstanceOf(ActionResult.Applied.class, manager.updateGroup(tools, DockGroupChanges.builder()
                    .preferredWidth(260.0).tabSide(Side.LEFT).collapsible(false).build()));
            assertEquals(1, changes.size());
            assertFalse(group(manager).collapsed());
            assertEquals(Side.LEFT, group(manager).tabSide());
            assertEquals(260, fixture.tabs().getWidth(), 1);
            assertEquals(150, fixture.tabs().prefHeight(-1), "Unspecified dimensions are retained");
            ActionResult.Rejected disabled = assertInstanceOf(ActionResult.Rejected.class,
                    manager.setGroupCollapsed(tools, true));
            assertEquals(DockFailure.Code.COLLAPSE_DISABLED, disabled.failure().code());
            DockLayoutView beforeInvalid = manager.layout();
            assertInstanceOf(ActionResult.Rejected.class, manager.updateGroup(tools, DockGroupChanges.builder()
                    .tabSide(Side.BOTTOM).preferredWidth(-1.0).build()));
            assertEquals(beforeInvalid, manager.layout());
            assertEquals(Side.LEFT, fixture.tabs().getSide());
            assertEquals(1, changes.size());
            assertInstanceOf(ActionResult.NoChange.class, manager.updateGroup(tools, DockGroupChanges.builder().build()));
        });
    }

    @Test
    void escapeReturnsFocusToTheSymbolAndActivationReopensTheRequestedTab() throws Exception {
        FxThread.onFx(() -> {
            Fixture fixture = fixture();
            var changes = new java.util.ArrayList<DockLayoutView>();
            fixture.manager().layoutProperty().addListener((observable, old, current) -> changes.add(current));
            fixture.project().getContent().fireEvent(new javafx.scene.input.KeyEvent(
                    javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.ESCAPE,
                    false, false, false, false));
            assertTrue(group(fixture.manager()).collapsed());
            assertEquals(1, changes.size());
            assertSame(header(fixture.tabs(), fixture.project().getId()), fixture.pane().getScene().getFocusOwner());
            assertEquals(new DockableId("editor"), fixture.manager().activeDockable().getId());
            assertInstanceOf(ActionResult.Applied.class, fixture.manager().activate(fixture.changes().getId()));
            assertFalse(group(fixture.manager()).collapsed());
            assertSame(fixture.changes(), fixture.manager().activeDockable());
            assertTrue(visibleThroughParents(fixture.changes().getContent()));
            assertEquals(2, changes.size());
        });
    }

    @Test
    void closingTheLastVisibleContentDoesNotActivateACollapsedGroup() throws Exception {
        FxThread.onFx(() -> {
            Fixture fixture = fixture();
            fixture.manager().setGroupCollapsed(new DockGroupId("tools"), true);
            assertEquals(new DockableId("editor"), fixture.manager().activeDockable().getId());
            var closed = fixture.manager().requestTabClose(new DockableId("editor"));
            assertInstanceOf(CloseResult.Closed.class, closed.toCompletableFuture().join());
            assertNull(fixture.manager().activeDockable());
            assertTrue(group(fixture.manager()).collapsed());
        });
    }

    @Test
    void aPendingCenterCloseProtectsItsGeometryFromToolGroupChanges() throws Exception {
        FxThread.onFx(() -> {
            Fixture fixture = fixture();
            DockingManager manager = fixture.manager();
            var consent = new java.util.concurrent.CompletableFuture<CloseConsent>();
            Dockable editor = manager.findDockable(new DockableId("editor")).orElseThrow();
            editor.setCloseCheck(request -> consent);
            manager.requestTabClose(editor.getId());
            DockGroupId tools = new DockGroupId("tools");
            try {
                DockLayoutView before = manager.layout();
                ActionResult.Rejected collapse = assertInstanceOf(ActionResult.Rejected.class,
                        manager.setGroupCollapsed(tools, true));
                assertEquals(DockFailure.Code.CLOSE_IN_PROGRESS, collapse.failure().code());
                ActionResult.Rejected resize = assertInstanceOf(ActionResult.Rejected.class,
                        manager.updateGroup(tools, DockGroupChanges.builder().preferredWidth(300).build()));
                assertEquals(DockFailure.Code.CLOSE_IN_PROGRESS, resize.failure().code());
                assertEquals(before, manager.layout());
                assertInstanceOf(ActionResult.Applied.class,
                        manager.updateGroup(tools, DockGroupChanges.builder().splitDirections(SplitDirections.NONE).build()),
                        "Non-geometric settings remain independent of a neighboring close");
            } finally {
                consent.complete(CloseConsent.VETO);
            }
            assertInstanceOf(ActionResult.Applied.class, manager.setGroupCollapsed(tools, true));
            assertInstanceOf(ActionResult.Applied.class,
                    manager.updateGroup(tools, DockGroupChanges.builder().preferredWidth(300).build()));
            var secondConsent = new java.util.concurrent.CompletableFuture<CloseConsent>();
            editor.setCloseCheck(request -> secondConsent);
            manager.requestTabClose(editor.getId());
            try {
                assertInstanceOf(ActionResult.NoChange.class,
                        manager.move(fixture.project().getId(), DockTarget.group(tools)),
                        "A move that leaves a collapsed group unchanged remains allowed");
                ActionResult.Rejected activation = assertInstanceOf(ActionResult.Rejected.class,
                        manager.activate(fixture.project().getId()));
                assertEquals(DockFailure.Code.CLOSE_IN_PROGRESS, activation.failure().code());
                assertTrue(group(manager).collapsed());
            } finally {
                secondConsent.complete(CloseConsent.VETO);
            }
            manager.setGroupCollapsed(tools, false);
            assertEquals(300, fixture.tabs().getWidth(), 1);
        });
    }

    @Test
    void splitGroupsInheritSettingsAndSubsequentUpdatesRemainIndependent() throws Exception {
        FxThread.onFx(() -> {
            Fixture fixture = fixture();
            DockingManager manager = fixture.manager();
            DockGroupId original = new DockGroupId("tools");
            manager.updateGroup(original, DockGroupChanges.builder().preferredWidth(260)
                    .minimumHeight(180).splitDirections(SplitDirections.VERTICAL).build());
            assertInstanceOf(ActionResult.Applied.class,
                    manager.move(fixture.project().getId(), DockTarget.split(original, Side.BOTTOM)));
            var area = manager.layout().windows().getFirst().areas().stream()
                    .filter(candidate -> candidate.id().equals(new DockAreaId("tools"))).findFirst().orElseThrow();
            DockGroupId added = area.groups().getLast().id();
            assertInstanceOf(ActionResult.Applied.class, manager.setGroupCollapsed(added, true));
            manager.updateGroup(original, DockGroupChanges.builder().collapsible(false)
                    .minimumHeight(20).splitDirections(SplitDirections.ALL).build());
            MoveCheck.Rejected direction = assertInstanceOf(MoveCheck.Rejected.class,
                    manager.checkMove(new DockableId("editor"), DockTarget.split(added, Side.RIGHT)));
            assertEquals(DockFailure.Code.SPLIT_DISABLED, direction.failure().code());
            MoveCheck.Rejected minimum = assertInstanceOf(MoveCheck.Rejected.class,
                    manager.checkMove(new DockableId("editor"), DockTarget.split(added, Side.TOP)));
            assertEquals(DockFailure.Code.SPLIT_TOO_SMALL, minimum.failure().code());
            assertInstanceOf(MoveCheck.Allowed.class,
                    manager.checkMove(new DockableId("editor"), DockTarget.split(original, Side.TOP)));
            assertInstanceOf(ActionResult.Applied.class,
                    manager.move(fixture.changes().getId(), DockTarget.group(added)));
            ActionResult.Rejected stale = assertInstanceOf(ActionResult.Rejected.class,
                    manager.updateGroup(original, DockGroupChanges.builder().collapsible(true).build()));
            assertEquals(DockFailure.Code.TARGET_NOT_FOUND, stale.failure().code());
        });
    }

    @Test
    void collapsingASplitLeafGivesItsContentSpaceToItsSiblingAndRestoresTheDivider() throws Exception {
        FxThread.onFx(() -> {
            Fixture fixture = fixture();
            DockingManager manager = fixture.manager();
            DockGroupId first = new DockGroupId("tools");
            manager.move(fixture.project().getId(), DockTarget.split(first, Side.BOTTOM));
            TabPane second = fixture.pane().lookupAll(".tab-pane").stream().map(TabPane.class::cast)
                    .filter(candidate -> candidate.getTabs().stream()
                            .anyMatch(tab -> tab.getContent() == fixture.project().getContent())).findFirst().orElseThrow();
            double firstHeight = fixture.tabs().getHeight();
            double secondHeight = second.getHeight();
            DockLayoutView.Split before = (DockLayoutView.Split) manager.layout().windows().getFirst().areas().stream()
                    .filter(area -> area.id().equals(new DockAreaId("tools"))).findFirst().orElseThrow().root();
            assertInstanceOf(ActionResult.Applied.class, manager.setGroupCollapsed(first, true));
            assertTrue(fixture.tabs().getHeight() < firstHeight - 60,
                    "The collapsed leaf retains only its header rail along the split axis");
            assertTrue(second.getHeight() > secondHeight + 60, "The sibling receives the released content space");
            DockLayoutView.Split collapsed = (DockLayoutView.Split) manager.layout().windows().getFirst().areas().stream()
                    .filter(area -> area.id().equals(new DockAreaId("tools"))).findFirst().orElseThrow().root();
            assertEquals(before.position(), collapsed.position(), .001, "Published state remembers the expanded proportion");
            assertInstanceOf(ActionResult.Applied.class, manager.setGroupCollapsed(first, false));
            assertEquals(firstHeight, fixture.tabs().getHeight(), 2);
            assertEquals(secondHeight, second.getHeight(), 2);
        });
    }

    @Test
    void anExplicitWidthIsComparedWithTheLiveDividerExtentAndProtectedAsGeometry() throws Exception {
        FxThread.onFx(() -> {
            Fixture fixture = fixture();
            DockingManager manager = fixture.manager();
            DockGroupId tools = new DockGroupId("tools");
            dragRightBoundary(fixture, 100);
            assertEquals(300, fixture.tabs().getWidth(), 1);
            assertInstanceOf(ActionResult.Applied.class,
                    manager.updateGroup(tools, DockGroupChanges.builder().preferredWidth(200).build()),
                    "An explicit initial width still changes the user's resized live width");
            assertEquals(200, fixture.tabs().getWidth(), 1);
            dragRightBoundary(fixture, 100);
            var consent = new java.util.concurrent.CompletableFuture<CloseConsent>();
            Dockable editor = manager.findDockable(new DockableId("editor")).orElseThrow();
            editor.setCloseCheck(request -> consent);
            manager.requestTabClose(editor.getId());
            try {
                DockLayoutView before = manager.layout();
                ActionResult.Rejected rejected = assertInstanceOf(ActionResult.Rejected.class,
                        manager.updateGroup(tools, DockGroupChanges.builder()
                                .preferredWidth(200).splitDirections(SplitDirections.NONE).build()));
                assertEquals(DockFailure.Code.CLOSE_IN_PROGRESS, rejected.failure().code());
                assertEquals(before, manager.layout());
                assertEquals(300, fixture.tabs().getWidth(), 1);
            } finally {
                consent.complete(CloseConsent.VETO);
            }
            assertInstanceOf(ActionResult.Applied.class,
                    manager.updateGroup(tools, DockGroupChanges.builder().preferredWidth(200).build()));
            assertEquals(200, fixture.tabs().getWidth(), 1);
            assertInstanceOf(ActionResult.NoChange.class,
                    manager.updateGroup(tools, DockGroupChanges.builder().preferredWidth(200).build()));
        });
    }

    @Test
    void anOrdinaryWindowResizeKeepsACollapsedRailFixedAndGivesSpaceToItsSibling() throws Exception {
        var fixtureRef = new java.util.concurrent.atomic.AtomicReference<Fixture>();
        var siblingRef = new java.util.concurrent.atomic.AtomicReference<TabPane>();
        var resized = new java.util.concurrent.CountDownLatch(1);
        double[] before = new double[3];
        try {
            FxThread.onFx(() -> {
                javafx.application.Platform.setImplicitExit(false);
                Fixture fixture = fixture();
                fixtureRef.set(fixture);
                Stage stage = (Stage) fixture.pane().getScene().getWindow();
                stage.show();
                fixture.manager().move(fixture.project().getId(),
                        DockTarget.split(new DockGroupId("tools"), Side.BOTTOM));
                TabPane sibling = fixture.pane().lookupAll(".tab-pane").stream().map(TabPane.class::cast)
                        .filter(candidate -> candidate.getTabs().stream()
                                .anyMatch(tab -> tab.getContent() == fixture.project().getContent())).findFirst().orElseThrow();
                siblingRef.set(sibling);
                fixture.manager().setGroupCollapsed(new DockGroupId("tools"), true);
                before[0] = fixture.tabs().getHeight();
                before[1] = sibling.getHeight();
                before[2] = stage.getScene().getHeight();
                stage.getScene().heightProperty().addListener((observable, old, current) -> {
                    if (current.doubleValue() > before[2] + 60) resized.countDown();
                });
                stage.setHeight(stage.getHeight() + 120);
            });
            assertTrue(resized.await(5, java.util.concurrent.TimeUnit.SECONDS), "The native window resize reaches the Scene");
            FxThread.onFx(() -> {
                Fixture fixture = fixtureRef.get();
                fixture.pane().layout();
                assertEquals(before[0], fixture.tabs().getHeight(), 2,
                        "Resizing a window does not expand the collapsed header rail");
                double addedHeight = fixture.pane().getScene().getHeight() - before[2];
                assertEquals(before[1] + addedHeight, siblingRef.get().getHeight(), 2);
                assertTrue(group(fixture.manager()).collapsed());
            });
        } finally {
            if (fixtureRef.get() != null) FxThread.onFx(() -> ((Stage) fixtureRef.get().pane().getScene().getWindow()).hide());
        }
    }

    @Test
    void onlyTheSelectedOwnedContentIsVisibleAndNestedApplicationTabsRemainUntouched() throws Exception {
        FxThread.onFx(() -> {
            Fixture fixture = fixture();
            DockingManager manager = fixture.manager();
            manager.activate(fixture.changes().getId());
            assertTrue(visibleThroughParents(fixture.changes().getContent()));
            assertFalse(visibleThroughParents(fixture.project().getContent()),
                    "Selecting an earlier tab must not also show the newest tab");

            TabPane applicationTabs = new TabPane(
                    new javafx.scene.control.Tab("Application first", new TextArea("First")),
                    new javafx.scene.control.Tab("Application second", new TextArea("Second")));
            applicationTabs.setStyle("-fx-font-size: 40px;");
            Dockable application = new Dockable(new DockableId("application"), applicationTabs);
            application.setTitle("Application");
            manager.open(application, DockTarget.area("tools"));
            var nestedVisibility = applicationTabs.getChildrenUnmodifiable().stream()
                    .filter(node -> node.getStyleClass().contains("tab-content-area"))
                    .collect(java.util.stream.Collectors.toMap(node -> node, javafx.scene.Node::isVisible));
            assertEquals(2, nestedVisibility.size());
            manager.activate(fixture.changes().getId());
            assertFalse(visibleThroughParents(applicationTabs));
            manager.setGroupCollapsed(new DockGroupId("tools"), true);
            assertFalse(visibleThroughParents(fixture.changes().getContent()));
            assertFalse(visibleThroughParents(fixture.project().getContent()));
            assertFalse(visibleThroughParents(applicationTabs));
            assertTrue(fixture.tabs().getWidth() < 50, "The rail uses its own header, not the application's large-font header");
            nestedVisibility.forEach((node, visible) -> assertEquals(visible, node.isVisible()));
            manager.setGroupCollapsed(new DockGroupId("tools"), false);
            assertTrue(visibleThroughParents(fixture.changes().getContent()));
            assertFalse(visibleThroughParents(fixture.project().getContent()));
            assertFalse(visibleThroughParents(applicationTabs));
            nestedVisibility.forEach((node, visible) -> assertEquals(visible, node.isVisible()));
        });
    }

    private static void dragRightBoundary(Fixture fixture, double pixels) {
        javafx.scene.Node divider = fixture.pane().lookup(".dock-area-divider");
        for (var type : java.util.List.of(javafx.scene.input.MouseEvent.MOUSE_PRESSED,
                javafx.scene.input.MouseEvent.MOUSE_DRAGGED, javafx.scene.input.MouseEvent.MOUSE_RELEASED)) {
            double screenX = type == javafx.scene.input.MouseEvent.MOUSE_PRESSED ? 0 : -pixels;
            divider.fireEvent(new javafx.scene.input.MouseEvent(type, 0, 0, screenX, 0,
                    javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false,
                    type != javafx.scene.input.MouseEvent.MOUSE_RELEASED, false, false, false, false, false,
                    new javafx.scene.input.PickResult(divider, 0, 0)));
            fixture.pane().layout();
        }
    }

    private static DockLayoutView.Group group(DockingManager manager) {
        return manager.layout().windows().getFirst().areas().stream()
                .filter(area -> area.id().equals(new DockAreaId("tools"))).findFirst().orElseThrow().groups().getFirst();
    }

    private static javafx.scene.Node header(TabPane tabs, DockableId id) {
        var tab = tabs.getTabs().stream().filter(candidate -> id.equals(candidate.getUserData())).findFirst().orElseThrow();
        return tabs.lookup("#" + tab.getId());
    }

    private static boolean visibleThroughParents(javafx.scene.Node node) {
        for (javafx.scene.Node current = node; current != null; current = current.getParent()) {
            if (!current.isVisible()) return false;
        }
        return true;
    }

    private static void click(javafx.scene.Node header) {
        for (var type : java.util.List.of(javafx.scene.input.MouseEvent.MOUSE_PRESSED,
                javafx.scene.input.MouseEvent.MOUSE_RELEASED, javafx.scene.input.MouseEvent.MOUSE_CLICKED)) {
            header.fireEvent(new javafx.scene.input.MouseEvent(type, 0, 0, 0, 0,
                    javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false,
                    type == javafx.scene.input.MouseEvent.MOUSE_PRESSED, false, false, false, false, true,
                    new javafx.scene.input.PickResult(header, 0, 0)));
        }
    }

    private static Fixture fixture() {
        DockLayout initial = new DockLayout();
        initial.setCenter(new DockGroupDefinition("editors"));
        DockGroupDefinition tools = new DockGroupDefinition("tools");
        tools.setTabSide(Side.RIGHT);
        tools.setCollapsible(true);
        initial.setRight(tools);
        DockPane pane = new DockPane();
        pane.setInitialLayout(initial);
        Stage stage = new Stage();
        stage.setScene(new Scene(pane, 800, 500));
        DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
        manager.open(new Dockable(new DockableId("editor"), new TextArea()), DockTarget.area("editors"));
        Dockable changes = new Dockable(new DockableId("changes"), new TextArea("Changed files"));
        changes.setTitle("Changed files");
        changes.setIcon(new Label("C"));
        manager.open(changes, DockTarget.area("tools"));
        Dockable project = new Dockable(new DockableId("project"), new TextArea("Project"));
        project.setTitle("Project");
        project.setIcon(new Label("P"));
        manager.open(project, DockTarget.area("tools"));
        TabPane tabs = pane.lookupAll(".tab-pane").stream().map(TabPane.class::cast)
                .filter(candidate -> candidate.getTabs().stream().anyMatch(tab -> tab.getContent() == project.getContent()))
                .findFirst().orElseThrow();
        return new Fixture(manager, pane, tabs, project, changes);
    }

    private record Fixture(DockingManager manager, DockPane pane, TabPane tabs, Dockable project, Dockable changes) { }
}
