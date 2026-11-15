package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Whole-window consent is observed only through the manager and real JavaFX content. */
class CloseFloatingWindowTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @Test
    void earlyVetoStillAsksEveryTabAndDuplicateWindowAndTabRequestsJoin() throws Exception {
        AtomicReference<Fixture> fixture = new AtomicReference<>();
        CompletableFuture<CloseConsent> firstAnswer = new CompletableFuture<>();
        CompletableFuture<CloseConsent> secondAnswer = new CompletableFuture<>();
        List<DockableId> checked = new ArrayList<>();
        AtomicReference<CompletionStage<CloseResult>> result = new AtomicReference<>();
        AtomicReference<CompletionStage<CloseResult>> duplicate = new AtomicReference<>();
        AtomicReference<CompletionStage<CloseResult>> tab = new AtomicReference<>();
        try {
            FxThread.onFx(() -> {
                Fixture f = window();
                fixture.set(f);
                f.first.setCloseCheck(request -> {
                    assertSame(f.floating, request.owner());
                    assertEquals(CloseScope.Kind.FLOATING_WINDOW, request.scope().kind());
                    assertEquals(List.of(f.first.getId(), f.second.getId()), request.scope().dockableIds());
                    checked.add(f.first.getId());
                    return firstAnswer;
                });
                f.second.setCloseCheck(request -> {
                    assertSame(f.floating, request.owner());
                    checked.add(f.second.getId());
                    return secondAnswer;
                });
                result.set(f.manager.requestFloatingWindowClose(f.windowId));
                duplicate.set(f.manager.requestFloatingWindowClose(f.windowId));
                tab.set(f.manager.requestTabClose(f.second.getId()));
                assertEquals(List.of(f.first.getId()), checked);
                firstAnswer.complete(CloseConsent.VETO);
                assertEquals(List.of(f.first.getId(), f.second.getId()), checked);
                assertFalse(result.get().toCompletableFuture().isDone());
                assertTrue(f.floating.isShowing());
                assertSame(f.first, f.manager.findDockable(f.first.getId()).orElseThrow());
                assertSame(f.second, f.manager.findDockable(f.second.getId()).orElseThrow());
                secondAnswer.complete(CloseConsent.ALLOW);
            });
            var veto = assertInstanceOf(CloseResult.Vetoed.class, result.get().toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertEquals(List.of(fixture.get().first.getId()), veto.vetoingIds());
            assertSame(veto, duplicate.get().toCompletableFuture().get());
            assertSame(veto, tab.get().toCompletableFuture().get());
            FxThread.onFx(() -> {
                Fixture f = fixture.get();
                assertNull(f.manager.closingScopeProperty().get());
                assertTrue(f.floating.isShowing());
                ((TextArea) f.second.getContent()).appendText(" still editable");
                assertEquals("Second still editable", ((TextArea) f.second.getContent()).getText());
            });
        } finally {
            hide(fixture.get());
        }
    }

    @Test
    void unanimousWindowCloseRemovesAllContentInOnePublicationBeforeEveryCleanup() throws Exception {
        AtomicReference<Fixture> fixture = new AtomicReference<>();
        try {
            FxThread.onFx(() -> {
                Fixture f = window();
                fixture.set(f);
                var group = f.manager.layout().windows().getLast().areas().getFirst().groups().getFirst().id();
                f.manager.move(f.second.getId(), DockTarget.split(group, javafx.geometry.Side.RIGHT));
                List<DockableId> cleaned = new ArrayList<>();
                RuntimeException failure = new IllegalStateException("First cleanup failed");
                var publications = new java.util.concurrent.atomic.AtomicInteger();
                f.manager.layoutProperty().addListener((observable, old, current) -> {
                    publications.incrementAndGet();
                    assertEquals(1, current.windows().size());
                    assertTrue(f.manager.findDockable(f.first.getId()).isEmpty());
                    assertTrue(f.manager.findDockable(f.second.getId()).isEmpty());
                });
                Runnable verifyCommitted = () -> {
                    assertTrue(Platform.isFxApplicationThread());
                    assertFalse(f.floating.isShowing());
                    assertTrue(f.manager.findDockable(f.first.getId()).isEmpty());
                    assertTrue(f.manager.findDockable(f.second.getId()).isEmpty());
                    assertNotNull(f.manager.closingScopeProperty().get());
                };
                f.first.setOnClosed(() -> {
                    verifyCommitted.run();
                    cleaned.add(f.first.getId());
                    throw failure;
                });
                f.second.setOnClosed(() -> {
                    verifyCommitted.run();
                    cleaned.add(f.second.getId());
                });
                var result = assertInstanceOf(CloseResult.Closed.class,
                        f.manager.requestFloatingWindowClose(f.windowId).toCompletableFuture().getNow(null));
                assertEquals(List.of(f.first.getId(), f.second.getId()), cleaned);
                assertEquals(1, publications.get());
                assertEquals(1, result.cleanupFailures().size());
                assertSame(failure, result.cleanupFailures().getFirst().cause());
                assertNull(f.manager.activeDockable());
                assertNull(f.manager.closingScopeProperty().get());
                assertInstanceOf(CloseResult.NoChange.class,
                        f.manager.requestFloatingWindowClose(f.windowId).toCompletableFuture().getNow(null));
            });
        } finally {
            hide(fixture.get());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 1, 2, 3})
    void failedChecksStillAskLaterTabsAndDoNotUndoCompletedSaves(int failureKind) throws Exception {
        AtomicReference<Fixture> fixture = new AtomicReference<>();
        try {
            FxThread.onFx(() -> {
                Fixture f = window();
                fixture.set(f);
                Dockable last = new Dockable(new DockableId("last"), new TextArea("Last"));
                var group = f.manager.layout().windows().getLast().areas().getFirst().groups().getFirst().id();
                f.manager.open(last, DockTarget.group(group));
                List<String> checked = new ArrayList<>();
                var saved = new AtomicReference<String>();
                RuntimeException failure = new IllegalStateException("Cannot save first");
                f.first.setCloseCheck(request -> {
                    checked.add("first");
                    return switch (failureKind) {
                        case 0 -> throw failure;
                        case 1 -> CompletableFuture.failedFuture(failure);
                        case 2 -> null;
                        default -> CompletableFuture.completedFuture(null);
                    };
                });
                f.second.setCloseCheck(request -> {
                    checked.add("second");
                    saved.set(((TextArea) f.second.getContent()).getText());
                    return CompletableFuture.completedFuture(CloseConsent.ALLOW);
                });
                last.setCloseCheck(request -> {
                    checked.add("last");
                    return CompletableFuture.completedFuture(CloseConsent.VETO);
                });
                f.first.setOnClosed(() -> fail("No resources may be released after a failed check"));
                f.second.setOnClosed(() -> fail("No resources may be released after a late veto"));
                var before = f.manager.layout();
                var veto = assertInstanceOf(CloseResult.Vetoed.class,
                        f.manager.requestFloatingWindowClose(f.windowId).toCompletableFuture().getNow(null));
                assertEquals(List.of("first", "second", "last"), checked);
                assertEquals("Second", saved.get());
                assertEquals(List.of(f.first.getId(), last.getId()), veto.vetoingIds());
                assertEquals(1, veto.checkFailures().size());
                assertEquals(DockFailure.Code.CLOSE_CHECK_FAILED, veto.checkFailures().getFirst().code());
                if (failureKind < 2) assertSame(failure, veto.checkFailures().getFirst().cause());
                assertSame(before, f.manager.layout());
                assertTrue(f.floating.isShowing());
                assertSame(f.second, f.manager.findDockable(f.second.getId()).orElseThrow());
                assertNull(f.manager.closingScopeProperty().get());
            });
        } finally {
            hide(fixture.get());
        }
    }

    @Test
    void windowCloseEventIsConsumedAndIndependentRequestsContinueAfterAVeto() throws Exception {
        AtomicReference<Fixture> fixture = new AtomicReference<>();
        AtomicReference<Stage> otherStage = new AtomicReference<>();
        AtomicReference<CompletionStage<CloseResult>> firstResult = new AtomicReference<>();
        AtomicReference<CompletionStage<CloseResult>> otherResult = new AtomicReference<>();
        CompletableFuture<CloseConsent> firstAnswer = new CompletableFuture<>();
        CompletableFuture<CloseConsent> otherAnswer = new CompletableFuture<>();
        CompletableFuture<Void> otherStarted = new CompletableFuture<>();
        var secondChecks = new java.util.concurrent.atomic.AtomicInteger();
        var otherChecks = new java.util.concurrent.atomic.AtomicInteger();
        try {
            FxThread.onFx(() -> {
                Fixture f = window();
                fixture.set(f);
                f.first.setCloseCheck(request -> firstAnswer);
                f.second.setCloseCheck(request -> {
                    secondChecks.incrementAndGet();
                    return CompletableFuture.completedFuture(CloseConsent.ALLOW);
                });
                Dockable other = new Dockable(new DockableId("other"), new TextArea("Other"));
                f.manager.open(other, DockTarget.floating());
                otherStage.set((Stage) other.getContent().getScene().getWindow());
                DockWindowId otherId = f.manager.layout().windows().getLast().id();
                other.setCloseCheck(request -> {
                    assertSame(otherStage.get(), request.owner());
                    assertEquals(List.of(other.getId()), request.scope().dockableIds());
                    otherChecks.incrementAndGet();
                    otherStarted.complete(null);
                    return otherAnswer;
                });
                f.floating.setOnCloseRequest(event -> fail("The manager filter must consume the close request"));
                javafx.event.Event.fireEvent(f.floating,
                        new javafx.stage.WindowEvent(f.floating, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
                assertTrue(f.floating.isShowing());
                firstResult.set(f.manager.requestFloatingWindowClose(f.windowId));
                otherResult.set(f.manager.requestFloatingWindowClose(otherId));
                assertSame(otherResult.get(), f.manager.requestFloatingWindowClose(otherId));
                assertEquals(0, otherChecks.get());
                assertFalse(otherResult.get().toCompletableFuture().isDone());
            });
            firstAnswer.complete(CloseConsent.VETO);
            assertInstanceOf(CloseResult.Vetoed.class, firstResult.get().toCompletableFuture().get(5, TimeUnit.SECONDS));
            otherStarted.get(5, TimeUnit.SECONDS);
            FxThread.onFx(() -> {
                assertEquals(1, secondChecks.get());
                assertEquals(1, otherChecks.get());
                assertTrue(fixture.get().floating.isShowing());
                assertTrue(otherStage.get().isShowing());
            });
            otherAnswer.complete(CloseConsent.ALLOW);
            assertInstanceOf(CloseResult.Closed.class, otherResult.get().toCompletableFuture().get(5, TimeUnit.SECONDS));
            FxThread.onFx(() -> {
                assertFalse(otherStage.get().isShowing());
                assertTrue(fixture.get().floating.isShowing());
                assertNull(fixture.get().manager.closingScopeProperty().get());
            });
        } finally {
            if (otherStage.get() != null) FxThread.onFx(() -> otherStage.get().hide());
            hide(fixture.get());
        }
    }

    @Test
    void pendingWindowProtectsEveryGroupButAllowsUnrelatedMainWindowDuringCleanup() throws Exception {
        AtomicReference<Fixture> fixture = new AtomicReference<>();
        try {
            FxThread.onFx(() -> {
                Fixture f = window();
                fixture.set(f);
                var group = f.manager.layout().windows().getLast().areas().getFirst().groups().getFirst().id();
                f.manager.move(f.second.getId(), DockTarget.split(group, javafx.geometry.Side.RIGHT));
                var right = f.manager.layout().windows().getLast().areas().getFirst().groups().getLast().id();
                var answer = new CompletableFuture<CloseConsent>();
                f.first.setCloseCheck(request -> answer);
                var result = f.manager.requestFloatingWindowClose(f.windowId);
                Dockable unrelated = new Dockable(new DockableId("unrelated"), new TextArea("Main"));
                assertInstanceOf(ActionResult.Applied.class, f.manager.open(unrelated, DockTarget.area("editors")));
                var moved = assertInstanceOf(MoveCheck.Rejected.class,
                        f.manager.checkMove(f.second.getId(), DockTarget.returnToMain()));
                assertEquals(DockFailure.Code.CLOSE_IN_PROGRESS, moved.failure().code());
                var into = assertInstanceOf(MoveCheck.Rejected.class,
                        f.manager.checkMove(unrelated.getId(), DockTarget.group(right)));
                assertEquals(DockFailure.Code.CLOSE_IN_PROGRESS, into.failure().code());
                Dockable addition = new Dockable(new DockableId("addition"), new TextArea("Addition"));
                var opened = assertInstanceOf(ActionResult.Rejected.class, f.manager.open(addition, DockTarget.group(group)));
                assertEquals(DockFailure.Code.CLOSE_IN_PROGRESS, opened.failure().code());
                assertTrue(f.manager.findDockable(addition.getId()).isEmpty());
                f.first.setOnClosed(() -> {
                    assertNotNull(f.manager.closingScopeProperty().get());
                    assertTrue(f.manager.findDockable(f.second.getId()).isEmpty());
                    assertInstanceOf(ActionResult.Applied.class, f.manager.open(addition, DockTarget.area("editors")));
                });
                answer.complete(CloseConsent.ALLOW);
                var closed = assertInstanceOf(CloseResult.Closed.class, result.toCompletableFuture().getNow(null));
                assertTrue(closed.cleanupFailures().isEmpty());
                assertSame(addition, f.manager.activeDockable());
                assertSame(f.main, addition.getContent().getScene().getWindow());
            });
        } finally {
            hide(fixture.get());
        }
    }

    @Test
    void retiredFloatingStageNoLongerInterceptsAnApplicationCloseRequest() throws Exception {
        AtomicReference<Fixture> fixture = new AtomicReference<>();
        try {
            FxThread.onFx(() -> {
                Fixture f = window();
                fixture.set(f);
                f.manager.move(f.first.getId(), DockTarget.returnToMain());
                f.manager.move(f.second.getId(), DockTarget.returnToMain());
                assertFalse(f.floating.isShowing());
                var delivered = new java.util.concurrent.atomic.AtomicInteger();
                f.floating.setOnCloseRequest(event -> delivered.incrementAndGet());
                javafx.event.Event.fireEvent(f.floating,
                        new javafx.stage.WindowEvent(f.floating, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
                assertEquals(1, delivered.get(), "A released Stage must not retain the manager's close filter");
                assertInstanceOf(CloseResult.NoChange.class,
                        f.manager.requestFloatingWindowClose(f.windowId).toCompletableFuture().getNow(null));
                var rejected = assertInstanceOf(CloseResult.Rejected.class,
                        f.manager.requestFloatingWindowClose(new DockWindowId("main")).toCompletableFuture().getNow(null));
                assertEquals(DockFailure.Code.TARGET_NOT_FOUND, rejected.failure().code());
            });
        } finally {
            hide(fixture.get());
        }
    }

    private static Fixture window() {
        DockPane pane = new DockPane();
        pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
        Stage main = new Stage();
        main.setScene(new Scene(pane, 800, 500));
        DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
        main.show();
        Dockable first = new Dockable(new DockableId("first"), new TextArea("First"));
        Dockable second = new Dockable(new DockableId("second"), new TextArea("Second"));
        manager.open(first, DockTarget.floating());
        var floating = manager.layout().windows().getLast();
        manager.open(second, DockTarget.group(floating.areas().getFirst().groups().getFirst().id()));
        return new Fixture(main, (Stage) first.getContent().getScene().getWindow(), manager, floating.id(), first, second);
    }

    private static void hide(Fixture fixture) throws Exception {
        if (fixture != null) FxThread.onFx(() -> {
            fixture.floating.hide();
            fixture.main.hide();
        });
    }

    private record Fixture(Stage main, Stage floating, DockingManager manager, DockWindowId windowId,
                           Dockable first, Dockable second) { }
}
