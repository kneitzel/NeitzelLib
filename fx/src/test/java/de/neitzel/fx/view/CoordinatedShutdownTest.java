package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Overlapping close entry points share consent and serialize global shutdown. */
class CoordinatedShutdownTest {
    @BeforeAll static void startJavaFx() throws Exception {
        FxThread.start(); FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void mainShutdownWaitsForFloatingCloseAndDoesNotRepeatAVetoedRequest(boolean allow) throws Exception {
        var fixture = new AtomicReference<Fixture>();
        var consent = new CompletableFuture<CloseConsent>();
        var floatingResult = new AtomicReference<CompletionStage<CloseResult>>();
        var mainResult = new AtomicReference<CompletionStage<CloseResult>>();
        List<String> asked = new ArrayList<>();
        List<String> closed = new ArrayList<>();
        List<CloseScope.Kind> scopes = new ArrayList<>();
        AtomicInteger saves = new AtomicInteger();
        try {
            FxThread.onFx(() -> {
                DockingOptions options = new DockingOptions();
                options.setBeforeShutdown(request -> {
                    assertTrue(Platform.isFxApplicationThread());
                    assertEquals(ShutdownReason.MAIN_WINDOW, request.reason());
                    assertEquals(allow ? 2 : 3, request.snapshot().windows().size());
                    saves.incrementAndGet();
                    return CompletableFuture.completedFuture(ShutdownDecision.PROCEED);
                });
                Fixture f = fixture(options); fixture.set(f);
                f.manager.closingScopeProperty().addListener((observable, old, current) -> {
                    if (current != null) scopes.add(current.kind());
                });
                for (Dockable dockable : List.of(f.mainContent, f.first, f.second)) {
                    dockable.setCloseCheck(request -> {
                        assertTrue(Platform.isFxApplicationThread());
                        assertSame(dockable.getContent().getScene().getWindow(), request.owner());
                        asked.add(dockable.getId().value());
                        return dockable == f.first ? consent : CompletableFuture.completedFuture(CloseConsent.ALLOW);
                    });
                    dockable.setOnClosed(() -> closed.add(dockable.getId().value()));
                }
                floatingResult.set(f.manager.requestFloatingWindowClose(f.firstWindow));
                assertSame(floatingResult.get(), f.manager.requestFloatingWindowClose(f.firstWindow));
                assertSame(floatingResult.get(), f.manager.requestTabClose(f.first.getId()));
                mainResult.set(f.manager.requestMainWindowClose());
                assertSame(mainResult.get(), f.manager.requestMainWindowClose());
                assertFalse(mainResult.get().toCompletableFuture().isDone());
                assertEquals(List.of("first"), asked);
                assertEquals(CloseScope.Kind.FLOATING_WINDOW, f.manager.closingScopeProperty().get().kind());
                assertEquals(0, saves.get());
                assertTrue(closed.isEmpty());
            });
            CompletableFuture.runAsync(() -> consent.complete(allow ? CloseConsent.ALLOW : CloseConsent.VETO)).get();
            // The queue continuation is scheduled by the FX-thread completion; drain two ordinary FX turns.
            FxThread.onFx(() -> { });
            FxThread.onFx(() -> {
                Fixture f = fixture.get();
                assertTrue(mainResult.get().toCompletableFuture().isDone(), "The queued shutdown must have settled");
                if (allow) {
                    assertInstanceOf(CloseResult.Closed.class, floatingResult.get().toCompletableFuture().join());
                    assertInstanceOf(CloseResult.Closed.class, mainResult.get().toCompletableFuture().join());
                    assertEquals(List.of("first", "main", "second"), asked);
                    assertEquals(List.of("first", "main", "second"), closed);
                    assertEquals(1, saves.get());
                    assertFalse(f.stage.isShowing());
                } else {
                    CloseResult veto = floatingResult.get().toCompletableFuture().join();
                    assertInstanceOf(CloseResult.Vetoed.class, veto);
                    assertEquals(veto, mainResult.get().toCompletableFuture().join());
                    assertEquals(List.of("first"), asked, "A waiting quit must not ask again after the floating veto");
                    assertEquals(List.of(CloseScope.Kind.FLOATING_WINDOW), scopes);
                    assertTrue(closed.isEmpty());
                    assertEquals(0, saves.get());
                    assertEquals(3, f.manager.layout().windows().size());
                    assertTrue(f.stage.isShowing());
                    f.first.setCloseCheck(request -> {
                        asked.add("first"); return CompletableFuture.completedFuture(CloseConsent.ALLOW);
                    });
                    asked.clear();
                    assertInstanceOf(CloseResult.Closed.class, f.manager.requestMainWindowClose().toCompletableFuture().join());
                    assertEquals(List.of("main", "first", "second"), asked, "A new explicit quit gets fresh consent");
                    assertEquals(1, saves.get());
                }
                assertNull(f.manager.closingScopeProperty().get());
            });
        } finally {
            if (fixture.get() != null) FxThread.onFx(fixture.get()::close);
        }
    }

    @org.junit.jupiter.api.Test
    void duplicatesShareTheFinalPublicationAndAQueuedQuitCannotSaveAnEndedManagerAgain() throws Exception {
        var fixture = new AtomicReference<Fixture>();
        var save = new CompletableFuture<ShutdownDecision>();
        var managerResult = new AtomicReference<CompletionStage<CloseResult>>();
        var queuedQuit = new AtomicReference<CompletionStage<CloseResult>>();
        List<CompletionStage<CloseResult>> duplicates = new ArrayList<>();
        List<CompletionStage<CloseResult>> coveredTabs = new ArrayList<>();
        List<ShutdownReason> saves = new ArrayList<>();
        try {
            FxThread.onFx(() -> {
                DockingOptions options = new DockingOptions();
                options.setBeforeShutdown(request -> { saves.add(request.reason()); return save; });
                Fixture f = fixture(options); fixture.set(f);
                f.manager.layoutProperty().addListener((observable, old, current) -> {
                    boolean empty = current.windows().stream().flatMap(window -> window.areas().stream())
                            .flatMap(area -> area.groups().stream()).allMatch(group -> group.dockableIds().isEmpty());
                    if (empty && f.manager.closingScopeProperty().get() != null) {
                        duplicates.add(f.manager.requestManagerClose());
                        coveredTabs.add(f.manager.requestTabClose(f.first.getId()));
                    }
                });
                managerResult.set(f.manager.requestManagerClose());
                queuedQuit.set(f.manager.requestMainWindowClose());
                assertFalse(queuedQuit.get().toCompletableFuture().isDone());
                assertEquals(List.of(ShutdownReason.MANAGER_ONLY), saves);
            });
            CompletableFuture.runAsync(() -> save.complete(ShutdownDecision.PROCEED)).get();
            FxThread.onFx(() -> { });
            FxThread.onFx(() -> {
                Fixture f = fixture.get();
                assertTrue(managerResult.get().toCompletableFuture().isDone());
                assertInstanceOf(CloseResult.Closed.class, managerResult.get().toCompletableFuture().join());
                assertTrue(duplicates.size() >= 2, "Observe both content removal and final area release");
                duplicates.forEach(answer -> assertSame(managerResult.get(), answer,
                        "A duplicate shares the pending outcome until the scope ends"));
                coveredTabs.forEach(answer -> assertSame(managerResult.get(), answer));
                assertTrue(queuedQuit.get().toCompletableFuture().isDone());
                assertInstanceOf(CloseResult.NoChange.class, queuedQuit.get().toCompletableFuture().join());
                assertEquals(List.of(ShutdownReason.MANAGER_ONLY), saves, "An ended manager cannot save an empty replacement snapshot");
                assertTrue(f.stage.isShowing(), "The ended manager no longer owns main-window closure");
                assertNull(f.manager.closingScopeProperty().get());
            });
        } finally {
            if (fixture.get() != null) FxThread.onFx(fixture.get()::close);
        }
    }

    private static Fixture fixture(DockingOptions options) {
        List<Window> existing = List.copyOf(Window.getWindows());
        DockPane pane = new DockPane(); pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
        Stage stage = new Stage(); stage.setScene(new Scene(pane, 700, 500));
        DockingManager manager = DockingManager.attach(stage, pane, options);
        Dockable main = new Dockable(new DockableId("main"), new TextArea("Main"));
        Dockable first = new Dockable(new DockableId("first"), new TextArea("First"));
        Dockable second = new Dockable(new DockableId("second"), new TextArea("Second"));
        manager.open(main, DockTarget.area("editors"));
        manager.open(first, DockTarget.floating());
        DockWindowId firstWindow = manager.layout().windows().stream().filter(window -> !window.main()).findFirst().orElseThrow().id();
        manager.open(second, DockTarget.floating());
        stage.show();
        return new Fixture(stage, manager, main, first, second, firstWindow, existing);
    }

    private record Fixture(Stage stage, DockingManager manager, Dockable mainContent, Dockable first,
                           Dockable second, DockWindowId firstWindow, List<Window> existing) {
        void close() { List.copyOf(Window.getWindows()).stream().filter(window -> !existing.contains(window)).forEach(Window::hide); }
    }
}
