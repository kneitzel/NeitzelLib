package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A split displays two groups while preserving the same live Dockables. */
class SplitDockGroupsTest {
    @BeforeAll
    static void startJavaFx() throws InterruptedException { FxThread.start(); }

    @Test
    void splittingAnEditorCreatesAnAdjacentGroupWithTheSameContent() throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 800, 500));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            TextArea notes = new TextArea("Unsaved notes");
            Dockable moved = new Dockable(new DockableId("notes"), notes);
            Dockable remaining = new Dockable(new DockableId("readme"), new TextArea("Readme"));
            manager.open(moved, DockTarget.area("editors"));
            manager.open(remaining, DockTarget.area("editors"));
            pane.resize(800, 500);
            pane.applyCss();
            pane.layout();
            DockGroupId original = manager.layout().windows().getFirst().areas().getFirst().groups().getFirst().id();
            assertInstanceOf(ActionResult.Applied.class, manager.move(moved.getId(), DockTarget.split(original, Side.RIGHT)));
            var groups = manager.layout().windows().getFirst().areas().getFirst().groups();
            assertEquals(2, groups.size());
            assertEquals(List.of(remaining.getId()), groups.getFirst().dockableIds());
            assertEquals(List.of(moved.getId()), groups.getLast().dockableIds());
            assertSame(notes, manager.findDockable(moved.getId()).orElseThrow().getContent());
            assertSame(moved, manager.activeDockable());
        });
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void emptyingEitherBranchDissolvesTheSplitAndPreservesTheArea(boolean emptyOriginal) throws Exception {
        FxThread.onFx(() -> {
            Fixture fixture = fixture();
            DockingManager manager = fixture.manager();
            DockGroupId original = manager.layout().windows().getFirst().areas().getFirst().groups().getFirst().id();
            manager.move(fixture.notes(), DockTarget.split(original, Side.RIGHT));
            DockGroupId added = manager.layout().windows().getFirst().areas().getFirst().groups().getLast().id();
            DockGroupId survivor = emptyOriginal ? added : original;
            DockGroupId removed = emptyOriginal ? original : added;
            DockableId moved = emptyOriginal ? fixture.readme() : fixture.notes();
            var changes = new java.util.ArrayList<DockLayoutView>();
            manager.layoutProperty().addListener((observable, old, current) -> changes.add(current));
            assertInstanceOf(ActionResult.Applied.class, manager.move(moved, DockTarget.group(survivor)));
            assertEquals(1, changes.size());
            var area = manager.layout().windows().getFirst().areas().getFirst();
            assertEquals(1, area.groups().size());
            assertEquals(survivor, area.groups().getFirst().id());
            assertInstanceOf(DockLayoutView.Group.class, area.root());
            assertInstanceOf(MoveCheck.Rejected.class, manager.checkMove(moved, DockTarget.group(removed)));
            Dockable next = new Dockable(new DockableId("next"), new TextArea());
            assertInstanceOf(ActionResult.Applied.class, manager.open(next, DockTarget.area("editors")));
            assertEquals(3, manager.layout().windows().getFirst().areas().getFirst().groups().getFirst().dockableIds().size());
        });
    }

    @Test
    void nestedFxmlSplitsSurviveWhenTheirSiblingBecomesEmpty() throws Exception {
        FxThread.onFx(() -> {
            DockPane pane = javafx.fxml.FXMLLoader.load(getClass().getResource("docking-nested-splits.fxml"));
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 900, 600));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            for (String area : List.of("one", "two", "three")) {
                manager.open(new Dockable(new DockableId(area), new TextArea(area)), DockTarget.area(area));
            }
            manager.move(new DockableId("one"), DockTarget.area("two"));
            var editorArea = manager.layout().windows().getFirst().areas().getFirst();
            assertEquals(new DockAreaId("editors"), editorArea.id());
            assertEquals(2, editorArea.groups().size());
            DockLayoutView.Split remaining = assertInstanceOf(DockLayoutView.Split.class, editorArea.root());
            assertEquals(javafx.geometry.Orientation.VERTICAL, remaining.orientation());
            assertEquals(.3, remaining.position(), .001);
            assertInstanceOf(MoveCheck.Rejected.class, manager.checkMove(new DockableId("one"),
                    DockTarget.group(new DockGroupId("one"))));
            assertFalse(manager.checkMove(new DockableId("three"), DockTarget.area("one")) instanceof MoveCheck.Rejected);
        });
    }

    @Test
    void areaTargetsPreferTheMostRecentlyActiveEligibleGroup() throws Exception {
        FxThread.onFx(() -> {
            var rejectedGroup = new java.util.concurrent.atomic.AtomicReference<DockGroupId>();
            DockingOptions options = new DockingOptions();
            options.setPlacementRule((dockable, destination) -> destination instanceof ResolvedDockTarget.Insertion insertion
                    && insertion.groupId().equals(rejectedGroup.get())
                    ? java.util.Optional.of(new DockFailure(DockFailure.Code.PLACEMENT_REJECTED,
                    dockable.getId(), "Use another group", null)) : java.util.Optional.empty());
            Fixture fixture = fixture(options);
            DockingManager manager = fixture.manager();
            DockGroupId original = manager.layout().windows().getFirst().areas().getFirst().groups().getFirst().id();
            manager.move(fixture.notes(), DockTarget.split(original, Side.RIGHT));
            DockGroupId right = manager.layout().windows().getFirst().areas().getFirst().groups().getLast().id();
            Dockable third = new Dockable(new DockableId("third"), new TextArea());
            manager.open(third, DockTarget.area("editors"));
            assertEquals(List.of(fixture.notes(), third.getId()), manager.layout().windows().getFirst()
                    .areas().getFirst().groups().getLast().dockableIds());
            rejectedGroup.set(right);
            Dockable fourth = new Dockable(new DockableId("fourth"), new TextArea());
            assertInstanceOf(ActionResult.Applied.class, manager.open(fourth, DockTarget.area("editors")));
            assertEquals(List.of(fixture.readme(), fourth.getId()), manager.layout().windows().getFirst()
                    .areas().getFirst().groups().getFirst().dockableIds());
        });
    }

    @Test
    void splitDirectionsAndBothMinimumSizesAreCheckedBeforeCreatingAGroup() throws Exception {
        FxThread.onFx(() -> {
            DockPane pane = javafx.fxml.FXMLLoader.load(getClass().getResource("docking-split-policy.fxml"));
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 800, 500));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            DockableId notes = new DockableId("notes");
            manager.open(new Dockable(notes, new TextArea()), DockTarget.area("editors"));
            manager.open(new Dockable(new DockableId("readme"), new TextArea()), DockTarget.area("editors"));
            pane.resize(800, 500);
            pane.applyCss();
            pane.layout();
            DockGroupId original = manager.layout().windows().getFirst().areas().getFirst().groups().getFirst().id();
            var before = manager.layout();
            assertInstanceOf(MoveCheck.Rejected.class, manager.checkMove(notes, DockTarget.split(original, Side.RIGHT)));
            assertInstanceOf(ActionResult.Rejected.class, manager.move(notes, DockTarget.split(original, Side.BOTTOM)));
            assertEquals(before, manager.layout());
            pane.resize(800, 900);
            pane.layout();
            assertInstanceOf(ActionResult.Applied.class, manager.move(notes, DockTarget.split(original, Side.TOP)));
            DockLayoutView.Split split = assertInstanceOf(DockLayoutView.Split.class,
                    manager.layout().windows().getFirst().areas().getFirst().root());
            assertEquals(.5, split.position());
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = Side.class, names = {"RIGHT", "BOTTOM"})
    void dividerGesturesPublishOnlyTheirCompletedGeometry(Side side) throws Exception {
        FxThread.onFx(() -> {
            Fixture fixture = fixture();
            DockingManager manager = fixture.manager();
            DockGroupId original = manager.layout().windows().getFirst().areas().getFirst().groups().getFirst().id();
            manager.move(fixture.notes(), DockTarget.split(original, side));
            fixture.pane().applyCss();
            fixture.pane().layout();
            javafx.scene.control.SplitPane split = (javafx.scene.control.SplitPane) fixture.pane().lookup(".split-pane");
            javafx.scene.Node divider = split.lookup(".split-pane-divider");
            assertNotNull(divider);
            var changes = new java.util.ArrayList<DockLayoutView>();
            manager.layoutProperty().addListener((observable, old, current) -> changes.add(current));
            divider.fireEvent(dividerMouse(javafx.scene.input.MouseEvent.MOUSE_PRESSED, divider));
            assertEquals(side == Side.RIGHT ? javafx.scene.Cursor.H_RESIZE : javafx.scene.Cursor.V_RESIZE, divider.getCursor());
            split.setDividerPositions(.7);
            fixture.pane().layout();
            assertEquals(.5, ((DockLayoutView.Split) manager.layout().windows().getFirst().areas().getFirst().root()).position());
            assertTrue(changes.isEmpty());
            divider.fireEvent(dividerMouse(javafx.scene.input.MouseEvent.MOUSE_RELEASED, divider));
            assertEquals(1, changes.size());
            assertEquals(.7, ((DockLayoutView.Split) changes.getFirst().windows().getFirst().areas().getFirst().root()).position(), .02);
        });
    }

    @Test
    void movingOneTabDoesNotDissolveAnUnrelatedInitiallyEmptySplit() throws Exception {
        FxThread.onFx(() -> {
            DockPane pane = javafx.fxml.FXMLLoader.load(getClass().getResource("docking-nested-splits.fxml"));
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 900, 600));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            manager.open(new Dockable(new DockableId("one"), new TextArea()), DockTarget.area("one"));
            manager.open(new Dockable(new DockableId("moving"), new TextArea()), DockTarget.area("one"));
            manager.open(new Dockable(new DockableId("two"), new TextArea()), DockTarget.area("two"));
            assertInstanceOf(ActionResult.Applied.class, manager.move(new DockableId("moving"), DockTarget.area("two")));
            DockLayoutView.Split root = assertInstanceOf(DockLayoutView.Split.class,
                    manager.layout().windows().getFirst().areas().getFirst().root());
            assertInstanceOf(DockLayoutView.Split.class, root.second());
            assertInstanceOf(ActionResult.Applied.class,
                    manager.open(new Dockable(new DockableId("three"), new TextArea()),
                            DockTarget.group(new DockGroupId("three"))));
        });
    }

    @Test
    void aNonpersistentRootDisappearsOnlyAfterItsLastContentLeaves() throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            DockGroupDefinition temporary = new DockGroupDefinition("temporary");
            temporary.setPersistent(false);
            initial.setLeft(temporary);
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 800, 500));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            DockableId id = new DockableId("notes");
            assertInstanceOf(ActionResult.Applied.class,
                    manager.open(new Dockable(id, new TextArea()), DockTarget.area("temporary")));
            assertInstanceOf(ActionResult.Applied.class, manager.move(id, DockTarget.area("editors")));
            assertEquals(List.of(new DockAreaId("editors")), manager.layout().windows().getFirst().areas().stream()
                    .map(DockLayoutView.Area::id).toList());
            MoveCheck.Rejected missing = assertInstanceOf(MoveCheck.Rejected.class,
                    manager.checkMove(id, DockTarget.area("temporary")));
            assertEquals(DockFailure.Code.TARGET_NOT_FOUND, missing.failure().code());
        });
    }

    @Test
    void releasingEmptySpaceHidesTheGroupEvenWithDefaultEmptyVisibility() throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            DockGroupDefinition tools = new DockGroupDefinition("tools");
            tools.setReleaseSpaceWhenEmpty(true);
            initial.setRight(tools);
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 800, 500));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            Dockable notes = new Dockable(new DockableId("notes"), new TextArea());
            manager.open(notes, DockTarget.area("tools"));
            pane.resize(800, 500);
            pane.applyCss();
            pane.layout();
            javafx.scene.control.TabPane tabs = pane.lookupAll(".tab-pane").stream()
                    .map(javafx.scene.control.TabPane.class::cast)
                    .filter(candidate -> !candidate.getTabs().isEmpty()).findFirst().orElseThrow();
            double width = tabs.getWidth();
            manager.move(notes.getId(), DockTarget.area("editors"));
            pane.layout();
            assertFalse(tabs.isManaged());
            assertFalse(tabs.isVisible(), "A released area must not remain painted over its replacement");
            manager.move(notes.getId(), DockTarget.area("tools"));
            pane.layout();
            assertTrue(tabs.isVisible());
            assertTrue(tabs.isManaged());
            assertEquals(width, tabs.getWidth(), 1);
        });
    }

    @Test
    void thePublishedInitialSplitHonorsItsMinimumAdjustedGeometry() throws Exception {
        FxThread.onFx(() -> {
            DockGroupDefinition first = new DockGroupDefinition("first");
            first.setMinimumWidth(200);
            DockSplitDefinition split = new DockSplitDefinition();
            split.setDestinationId("editors");
            split.setFirst(first);
            split.setSecond(new DockGroupDefinition("second"));
            split.setPosition(.1);
            DockLayout initial = new DockLayout();
            initial.setCenter(split);
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 500, 400));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            DockLayoutView.Split view = assertInstanceOf(DockLayoutView.Split.class,
                    manager.layout().windows().getFirst().areas().getFirst().root());
            assertTrue(view.position() >= .4 && view.position() < .5,
                    "Published geometry must describe the minimum-adjusted layout");
            javafx.scene.control.SplitPane surface = (javafx.scene.control.SplitPane) pane.lookup(".split-pane");
            assertEquals(surface.getDividers().getFirst().getPosition(), view.position(), .001);
        });
    }

    @Test
    void emptyingASiblingCannotResizeAGroupWhoseCloseConsentIsPending() throws Exception {
        FxThread.onFx(() -> {
            DockSplitDefinition split = new DockSplitDefinition();
            split.setDestinationId("editors");
            split.setFirst(new DockGroupDefinition("pending"));
            split.setSecond(new DockGroupDefinition("sibling"));
            DockLayout initial = new DockLayout();
            initial.setCenter(split);
            initial.setRight(new DockGroupDefinition("tools"));
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 900, 600));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            var consent = new java.util.concurrent.CompletableFuture<CloseConsent>();
            Dockable pending = new Dockable(new DockableId("pending"), new TextArea());
            pending.setCloseCheck(request -> consent);
            manager.open(pending, DockTarget.area("pending"));
            DockableId last = new DockableId("last");
            DockableId extra = new DockableId("extra");
            manager.open(new Dockable(last, new TextArea()), DockTarget.area("sibling"));
            manager.open(new Dockable(extra, new TextArea()), DockTarget.area("sibling"));
            var close = manager.requestTabClose(pending.getId());
            assertInstanceOf(ActionResult.Applied.class, manager.move(extra, DockTarget.area("tools")),
                    "Moving a non-last sibling tab does not affect the pending group");
            DockLayoutView before = manager.layout();
            MoveCheck.Rejected check = assertInstanceOf(MoveCheck.Rejected.class,
                    manager.checkMove(last, DockTarget.area("tools")));
            assertEquals(DockFailure.Code.CLOSE_IN_PROGRESS, check.failure().code());
            ActionResult.Rejected rejected = assertInstanceOf(ActionResult.Rejected.class,
                    manager.move(last, DockTarget.area("tools")));
            assertEquals(DockFailure.Code.CLOSE_IN_PROGRESS, rejected.failure().code());
            assertEquals(before, manager.layout());
            consent.complete(CloseConsent.VETO);
            assertInstanceOf(CloseResult.Vetoed.class, close.toCompletableFuture().join());
            assertInstanceOf(ActionResult.Applied.class, manager.move(last, DockTarget.area("tools")));
            assertInstanceOf(DockLayoutView.Group.class,
                    manager.layout().windows().getFirst().areas().getFirst().root());
        });
    }

    @Test
    void anUnrelatedPendingCloseDoesNotCaptureANestedDividerGesture() throws Exception {
        FxThread.onFx(() -> {
            DockPane pane = javafx.fxml.FXMLLoader.load(getClass().getResource("docking-nested-splits.fxml"));
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 900, 600));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            var consent = new java.util.concurrent.CompletableFuture<CloseConsent>();
            for (String area : List.of("one", "two", "three")) {
                Dockable dockable = new Dockable(new DockableId(area), new TextArea(area));
                if (area.equals("one")) dockable.setCloseCheck(request -> consent);
                manager.open(dockable, DockTarget.area(area));
            }
            var close = manager.requestTabClose(new DockableId("one"));
            try {
                javafx.scene.control.SplitPane nested = pane.lookupAll(".split-pane").stream()
                        .map(javafx.scene.control.SplitPane.class::cast)
                        .filter(candidate -> candidate.getOrientation() == javafx.geometry.Orientation.VERTICAL)
                        .findFirst().orElseThrow();
                javafx.scene.Node divider = nested.lookup(".split-pane-divider");
                var changes = new java.util.ArrayList<DockLayoutView>();
                manager.layoutProperty().addListener((observable, old, current) -> changes.add(current));
                divider.fireEvent(dividerMouse(javafx.scene.input.MouseEvent.MOUSE_PRESSED, divider));
                nested.setDividerPositions(.7);
                pane.layout();
                assertTrue(changes.isEmpty());
                divider.fireEvent(dividerMouse(javafx.scene.input.MouseEvent.MOUSE_RELEASED, divider));
                assertEquals(1, changes.size(), "Only the owning nested split handles its divider");
                assertEquals(javafx.scene.Cursor.V_RESIZE, divider.getCursor());
                DockLayoutView.Split root = (DockLayoutView.Split) manager.layout().windows().getFirst().areas().getFirst().root();
                assertEquals(.4, root.position(), .01);
                assertEquals(.7, ((DockLayoutView.Split) root.second()).position(), .02);
                assertFalse(close.toCompletableFuture().isDone());
            } finally {
                consent.complete(CloseConsent.VETO);
            }
        });
    }

    private static javafx.scene.input.MouseEvent dividerMouse(javafx.event.EventType<javafx.scene.input.MouseEvent> type,
                                                             javafx.scene.Node divider) {
        return new javafx.scene.input.MouseEvent(type, 0, 0, 0, 0, javafx.scene.input.MouseButton.PRIMARY, 1,
                false, false, false, false, type != javafx.scene.input.MouseEvent.MOUSE_RELEASED,
                false, false, false, false, false, new javafx.scene.input.PickResult(divider, 0, 0));
    }

    private static Fixture fixture() { return fixture(new DockingOptions()); }

    private static Fixture fixture(DockingOptions options) {
        DockLayout initial = new DockLayout();
        initial.setCenter(new DockGroupDefinition("editors"));
        DockPane pane = new DockPane();
        pane.setInitialLayout(initial);
        Stage stage = new Stage();
        stage.setScene(new Scene(pane, 800, 500));
        DockingManager manager = DockingManager.attach(stage, pane, options);
        DockableId notes = new DockableId("notes");
        DockableId readme = new DockableId("readme");
        manager.open(new Dockable(notes, new TextArea("Unsaved notes")), DockTarget.area("editors"));
        manager.open(new Dockable(readme, new TextArea("Readme")), DockTarget.area("editors"));
        pane.resize(800, 500);
        pane.applyCss();
        pane.layout();
        return new Fixture(manager, pane, notes, readme);
    }

    private record Fixture(DockingManager manager, DockPane pane, DockableId notes, DockableId readme) { }
}
