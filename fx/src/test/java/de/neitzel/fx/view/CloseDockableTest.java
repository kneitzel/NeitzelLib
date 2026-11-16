package de.neitzel.fx.view;

import de.neitzel.fx.docking.CloseConsent;
import de.neitzel.fx.docking.CloseRequest;
import de.neitzel.fx.docking.CloseResult;
import de.neitzel.fx.docking.CloseScope;
import de.neitzel.fx.docking.DockGroupDefinition;
import de.neitzel.fx.docking.DockLayout;
import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import de.neitzel.fx.docking.ActionResult;
import de.neitzel.fx.docking.DockFailure;
import de.neitzel.fx.docking.MoveCheck;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.event.Event;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Consent and resource lifecycle are tested through the public manager API. */
class CloseDockableTest {
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        FxThread.start();
    }

    @Test
    void duplicateRequestsAwaitOneConsentAndReleaseOnlyAfterActualClose() throws Exception {
        CompletableFuture<CloseConsent> consent = new CompletableFuture<>();
        AtomicReference<Fixture> fixture = new AtomicReference<>();
        AtomicReference<CompletionStage<CloseResult>> first = new AtomicReference<>();
        AtomicReference<CompletionStage<CloseResult>> duplicate = new AtomicReference<>();
        AtomicReference<CloseRequest> request = new AtomicReference<>();
        AtomicInteger checks = new AtomicInteger();
        AtomicInteger released = new AtomicInteger();
        FxThread.onFx(() -> {
            Fixture window = window();
            fixture.set(window);
            window.editor().setCloseCheck(context -> {
                checks.incrementAndGet();
                request.set(context);
                assertTrue(Platform.isFxApplicationThread());
                return consent;
            });
            window.editor().setOnClosed(() -> {
                assertTrue(Platform.isFxApplicationThread());
                released.incrementAndGet();
            });
            first.set(window.manager().requestTabClose(window.editor().getId()));
            duplicate.set(window.manager().requestTabClose(window.editor().getId()));
            assertEquals(1, checks.get());
            assertEquals(0, released.get());
            assertFalse(first.get().toCompletableFuture().isDone());
            assertTrue(window.manager().findDockable(window.editor().getId()).isPresent());
            assertEquals(CloseScope.Kind.TAB, request.get().scope().kind());
            assertEquals(List.of(window.editor().getId()), request.get().scope().dockableIds());
            assertSame(window.stage(), request.get().owner());
            assertEquals(request.get().scope(), window.manager().closingScopeProperty().get());
        });
        consent.complete(CloseConsent.ALLOW);
        assertInstanceOf(CloseResult.Closed.class, first.get().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals(first.get().toCompletableFuture().get(), duplicate.get().toCompletableFuture().get());
        FxThread.onFx(() -> {
            assertEquals(1, released.get());
            assertTrue(fixture.get().manager().findDockable(fixture.get().editor().getId()).isEmpty());
            assertNull(fixture.get().manager().closingScopeProperty().get());
        });
    }

    @Test
    void anotherTabWaitsForTheCurrentConsentEvenWhenItVetoes() throws Exception {
        CompletableFuture<CloseConsent> notesConsent = new CompletableFuture<>();
        CompletableFuture<CloseConsent> projectConsent = new CompletableFuture<>();
        CompletableFuture<Void> projectStarted = new CompletableFuture<>();
        AtomicReference<Fixture> fixture = new AtomicReference<>();
        AtomicReference<CompletionStage<CloseResult>> notesResult = new AtomicReference<>();
        AtomicReference<CompletionStage<CloseResult>> projectResult = new AtomicReference<>();
        AtomicReference<CompletionStage<CloseResult>> projectDuplicate = new AtomicReference<>();
        AtomicInteger projectChecks = new AtomicInteger();
        DockableId projectId = new DockableId("project");
        FxThread.onFx(() -> {
            Fixture window = window();
            fixture.set(window);
            window.editor().setCloseCheck(request -> notesConsent);
            Dockable project = new Dockable(projectId, new TextArea("Project"));
            project.setCloseCheck(request -> {
                projectChecks.incrementAndGet();
                projectStarted.complete(null);
                return projectConsent;
            });
            window.manager().open(project, DockTarget.area("tools"));
            notesResult.set(window.manager().requestTabClose(window.editor().getId()));
            projectResult.set(window.manager().requestTabClose(projectId));
            projectDuplicate.set(window.manager().requestTabClose(projectId));
            assertEquals(0, projectChecks.get());
            assertFalse(projectResult.get().toCompletableFuture().isDone());
        });
        notesConsent.complete(CloseConsent.VETO);
        assertInstanceOf(CloseResult.Vetoed.class, notesResult.get().toCompletableFuture().get(5, TimeUnit.SECONDS));
        projectStarted.get(5, TimeUnit.SECONDS);
        FxThread.onFx(() -> {
            assertEquals(1, projectChecks.get());
            assertEquals(List.of(projectId), fixture.get().manager().closingScopeProperty().get().dockableIds());
            assertTrue(fixture.get().manager().findDockable(fixture.get().editor().getId()).isPresent());
        });
        projectConsent.complete(CloseConsent.ALLOW);
        assertInstanceOf(CloseResult.Closed.class, projectResult.get().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals(projectResult.get().toCompletableFuture().get(), projectDuplicate.get().toCompletableFuture().get());
    }

    @Test
    void closingActiveContentFallsBackToTheMostRecentlyActiveVisibleDockable() throws Exception {
        FxThread.onFx(() -> {
            Fixture window = window();
            Dockable project = new Dockable(new DockableId("project"), new TextArea("Project"));
            Dockable console = new Dockable(new DockableId("console"), new TextArea("Console"));
            window.manager().open(project, DockTarget.area("tools"));
            window.manager().open(console, DockTarget.area("console"));
            window.manager().activate(project.getId());
            window.manager().activate(window.editor().getId());
            assertInstanceOf(CloseResult.Closed.class,
                    window.manager().requestTabClose(window.editor().getId()).toCompletableFuture().getNow(null));
            assertSame(project, window.manager().activeDockable());
        });
    }

    @Test
    void tabCloseControlAndContextMenuBothWaitForConsent() throws Exception {
        FxThread.onFx(() -> {
            Fixture window = window();
            AtomicInteger checks = new AtomicInteger();
            AtomicReference<CompletableFuture<CloseConsent>> answer = new AtomicReference<>();
            window.editor().setCloseCheck(request -> {
                checks.incrementAndGet();
                CompletableFuture<CloseConsent> pending = new CompletableFuture<>();
                answer.set(pending);
                return pending;
            });
            window.pane().applyCss();
            Tab tab = window.pane().lookupAll(".tab-pane").stream().map(TabPane.class::cast)
                    .flatMap(tabs -> tabs.getTabs().stream())
                    .filter(item -> item.getContent() == window.editor().getContent()).findFirst().orElseThrow();
            assertTrue(tab.isClosable());
            Event.fireEvent(tab, new Event(Tab.TAB_CLOSE_REQUEST_EVENT));
            assertEquals(1, checks.get());
            assertTrue(window.manager().findDockable(window.editor().getId()).isPresent());
            answer.get().complete(CloseConsent.VETO);
            assertTrue(window.manager().findDockable(window.editor().getId()).isPresent());
            MenuItem close = tab.getContextMenu().getItems().stream()
                    .filter(item -> "Close".equals(item.getText())).findFirst().orElse(null);
            assertNotNull(close);
            close.fire();
            assertEquals(2, checks.get());
            assertTrue(window.manager().findDockable(window.editor().getId()).isPresent());
            answer.get().complete(CloseConsent.ALLOW);
            assertTrue(window.manager().findDockable(window.editor().getId()).isEmpty());
        });
    }

    @Test
    void failedChecksVetoWithoutReleasingAndALaterRequestCanClose() throws Exception {
        FxThread.onFx(() -> {
            Fixture window = window();
            AtomicInteger released = new AtomicInteger();
            AtomicInteger discarded = new AtomicInteger();
            window.editor().setOnClosed(released::incrementAndGet);
            window.editor().setOnDiscarded(discarded::incrementAndGet);
            IllegalStateException failure = new IllegalStateException("Save failed");
            window.editor().setCloseCheck(request -> CompletableFuture.failedFuture(failure));
            var result = window.manager().requestTabClose(window.editor().getId()).toCompletableFuture().getNow(null);
            CloseResult.Vetoed veto = assertInstanceOf(CloseResult.Vetoed.class, result);
            assertEquals(List.of(window.editor().getId()), veto.vetoingIds());
            assertSame(failure, veto.checkFailures().getFirst().cause());
            assertEquals(DockFailure.Code.CLOSE_CHECK_FAILED, veto.checkFailures().getFirst().code());
            assertTrue(window.manager().findDockable(window.editor().getId()).isPresent());
            assertNull(window.manager().closingScopeProperty().get());
            assertEquals(0, released.get());
            assertEquals(0, discarded.get());

            window.editor().setCloseCheck(request -> { throw failure; });
            assertInstanceOf(CloseResult.Vetoed.class,
                    window.manager().requestTabClose(window.editor().getId()).toCompletableFuture().getNow(null));
            window.editor().setCloseCheck(request -> CompletableFuture.completedFuture(CloseConsent.ALLOW));
            assertInstanceOf(CloseResult.Closed.class,
                    window.manager().requestTabClose(window.editor().getId()).toCompletableFuture().getNow(null));
            assertEquals(1, released.get());
            assertEquals(0, discarded.get());
        });
    }

    @Test
    void cleanupFailureReportsAnActualCloseAndKeepsScopeThroughRelease() throws Exception {
        FxThread.onFx(() -> {
            Fixture window = window();
            IllegalStateException cleanup = new IllegalStateException("Release failed");
            window.editor().setOnClosed(() -> {
                assertTrue(window.manager().findDockable(window.editor().getId()).isEmpty());
                assertEquals(List.of(window.editor().getId()), window.manager().closingScopeProperty().get().dockableIds());
                Dockable duringRelease = new Dockable(new DockableId("during-release"), new TextArea());
                ActionResult.Rejected rejected = assertInstanceOf(ActionResult.Rejected.class,
                        window.manager().open(duringRelease, DockTarget.area("tools")));
                assertEquals(DockFailure.Code.CLOSE_IN_PROGRESS, rejected.failure().code());
                throw cleanup;
            });
            CloseResult.Closed result = assertInstanceOf(CloseResult.Closed.class,
                    window.manager().requestTabClose(window.editor().getId()).toCompletableFuture().getNow(null));
            assertEquals(DockFailure.Code.CLEANUP_FAILED, result.cleanupFailures().getFirst().code());
            assertSame(cleanup, result.cleanupFailures().getFirst().cause());
            assertNull(window.manager().activeDockable());
            assertNull(window.manager().closingScopeProperty().get());
            assertInstanceOf(CloseResult.NoChange.class,
                    window.manager().requestTabClose(window.editor().getId()).toCompletableFuture().getNow(null));
        });
    }

    @Test
    void pendingConsentProtectsItsGroupWhileUnrelatedGroupsRemainUsable() throws Exception {
        FxThread.onFx(() -> {
            Fixture window = window();
            CompletableFuture<CloseConsent> consent = new CompletableFuture<>();
            window.editor().setCloseCheck(request -> consent);
            Dockable project = new Dockable(new DockableId("project"), new TextArea("Project"));
            window.manager().open(project, DockTarget.area("tools"));
            window.manager().requestTabClose(window.editor().getId());
            ActionResult.Rejected move = assertInstanceOf(ActionResult.Rejected.class,
                    window.manager().move(window.editor().getId(), DockTarget.area("tools")));
            assertEquals(DockFailure.Code.CLOSE_IN_PROGRESS, move.failure().code());
            MoveCheck.Rejected inbound = assertInstanceOf(MoveCheck.Rejected.class,
                    window.manager().checkMove(project.getId(), DockTarget.area("editors")));
            assertEquals(DockFailure.Code.CLOSE_IN_PROGRESS, inbound.failure().code());
            Dockable prepared = new Dockable(new DockableId("new-editor"), new TextArea());
            assertInstanceOf(ActionResult.Rejected.class, window.manager().open(prepared, DockTarget.area("editors")));
            assertInstanceOf(ActionResult.Applied.class, window.manager().move(project.getId(), DockTarget.area("console")));
            consent.complete(CloseConsent.VETO);
            assertInstanceOf(ActionResult.Applied.class, window.manager().move(window.editor().getId(), DockTarget.area("tools")));
        });
    }

    @Test
    void closingActiveTabPrefersItsGroupsNewSelection() throws Exception {
        FxThread.onFx(() -> {
            Fixture window = window();
            Dockable second = new Dockable(new DockableId("second"), new TextArea("Second"));
            Dockable project = new Dockable(new DockableId("project"), new TextArea("Project"));
            window.manager().open(second, DockTarget.area("editors"));
            window.manager().open(project, DockTarget.area("tools"));
            window.manager().activate(window.editor().getId());
            assertInstanceOf(CloseResult.Closed.class,
                    window.manager().requestTabClose(window.editor().getId()).toCompletableFuture().getNow(null));
            assertSame(second, window.manager().activeDockable());
        });
    }

    @Test
    void aFailingReplacementFocusTargetCannotStrandAnActualClose() throws Exception {
        AtomicReference<Fixture> fixture = new AtomicReference<>();
        AtomicReference<CompletionStage<CloseResult>> result = new AtomicReference<>();
        AtomicReference<Dockable> replacement = new AtomicReference<>();
        AtomicInteger released = new AtomicInteger();
        FxThread.onFx(() -> {
            Fixture window = window();
            fixture.set(window);
            Dockable second = new Dockable(new DockableId("second"), new TextArea("Second"));
            replacement.set(second);
            window.manager().open(second, DockTarget.area("editors"));
            window.manager().activate(window.editor().getId());
            second.setFocusTarget(() -> { throw new IllegalStateException("Focus target unavailable"); });
            window.editor().setOnClosed(released::incrementAndGet);
            result.set(window.manager().requestTabClose(window.editor().getId()));
        });
        assertInstanceOf(CloseResult.Closed.class, result.get().toCompletableFuture().get(5, TimeUnit.SECONDS));
        FxThread.onFx(() -> {
            assertEquals(1, released.get());
            assertSame(replacement.get(), fixture.get().manager().activeDockable());
            assertTrue(fixture.get().manager().findDockable(fixture.get().editor().getId()).isEmpty());
            assertNull(fixture.get().manager().closingScopeProperty().get());
        });
    }

    private static Fixture window() {
        DockLayout initial = new DockLayout();
        initial.setCenter(new DockGroupDefinition("editors"));
        initial.setLeft(new DockGroupDefinition("console"));
        initial.setRight(new DockGroupDefinition("tools"));
        DockPane pane = new DockPane();
        pane.setInitialLayout(initial);
        Stage stage = new Stage();
        stage.setScene(new Scene(pane, 800, 500));
        DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
        Dockable editor = new Dockable(new DockableId("notes"), new TextArea("Unsaved notes"));
        editor.setTitle("Notes");
        manager.open(editor, DockTarget.area("editors"));
        return new Fixture(stage, pane, manager, editor);
    }

    private record Fixture(Stage stage, DockPane pane, DockingManager manager, Dockable editor) { }
}
