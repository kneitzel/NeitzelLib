package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Placement commands retain live contents and publish completed moves. */
class MoveDockablesTest {
    @BeforeAll static void start() throws InterruptedException { FxThread.start(); }

    @Test void anUnchangedPositionNeedsNoNewPlacementPermission() throws Exception {
        FxThread.onFx(() -> {
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage stage = new Stage();
            stage.setScene(new Scene(pane));
            var allowed = new java.util.concurrent.atomic.AtomicBoolean(true);
            DockingOptions options = new DockingOptions();
            options.setPlacementRule((dockable, destination) -> allowed.get() ? java.util.Optional.empty()
                    : java.util.Optional.of(new DockFailure(DockFailure.Code.PLACEMENT_REJECTED,
                    dockable.getId(), "No new placements", null)));
            DockingManager manager = DockingManager.attach(stage, pane, options);
            Dockable dockable = new Dockable(new DockableId("notes"), new TextArea("Draft"));
            manager.open(dockable, DockTarget.area("editors"));
            DockLayoutView before = manager.layout();
            allowed.set(false);
            assertInstanceOf(MoveCheck.NoChange.class, manager.checkMove(dockable.getId(), DockTarget.area("editors")));
            assertInstanceOf(ActionResult.NoChange.class, manager.move(dockable.getId(), DockTarget.area("editors")));
            assertSame(before, manager.layout());
        });
    }

    @Test void anIncorrectFactoryCannotDiscardAlreadyOpenContent() throws Exception {
        FxThread.onFx(() -> {
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage stage = new Stage();
            stage.setScene(new Scene(pane));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            Dockable existing = new Dockable(new DockableId("existing"), new TextArea("Unsaved"));
            AtomicInteger discards = new AtomicInteger();
            existing.setOnDiscarded(discards::incrementAndGet);
            manager.open(existing, DockTarget.area("editors"));
            assertInstanceOf(ActionResult.Rejected.class, manager.open(new DockableId("wrong"),
                    () -> existing, DockTarget.area("editors")));
            assertEquals(0, discards.get());
            assertSame(existing, manager.findDockable(existing.getId()).orElseThrow());
        });
    }

    @Test void aFailedFactoryReportsTheCauseWithoutOpeningContent() throws Exception {
        FxThread.onFx(() -> {
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage stage = new Stage();
            stage.setScene(new Scene(pane));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            DockableId id = new DockableId("failed");
            RuntimeException failure = new IllegalStateException("Load failed");
            ActionResult.Rejected rejected = assertInstanceOf(ActionResult.Rejected.class,
                    manager.open(id, () -> { throw failure; }, DockTarget.area("editors")));
            assertSame(failure, rejected.failure().cause());
            assertEquals(id, rejected.failure().dockableId());
            assertTrue(manager.findDockable(id).isEmpty());
        });
    }

    @Test void aFactoryCannotOpenADifferentIdentityThanRequested() throws Exception {
        FxThread.onFx(() -> {
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage stage = new Stage();
            stage.setScene(new Scene(pane));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            DockLayoutView before = manager.layout();
            DockableId requested = new DockableId("requested");
            Dockable wrong = new Dockable(new DockableId("wrong"), new TextArea("Draft"));
            AtomicInteger discarded = new AtomicInteger();
            wrong.setOnDiscarded(discarded::incrementAndGet);
            ActionResult.Rejected rejected = assertInstanceOf(ActionResult.Rejected.class,
                    manager.open(requested, () -> wrong, DockTarget.area("editors")));
            assertEquals(requested, rejected.failure().dockableId());
            assertSame(before, manager.layout());
            assertTrue(manager.findDockable(wrong.getId()).isEmpty());
            assertEquals(1, discarded.get());
        });
    }

    @Test void cleanupFailureIsReportedAlongsideThePlacementRejection() throws Exception {
        FxThread.onFx(() -> {
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage stage = new Stage();
            stage.setScene(new Scene(pane));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            RuntimeException cleanup = new IllegalStateException("Listener release failed");
            Dockable prepared = new Dockable(new DockableId("notes"), new TextArea("Draft"));
            prepared.setOnDiscarded(() -> { throw cleanup; });
            ActionResult.Rejected rejected = assertInstanceOf(ActionResult.Rejected.class,
                    manager.open(prepared.getId(), () -> prepared, DockTarget.area("missing")));
            assertEquals(DockFailure.Code.TARGET_NOT_FOUND, rejected.failure().code());
            assertSame(cleanup, rejected.failure().cause().getSuppressed()[0]);
            assertTrue(manager.findDockable(prepared.getId()).isEmpty());
        });
    }

    @Test void rejectedFactoryContentIsDiscardedButPreparedContentRemainsWithItsCaller() throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            AtomicInteger factories = new AtomicInteger();
            AtomicInteger discards = new AtomicInteger();
            DockableId notesId = new DockableId("notes");
            Dockable notes = new Dockable(notesId, new TextArea("Unsaved"));
            notes.setOnDiscarded(discards::incrementAndGet);
            assertInstanceOf(ActionResult.Rejected.class, manager.open(notesId, () -> {
                factories.incrementAndGet();
                assertTrue(javafx.application.Platform.isFxApplicationThread());
                return notes;
            }, DockTarget.area("missing")));
            assertEquals(1, factories.get());
            assertEquals(1, discards.get());
            assertTrue(manager.findDockable(notesId).isEmpty());
            Dockable prepared = new Dockable(new DockableId("prepared"), new TextArea("Draft"));
            prepared.setOnDiscarded(discards::incrementAndGet);
            assertInstanceOf(ActionResult.Rejected.class, manager.open(prepared, DockTarget.area("missing")));
            assertEquals(1, discards.get());
            manager.open(prepared, DockTarget.area("editors"));
            manager.open(prepared.getId(), () -> { throw new AssertionError("Already open"); }, DockTarget.area("missing"));
            assertSame(prepared, manager.findDockable(prepared.getId()).orElseThrow());
            assertEquals(1, discards.get());
        });
    }

    @Test void checksAreSideEffectFreeAndExecutionRechecksTheCapturedRule() throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            initial.setRight(new DockGroupDefinition("tools"));
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane));
            var allowed = new java.util.concurrent.atomic.AtomicBoolean(true);
            DockingOptions options = new DockingOptions();
            options.setPlacementRule((dockable, target) -> allowed.get() ? java.util.Optional.empty()
                    : java.util.Optional.of(new DockFailure(DockFailure.Code.PLACEMENT_REJECTED,
                    dockable.getId(), "Editor must remain here", null)));
            DockingManager manager = DockingManager.attach(stage, pane, options);
            Dockable notes = new Dockable(new DockableId("notes"), new TextArea("Draft"));
            manager.open(notes, DockTarget.area("editors"));
            DockLayoutView before = manager.layout();
            assertInstanceOf(MoveCheck.Allowed.class, manager.checkMove(notes.getId(), DockTarget.area("tools")));
            assertSame(before, manager.layout());
            allowed.set(false);
            options.setPlacementRule((dockable, target) -> java.util.Optional.empty());
            ActionResult.Rejected rejected = assertInstanceOf(ActionResult.Rejected.class,
                    manager.move(notes.getId(), DockTarget.area("tools")));
            assertEquals(DockFailure.Code.PLACEMENT_REJECTED, rejected.failure().code());
            assertSame(before, manager.layout());
            assertEquals(DockFailure.Code.TARGET_NOT_FOUND, assertInstanceOf(MoveCheck.Rejected.class,
                    manager.checkMove(notes.getId(), DockTarget.group(new DockGroupId("gone")))).failure().code());
            assertEquals(DockFailure.Code.REFERENCE_TAB_NOT_FOUND, assertInstanceOf(MoveCheck.Rejected.class,
                    manager.checkMove(notes.getId(), DockTarget.group(new DockGroupId("tools"),
                            TabPosition.after(new DockableId("gone"))))).failure().code());
        });
    }

    @Test void movesTheSameEditorBeforeAnotherTabAndPublishesOnce() throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            initial.setRight(new DockGroupDefinition("tools"));
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 800, 500));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            TextArea text = new TextArea("Unsaved notes");
            Dockable notes = new Dockable(new DockableId("notes"), text);
            Dockable project = new Dockable(new DockableId("project"), new TextArea("Project"));
            manager.open(notes, DockTarget.area("editors"));
            manager.open(project, DockTarget.area("tools"));
            text.selectRange(2, 7);
            AtomicInteger changes = new AtomicInteger();
            manager.layoutProperty().addListener((observable, before, after) -> changes.incrementAndGet());
            var target = DockTarget.group(new DockGroupId("tools"), TabPosition.before(project.getId()));
            assertInstanceOf(MoveCheck.Allowed.class, manager.checkMove(notes.getId(), target));
            assertEquals(0, changes.get());
            assertInstanceOf(ActionResult.Applied.class, manager.move(notes.getId(), target));
            assertEquals(1, changes.get());
            assertEquals(List.of(notes.getId(), project.getId()), manager.layout().windows().getFirst()
                    .areas().get(1).groups().getFirst().dockableIds());
            assertSame(text, manager.findDockable(notes.getId()).orElseThrow().getContent());
            assertEquals("Unsaved notes", text.getText());
            assertEquals(2, text.getSelection().getStart());
            assertEquals(7, text.getSelection().getEnd());
            assertSame(notes, manager.activeDockable());
            assertInstanceOf(MoveCheck.NoChange.class, manager.checkMove(notes.getId(), target));
        });
    }
}
