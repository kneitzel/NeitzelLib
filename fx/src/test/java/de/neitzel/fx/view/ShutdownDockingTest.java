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
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Application shutdown asks all windows before a snapshot or any removal. */
class ShutdownDockingTest {
    @BeforeAll static void startJavaFx() throws Exception {
        FxThread.start(); FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @Test void lateVetoLeavesEveryWindowOpenAndSkipsTheShutdownSnapshot() throws Exception {
        FxThread.onFx(() -> {
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            DockGroupDefinition tools = new DockGroupDefinition("tools");
            tools.setCollapsible(true);
            pane.getInitialLayout().setLeft(tools);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 700, 500));
            AtomicInteger saves = new AtomicInteger();
            DockingOptions options = new DockingOptions();
            options.setBeforeShutdown(request -> {
                saves.incrementAndGet();
                return CompletableFuture.completedFuture(ShutdownDecision.PROCEED);
            });
            DockingManager manager = DockingManager.attach(stage, pane, options);
            List<String> asked = new ArrayList<>();
            AtomicInteger closed = new AtomicInteger();
            Dockable editor = new Dockable(new DockableId("editor"), new TextArea("Unsaved editor"));
            Dockable tool = new Dockable(new DockableId("project"), new TextArea("Collapsed project"));
            Dockable floating = new Dockable(new DockableId("floating"), new TextArea("Unsaved float"));
            for (Dockable dockable : List.of(editor, tool, floating)) {
                dockable.setCloseCheck(request -> {
                    assertEquals(CloseScope.Kind.MAIN_WINDOW, request.scope().kind());
                    assertEquals(3, request.scope().dockableIds().size());
                    assertEquals(0, closed.get());
                    assertSame(dockable.getContent().getScene().getWindow(), request.owner());
                    asked.add(dockable.getId().value());
                    return CompletableFuture.completedFuture(dockable == tool ? CloseConsent.VETO : CloseConsent.ALLOW);
                });
                dockable.setOnClosed(closed::incrementAndGet);
            }
            manager.open(editor, DockTarget.area("editors"));
            manager.open(tool, DockTarget.area("tools"));
            manager.open(floating, DockTarget.floating());
            var toolGroup = manager.layout().windows().getFirst().areas().stream()
                    .filter(area -> area.id().value().equals("tools")).findFirst().orElseThrow().groups().getFirst();
            manager.setGroupCollapsed(toolGroup.id(), true);
            stage.show();
            Stage floated = (Stage) floating.getContent().getScene().getWindow();
            try {
                var before = manager.layout();
                var snapshot = manager.snapshot();
                var result = manager.requestMainWindowClose().toCompletableFuture().join();
                assertInstanceOf(CloseResult.Vetoed.class, result);
                assertEquals(List.of("editor", "project", "floating"), asked);
                assertEquals(0, saves.get());
                assertEquals(0, closed.get());
                assertSame(before, manager.layout());
                assertSame(snapshot, manager.snapshot());
                assertTrue(stage.isShowing());
                assertTrue(floated.isShowing());
                assertNull(manager.closingScopeProperty().get());
                assertInstanceOf(ActionResult.Applied.class, manager.move(editor.getId(), DockTarget.area("tools")));
            } finally { floated.hide(); stage.hide(); }
        });
    }
    @Test void asynchronousShutdownSaveKeepsEveryContentOpenThenClosesOnTheFxThread() throws Exception {
        var save = new CompletableFuture<ShutdownDecision>();
        var fixture = new java.util.concurrent.atomic.AtomicReference<ShutdownFixture>();
        var outcome = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletionStage<CloseResult>>();
        AtomicInteger saves = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        try {
            FxThread.onFx(() -> {
                DockingOptions options = new DockingOptions();
                options.setBeforeShutdown(request -> {
                    assertTrue(Platform.isFxApplicationThread());
                    assertEquals(ShutdownReason.MAIN_WINDOW, request.reason());
                    assertEquals(2, request.snapshot().windows().size());
                    assertEquals(2, fixture.get().manager.closingScopeProperty().get().dockableIds().size());
                    assertEquals(0, closed.get());
                    assertTrue(fixture.get().main.isShowing());
                    assertTrue(fixture.get().floating.isShowing());
                    saves.incrementAndGet();
                    return save;
                });
                ShutdownFixture f = shutdownFixture(options);
                fixture.set(f);
                for (Dockable dockable : List.of(f.notes, f.project)) {
                    dockable.setOnClosed(() -> {
                        assertTrue(Platform.isFxApplicationThread());
                        assertNotNull(f.manager.closingScopeProperty().get(), "Scope includes resource release");
                        assertNull(dockable.getContent().getScene());
                        closed.incrementAndGet();
                    });
                }
                var before = f.manager.layout();
                outcome.set(f.manager.requestMainWindowClose());
                assertEquals(1, saves.get());
                assertFalse(outcome.get().toCompletableFuture().isDone());
                assertEquals(0, closed.get());
                assertSame(before, f.manager.layout());
                assertTrue(f.main.isShowing());
                assertTrue(f.floating.isShowing());
                assertEquals(CloseScope.Kind.MAIN_WINDOW, f.manager.closingScopeProperty().get().kind());
                assertInstanceOf(ActionResult.Rejected.class, f.manager.move(f.notes.getId(), DockTarget.area("editors")));
                assertInstanceOf(ActionResult.Rejected.class, f.manager.open(
                        new Dockable(new DockableId("late"), new TextArea()), DockTarget.floating()));
                AtomicInteger factories = new AtomicInteger();
                assertInstanceOf(ActionResult.Rejected.class, f.manager.open(new DockableId("factory"), () -> {
                    factories.incrementAndGet(); return new Dockable(new DockableId("factory"), new TextArea());
                }, DockTarget.area("editors")));
                assertEquals(0, factories.get(), "A global close cannot start creating new content");
            });
            CompletableFuture.runAsync(() -> save.complete(ShutdownDecision.PROCEED)).get();
            FxThread.onFx(() -> {
                ShutdownFixture f = fixture.get();
                assertInstanceOf(CloseResult.Closed.class, outcome.get().toCompletableFuture().join());
                assertEquals(2, closed.get());
                assertEquals(1, saves.get());
                assertFalse(f.main.isShowing());
                assertFalse(f.floating.isShowing());
                assertNull(f.manager.closingScopeProperty().get());
                assertTrue(f.manager.findDockable(f.notes.getId()).isEmpty());
                assertInstanceOf(ActionResult.Rejected.class, f.manager.open(
                        new Dockable(new DockableId("after-close"), new TextArea()), DockTarget.area("editors")));
            });
        } finally {
            if (fixture.get() != null) FxThread.onFx(fixture.get()::close);
        }
    }

    @Test void managerOnlyCloseReleasesTheSamePaneForANewWorkingOverlayManager() throws Exception {
        FxThread.onFx(() -> {
            List<ShutdownReason> reasons = new ArrayList<>();
            DockingOptions options = new DockingOptions();
            options.setBeforeShutdown(request -> {
                reasons.add(request.reason());
                return CompletableFuture.completedFuture(ShutdownDecision.PROCEED);
            });
            ShutdownFixture f = shutdownFixture(options);
            try {
                assertInstanceOf(CloseResult.Closed.class, f.manager.requestManagerClose().toCompletableFuture().join());
                assertEquals(List.of(ShutdownReason.MANAGER_ONLY), reasons);
                assertTrue(f.main.isShowing());
                assertFalse(f.floating.isShowing());
                assertEquals(DockFailure.Code.MANAGER_CLOSED,
                        assertInstanceOf(ActionResult.Rejected.class, f.manager.activate(f.notes.getId())).failure().code());
                assertInstanceOf(CloseResult.NoChange.class, f.manager.requestMainWindowClose().toCompletableFuture().join());
                assertTrue(f.main.isShowing(), "An ended manager no longer owns the application's Stage");

                ((DockGroupDefinition) f.pane.getInitialLayout().getLeft()).setMode(DockGroupMode.OVERLAY);
                DockingManager replacement = DockingManager.attach(f.main, f.pane, new DockingOptions());
                TextArea editor = new TextArea("Replacement editor");
                TextArea project = new TextArea("Replacement project");
                replacement.open(new Dockable(new DockableId("new-editor"), editor), DockTarget.area("editors"));
                replacement.open(new Dockable(new DockableId("new-project"), project), DockTarget.area("tools"));
                f.pane.applyCss(); f.pane.layout();
                assertFalse(group(replacement, "tools").collapsed());
                editor.fireEvent(new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_PRESSED,
                        5, 5, 5, 5, javafx.scene.input.MouseButton.PRIMARY, 1,
                        false, false, false, false, true, false, false, false, false, false,
                        new javafx.scene.input.PickResult(editor, 5, 5)));
                assertTrue(group(replacement, "tools").collapsed(), "Reattached overlay restores the outside-click handler");
                f.main.hide(); f.main.show();
                assertFalse(f.floating.isShowing(), "The ended manager cannot resurrect its old floating Stage");
                assertInstanceOf(CloseResult.Closed.class, replacement.requestManagerClose().toCompletableFuture().join());
                assertTrue(f.main.isShowing());
            } finally { f.close(); }
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(MainWindowCloseMode.class)
    void mainCloseControlHonorsTheCapturedOwnershipModeAndWaitsForConsent(MainWindowCloseMode mode) throws Exception {
        FxThread.onFx(() -> {
            DockingOptions options = new DockingOptions();
            options.setMainWindowCloseMode(mode);
            AtomicInteger saves = new AtomicInteger();
            options.setBeforeShutdown(request -> {
                saves.incrementAndGet();
                return CompletableFuture.completedFuture(ShutdownDecision.PROCEED);
            });
            ShutdownFixture f = shutdownFixture(options);
            options.setMainWindowCloseMode(mode == MainWindowCloseMode.AUTOMATIC
                    ? MainWindowCloseMode.APPLICATION_COORDINATED : MainWindowCloseMode.AUTOMATIC);
            var consent = new CompletableFuture<CloseConsent>();
            f.notes.setCloseCheck(request -> consent);
            AtomicInteger applicationEvents = new AtomicInteger();
            if (mode == MainWindowCloseMode.APPLICATION_COORDINATED) {
                f.main.setOnCloseRequest(event -> {
                    event.consume(); applicationEvents.incrementAndGet();
                    f.manager.requestMainWindowClose();
                });
            }
            try {
                f.main.fireEvent(new javafx.stage.WindowEvent(f.main, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
                assertTrue(f.main.isShowing(), "The main close control waits for consent");
                assertTrue(f.floating.isShowing());
                assertNotNull(f.manager.closingScopeProperty().get());
                assertEquals(0, saves.get());
                consent.complete(CloseConsent.VETO);
                assertTrue(f.main.isShowing());
                assertTrue(f.floating.isShowing());
                assertNull(f.manager.closingScopeProperty().get());
                assertEquals(0, saves.get());
                f.notes.setCloseCheck(request -> CompletableFuture.completedFuture(CloseConsent.ALLOW));
                f.main.fireEvent(new javafx.stage.WindowEvent(f.main, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
                assertFalse(f.floating.isShowing());
                assertEquals(1, saves.get());
                assertEquals(mode == MainWindowCloseMode.APPLICATION_COORDINATED, f.main.isShowing());
                assertEquals(mode == MainWindowCloseMode.APPLICATION_COORDINATED ? 2 : 0, applicationEvents.get());
                if (mode == MainWindowCloseMode.APPLICATION_COORDINATED) f.main.hide();
            } finally { f.close(); }
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"cancel", "throw", "failed"})
    void canceledOrFailedShutdownSaveLeavesTheManagerOperational(String fault) throws Exception {
        FxThread.onFx(() -> {
            RuntimeException failure = new IllegalStateException("Layout storage unavailable");
            DockingOptions options = new DockingOptions();
            options.setBeforeShutdown(request -> {
                if (fault.equals("throw")) throw failure;
                if (fault.equals("failed")) return CompletableFuture.failedFuture(failure);
                return CompletableFuture.completedFuture(ShutdownDecision.CANCEL);
            });
            ShutdownFixture f = shutdownFixture(options);
            AtomicInteger closed = new AtomicInteger();
            f.notes.setOnClosed(closed::incrementAndGet);
            f.project.setOnClosed(closed::incrementAndGet);
            try {
                var before = f.manager.layout();
                var snapshot = f.manager.snapshot();
                CloseResult result = f.manager.requestMainWindowClose().toCompletableFuture().join();
                if (fault.equals("cancel")) assertInstanceOf(CloseResult.Canceled.class, result);
                else {
                    DockFailure diagnostic = assertInstanceOf(CloseResult.Rejected.class, result).failure();
                    assertEquals(DockFailure.Code.SHUTDOWN_CALLBACK_FAILED, diagnostic.code());
                    assertSame(failure, diagnostic.cause());
                }
                assertSame(before, f.manager.layout());
                assertSame(snapshot, f.manager.snapshot());
                assertEquals(0, closed.get());
                assertTrue(f.main.isShowing());
                assertTrue(f.floating.isShowing());
                assertNull(f.manager.closingScopeProperty().get());
                assertInstanceOf(ActionResult.Applied.class, f.manager.move(f.notes.getId(), DockTarget.returnToMain()));
            } finally { f.close(); }
        });
    }

    @Test void shutdownCleanupFailuresStillReleaseEveryDockableAndTheManager() throws Exception {
        FxThread.onFx(() -> {
            ShutdownFixture f = shutdownFixture(new DockingOptions());
            AtomicInteger cleanups = new AtomicInteger();
            RuntimeException failure = new IllegalStateException("Listener release failed");
            f.notes.setOnClosed(() -> { cleanups.incrementAndGet(); throw failure; });
            f.project.setOnClosed(cleanups::incrementAndGet);
            try {
                CloseResult.Closed result = assertInstanceOf(CloseResult.Closed.class,
                        f.manager.requestManagerClose().toCompletableFuture().join());
                assertEquals(2, cleanups.get());
                assertEquals(1, result.cleanupFailures().size());
                assertSame(failure, result.cleanupFailures().getFirst().cause());
                assertFalse(f.floating.isShowing());
                assertTrue(f.main.isShowing());
                assertNull(f.manager.closingScopeProperty().get());
                DockingManager replacement = DockingManager.attach(f.main, f.pane, new DockingOptions());
                assertInstanceOf(CloseResult.Closed.class, replacement.requestManagerClose().toCompletableFuture().join());
                assertInstanceOf(CloseResult.NoChange.class, f.manager.requestManagerClose().toCompletableFuture().join());
                assertEquals(2, cleanups.get());
            } finally { f.close(); }
        });
    }

    private static DockLayoutView.Group group(DockingManager manager, String area) {
        return manager.layout().windows().getFirst().areas().stream().filter(value -> value.id().value().equals(area))
                .findFirst().orElseThrow().groups().getFirst();
    }

    private static ShutdownFixture shutdownFixture(DockingOptions options) {
        List<javafx.stage.Window> existing = List.copyOf(javafx.stage.Window.getWindows());
        DockPane pane = new DockPane();
        pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
        DockGroupDefinition tools = new DockGroupDefinition("tools");
        tools.setCollapsible(true);
        pane.getInitialLayout().setLeft(tools);
        Stage main = new Stage();
        main.setScene(new Scene(pane, 700, 500));
        DockingManager manager = DockingManager.attach(main, pane, options);
        Dockable notes = new Dockable(new DockableId("notes"), new TextArea("Unsaved notes"));
        Dockable project = new Dockable(new DockableId("project"), new TextArea("Project"));
        manager.open(notes, DockTarget.area("editors"));
        manager.open(project, DockTarget.area("tools"));
        manager.setGroupCollapsed(new DockGroupId("tools"), true);
        manager.move(notes.getId(), DockTarget.floating());
        main.show();
        return new ShutdownFixture(main, (Stage) notes.getContent().getScene().getWindow(), pane, manager, notes, project, existing);
    }

    private record ShutdownFixture(Stage main, Stage floating, DockPane pane, DockingManager manager,
                                   Dockable notes, Dockable project, List<javafx.stage.Window> existing) {
        void close() {
            List.copyOf(javafx.stage.Window.getWindows()).stream().filter(window -> !existing.contains(window))
                    .forEach(javafx.stage.Window::hide);
        }
    }

}
