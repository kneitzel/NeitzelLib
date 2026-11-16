package de.neitzel.fx.view;

import de.neitzel.fx.docking.ActionResult;
import de.neitzel.fx.docking.CloseConsent;
import de.neitzel.fx.docking.DockGroupChanges;
import de.neitzel.fx.docking.DockFailure;
import de.neitzel.fx.docking.DockGroupDefinition;
import de.neitzel.fx.docking.DockGroupId;
import de.neitzel.fx.docking.DockGroupMode;
import de.neitzel.fx.docking.DockLayout;
import de.neitzel.fx.docking.DockLayoutView;
import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.DockSplitDefinition;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import de.neitzel.fx.docking.ResolvedDockTarget;
import de.neitzel.fx.docking.MoveCheck;
import de.neitzel.fx.docking.TabPosition;
import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.IndexRange;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Reset returns live contents to the captured initial arrangement through the public API. */
class ResetDockingLayoutTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @Test
    void resetKeepsLiveEditsAndRestoresTheCapturedDefinitionsInOnePublication() throws Exception {
        FxThread.onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            DockGroupDefinition editors = new DockGroupDefinition("editors");
            DockGroupDefinition tools = new DockGroupDefinition("tools");
            tools.setCollapsible(true);
            tools.setTabSide(Side.RIGHT);
            tools.setPreferredWidth(240);
            DockLayout initial = new DockLayout();
            initial.setCenter(editors);
            initial.setRight(tools);
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage main = new Stage();
            main.setScene(new Scene(pane, 1000, 600));
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            TextArea text = new TextArea("Unsaved document");
            Dockable notes = new Dockable(new DockableId("notes"), text);
            Dockable readme = new Dockable(new DockableId("readme"), new TextArea("Readme"));
            Dockable project = new Dockable(new DockableId("project"), new TextArea("Project"));
            AtomicInteger released = new AtomicInteger();
            notes.setOnClosed(released::incrementAndGet);
            notes.setOnDiscarded(released::incrementAndGet);
            manager.open(notes, DockTarget.area("editors"));
            manager.open(readme, DockTarget.area("editors"));
            manager.open(project, DockTarget.area("tools"));
            main.show();
            try {
                editors.setTabSide(Side.LEFT);
                tools.setPreferredWidth(390);
                tools.setMode(DockGroupMode.OVERLAY);
                initial.setLeft(new DockGroupDefinition("late-area"));
                assertInstanceOf(ActionResult.Applied.class,
                        manager.move(notes.getId(), DockTarget.split(new DockGroupId("editors"), Side.RIGHT)));
                manager.move(notes.getId(), DockTarget.floating());
                Window floating = text.getScene().getWindow();
                manager.move(project.getId(), DockTarget.area("editors"));
                manager.updateGroup(new DockGroupId("tools"), DockGroupChanges.builder()
                        .mode(DockGroupMode.OVERLAY).preferredWidth(330).build());
                manager.setGroupCollapsed(new DockGroupId("tools"), true);
                text.setText("Still unsaved");
                text.selectRange(0, 5);
                List<DockLayoutView> publications = new ArrayList<>();
                manager.layoutProperty().addListener((observable, old, current) -> publications.add(current));

                assertInstanceOf(ActionResult.Applied.class, manager.resetLayout());
                assertEquals(1, publications.size());
                assertEquals(1, manager.layout().windows().size());
                assertFalse(floating.isShowing());
                assertSame(main, text.getScene().getWindow());
                assertSame(text, manager.findDockable(notes.getId()).orElseThrow().getContent());
                assertEquals("Still unsaved", text.getText());
                assertEquals(new IndexRange(0, 5), text.getSelection());
                assertEquals(0, released.get(), "Reset moves content without closing or discarding it");
                DockLayoutView.Area resetEditors = area(manager, "editors");
                DockLayoutView.Area resetTools = area(manager, "tools");
                assertEquals(1, resetEditors.groups().size());
                assertEquals(List.of(notes.getId(), readme.getId()), resetEditors.groups().getFirst().dockableIds());
                assertEquals(Side.TOP, resetEditors.groups().getFirst().tabSide());
                assertEquals(List.of(project.getId()), resetTools.groups().getFirst().dockableIds());
                assertEquals(Side.RIGHT, resetTools.groups().getFirst().tabSide());
                assertEquals(DockGroupMode.DOCKED, resetTools.groups().getFirst().mode());
                assertFalse(resetTools.groups().getFirst().collapsed());
                assertEquals(240, resetTools.preferredWidth(), 1);
                assertTrue(manager.layout().windows().getFirst().areas().stream()
                        .noneMatch(candidate -> candidate.id().value().equals("late-area")));
            } finally {
                List.copyOf(Window.getWindows()).stream().filter(window -> !existing.contains(window)).forEach(Window::hide);
            }
        });
    }

    private static DockLayoutView.Area area(DockingManager manager, String id) {
        return manager.layout().windows().getFirst().areas().stream()
                .filter(area -> area.id().value().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void aRejectedResetKeepsAllWindowsContentsSelectionAndTheCommittedSnapshot() throws Exception {
        FxThread.onFx(() -> {
            AtomicBoolean reject = new AtomicBoolean();
            DockingOptions options = new DockingOptions();
            options.setPlacementRule((dockable, destination) -> reject.get() && dockable.getId().value().equals("project")
                    ? Optional.of(new DockFailure(DockFailure.Code.PLACEMENT_REJECTED, dockable.getId(),
                            "Project is locked", null, "application.project.locked"))
                    : Optional.empty());
            Fixture fixture = fixture(options);
            DockingManager manager = fixture.manager();
            Dockable notes = open(manager, "notes", "editors");
            Dockable project = open(manager, "project", "tools");
            fixture.stage().show();
            manager.move(notes.getId(), DockTarget.floating());
            Window floating = notes.getContent().getScene().getWindow();
            try {
                TextArea text = (TextArea) notes.getContent();
                text.setText("Unsaved notes");
                text.selectRange(2, 8);
                var layout = manager.layout();
                var snapshot = manager.snapshot();
                var active = manager.activeDockable();
                var parent = text.getParent();
                AtomicInteger publications = new AtomicInteger();
                manager.layoutProperty().addListener((observable, old, current) -> publications.incrementAndGet());
                reject.set(true);

                ActionResult.Rejected result = assertInstanceOf(ActionResult.Rejected.class, manager.resetLayout());
                assertEquals(project.getId(), result.failure().dockableId());
                assertEquals(DockFailure.Code.PLACEMENT_REJECTED, result.failure().code());
                assertEquals("application.project.locked", result.failure().messageKey());
                assertSame(layout, manager.layout());
                assertSame(snapshot, manager.snapshot());
                assertSame(active, manager.activeDockable());
                assertSame(parent, text.getParent());
                assertSame(floating, text.getScene().getWindow());
                assertTrue(floating.isShowing());
                assertEquals("Unsaved notes", text.getText());
                assertEquals(new IndexRange(2, 8), text.getSelection());
                assertEquals(0, publications.get());
                reject.set(false);
                assertInstanceOf(ActionResult.Applied.class, manager.resetLayout(), "A rejection must release the transaction guard");
            } finally { floating.hide(); fixture.stage().hide(); }
        });
    }

    @Test
    void resetUsesAnEligibleFallbackForARejectedHomeAndInitiallyFloatingContent() throws Exception {
        FxThread.onFx(() -> {
            AtomicBoolean rejectHome = new AtomicBoolean();
            DockingOptions options = new DockingOptions();
            options.setFallbackTarget(DockTarget.area("editors"));
            options.setPlacementRule((dockable, destination) -> rejectHome.get()
                    && destination instanceof ResolvedDockTarget.Insertion insertion
                    && insertion.groupId().equals(new DockGroupId("tools"))
                    ? Optional.of(new DockFailure(DockFailure.Code.PLACEMENT_REJECTED, dockable.getId(), "Tools are locked", null))
                    : Optional.empty());
            Fixture fixture = fixture(options);
            DockingManager manager = fixture.manager();
            Dockable project = open(manager, "project", "tools");
            Dockable detached = new Dockable(new DockableId("detached"), new TextArea("First opened floating"));
            fixture.stage().show();
            manager.open(detached, DockTarget.floating());
            Window floating = detached.getContent().getScene().getWindow();
            try {
                rejectHome.set(true);
                assertInstanceOf(ActionResult.Applied.class, manager.resetLayout());
                assertEquals(List.of(project.getId(), detached.getId()), area(manager, "editors").groups().getFirst().dockableIds());
                assertTrue(area(manager, "tools").groups().getFirst().dockableIds().isEmpty(), "The persistent initial target survives empty");
                assertSame(fixture.stage(), detached.getContent().getScene().getWindow());
                assertFalse(floating.isShowing());
            } finally { floating.hide(); fixture.stage().hide(); }
        });
    }

    @Test
    void resetSelectsTheRetainedActiveEditorInItsFinalGroup() throws Exception {
        FxThread.onFx(() -> {
            Fixture fixture = fixture(new DockingOptions());
            DockingManager manager = fixture.manager();
            Dockable first = open(manager, "first", "editors");
            open(manager, "second", "editors");
            fixture.stage().show();
            try {
                manager.activate(first.getId());
                assertInstanceOf(ActionResult.Applied.class, manager.resetLayout());
                assertSame(first, manager.activeDockable());
                assertEquals(first.getId(), area(manager, "editors").groups().getFirst().selectedDockableId(),
                        "The active content must be the visible selected editor after reset");
            } finally { fixture.stage().hide(); }
        });
    }

    private static Dockable open(DockingManager manager, String id, String area) {
        Dockable dockable = new Dockable(new DockableId(id), new TextArea(id));
        assertInstanceOf(ActionResult.Applied.class, manager.open(dockable, DockTarget.area(area)));
        return dockable;
    }

    @Test
    void aRelativeFallbackCanReferToAnEditorOpenedAfterTheFloatingContent() throws Exception {
        FxThread.onFx(() -> {
            DockingOptions options = new DockingOptions();
            DockableId anchor = new DockableId("anchor");
            options.setFallbackTarget(DockTarget.group(new DockGroupId("editors"), TabPosition.before(anchor)));
            Fixture fixture = fixture(options);
            Dockable first = new Dockable(new DockableId("floating-first"), new TextArea("First"));
            fixture.stage().show();
            fixture.manager().open(first, DockTarget.floating());
            Window floating = first.getContent().getScene().getWindow();
            try {
                open(fixture.manager(), anchor.value(), "editors");
                assertInstanceOf(ActionResult.Applied.class, fixture.manager().resetLayout());
                assertEquals(List.of(first.getId(), anchor), area(fixture.manager(), "editors").groups().getFirst().dockableIds());
                assertFalse(floating.isShowing());
            } finally { floating.hide(); fixture.stage().hide(); }
        });
    }

    @Test
    void restoringAnInitialAreaDoesNotReviveItsRetiredConcreteGroupId() throws Exception {
        FxThread.onFx(() -> {
            DockSplitDefinition split = new DockSplitDefinition();
            split.setDestinationId("editors");
            split.setFirst(new DockGroupDefinition("primary"));
            split.setSecond(new DockGroupDefinition("secondary"));
            DockLayout initial = new DockLayout();
            initial.setCenter(split);
            Fixture fixture = fixture(initial, new DockingOptions());
            DockingManager manager = fixture.manager();
            Dockable first = open(manager, "first", "primary");
            Dockable second = open(manager, "second", "secondary");
            fixture.stage().show();
            try {
                manager.move(first.getId(), DockTarget.area("secondary"));
                assertInstanceOf(MoveCheck.Rejected.class, manager.checkMove(second.getId(), DockTarget.group(new DockGroupId("primary"))));
                assertInstanceOf(ActionResult.Applied.class, manager.resetLayout());
                assertEquals(List.of(first.getId()), area(manager, "primary").groups().getFirst().dockableIds());
                assertNotEquals(new DockGroupId("primary"), area(manager, "primary").groups().getFirst().id());
                assertEquals(DockFailure.Code.TARGET_NOT_FOUND, assertInstanceOf(MoveCheck.Rejected.class,
                        manager.checkMove(second.getId(), DockTarget.group(new DockGroupId("primary")))).failure().code());
            } finally { fixture.stage().hide(); }
        });
    }

    @Test
    void resetCannotReplaceTheLayoutWhileAnAsynchronousCloseIsPending() throws Exception {
        FxThread.onFx(() -> {
            Fixture fixture = fixture(new DockingOptions());
            Dockable notes = open(fixture.manager(), "notes", "editors");
            CompletableFuture<CloseConsent> consent = new CompletableFuture<>();
            notes.setCloseCheck(request -> consent);
            fixture.stage().show();
            try {
                fixture.manager().requestTabClose(notes.getId());
                var snapshot = fixture.manager().snapshot();
                ActionResult.Rejected rejected = assertInstanceOf(ActionResult.Rejected.class, fixture.manager().resetLayout());
                assertEquals(DockFailure.Code.CLOSE_IN_PROGRESS, rejected.failure().code());
                assertSame(snapshot, fixture.manager().snapshot());
                assertSame(notes, fixture.manager().findDockable(notes.getId()).orElseThrow());
            } finally { consent.complete(CloseConsent.VETO); fixture.stage().hide(); }
        });
    }

    @Test
    void aPlacementCallbackCannotMoveContentDuringResetPreparation() throws Exception {
        FxThread.onFx(() -> {
            AtomicBoolean resetting = new AtomicBoolean();
            AtomicReference<DockingManager> owner = new AtomicReference<>();
            AtomicReference<ActionResult> reentrant = new AtomicReference<>();
            DockingOptions options = new DockingOptions();
            options.setPlacementRule((dockable, destination) -> {
                if (resetting.compareAndSet(true, false)) reentrant.set(owner.get().move(dockable.getId(), DockTarget.area("tools")));
                return Optional.empty();
            });
            Fixture fixture = fixture(options);
            owner.set(fixture.manager());
            Dockable notes = open(fixture.manager(), "notes", "editors");
            fixture.manager().move(notes.getId(), DockTarget.area("tools"));
            fixture.stage().show();
            try {
                AtomicInteger publications = new AtomicInteger();
                fixture.manager().layoutProperty().addListener((observable, old, current) -> publications.incrementAndGet());
                resetting.set(true);
                assertInstanceOf(ActionResult.Applied.class, fixture.manager().resetLayout());
                assertEquals(DockFailure.Code.LAYOUT_CHANGE_IN_PROGRESS,
                        assertInstanceOf(ActionResult.Rejected.class, reentrant.get()).failure().code());
                assertEquals(List.of(notes.getId()), area(fixture.manager(), "editors").groups().getFirst().dockableIds());
                assertEquals(1, publications.get());
            } finally { fixture.stage().hide(); }
        });
    }

    private static Fixture fixture(DockingOptions options) {
        DockLayout initial = new DockLayout();
        initial.setCenter(new DockGroupDefinition("editors"));
        DockGroupDefinition tools = new DockGroupDefinition("tools");
        tools.setTabSide(Side.RIGHT);
        tools.setCollapsible(true);
        initial.setRight(tools);
        return fixture(initial, options);
    }

    private static Fixture fixture(DockLayout initial, DockingOptions options) {
        DockPane pane = new DockPane();
        pane.setInitialLayout(initial);
        Stage stage = new Stage();
        stage.setScene(new Scene(pane, 900, 600));
        return new Fixture(stage, DockingManager.attach(stage, pane, options));
    }

    private record Fixture(Stage stage, DockingManager manager) { }
}
