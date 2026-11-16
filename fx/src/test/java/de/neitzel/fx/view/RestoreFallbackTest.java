package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RestoreFallbackTest {
    @BeforeAll static void startJavaFx() throws InterruptedException { FxThread.start(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void changedRulesUseTheDesignatedRelativeFallbackAfterEveryIdentityIsResolved(boolean movingFirst) throws Exception {
        FxThread.onFx(() -> {
            LayoutSnapshot saved = RestoreValidationTest.saved();
            DockableId moved = new DockableId(movingFirst ? "a" : "b");
            DockableId anchor = new DockableId(movingFirst ? "b" : "a");
            DockGroupId receiver = new DockGroupId(movingFirst ? "preview" : "primary");
            DockingOptions options = new DockingOptions();
            options.setFallbackTarget(DockTarget.group(receiver, TabPosition.before(anchor)));
            options.setPlacementRule((dockable, target) -> dockable.getId().equals(moved)
                    && !((ResolvedDockTarget.Insertion) target).groupId().equals(receiver)
                    ? Optional.of(new DockFailure(DockFailure.Code.PLACEMENT_REJECTED, moved, "Saved group is no longer allowed", null))
                    : Optional.empty());
            DockingManager manager = manager(options);
            List<DockableId> requested = new ArrayList<>();
            RestoreResult.Applied applied = assertInstanceOf(RestoreResult.Applied.class, manager.restore(saved, id -> {
                requested.add(id);
                return new DockableResolution.Available(new Dockable(id, new TextArea("Reloaded " + id.value())));
            }));
            assertTrue(applied.missingIds().isEmpty());
            assertEquals(List.of(new DockableId("a"), new DockableId("b")), requested,
                    "Moving to a later-resolved anchor cannot resolve or construct a Dockable twice");
            LayoutSnapshot.Group group = assertInstanceOf(LayoutSnapshot.Group.class,
                    manager.snapshot().windows().getFirst().roots().getFirst().branch());
            assertEquals(List.of(moved, anchor), group.tabs());
            assertEquals(receiver, group.id());
            assertEquals(new DockableId("b"), group.selected());
            assertEquals(new DockableId("b"), manager.activeDockable().getId());
            assertFalse(manager.checkMove(anchor, DockTarget.area(movingFirst ? "primary" : "preview")) instanceof MoveCheck.Rejected,
                    "The emptied saved destination remains a stable alias of the surviving group");
        });
    }

    @ParameterizedTest @ValueSource(strings = {"absent", "missing", "denied", "throws"})
    void anUnusableFallbackAbortsTheWholeRestoreAndKeepsCleanupFailuresSeparate(String fallback) throws Exception {
        FxThread.onFx(() -> {
            LayoutSnapshot saved = RestoreValidationTest.saved();
            RuntimeException policyFailure = new IllegalStateException("Fallback policy failed");
            RuntimeException cleanupFailure = new IllegalArgumentException("Cleanup A failed");
            DockingOptions options = new DockingOptions();
            if (!fallback.equals("absent")) options.setFallbackTarget(DockTarget.area(fallback.equals("missing") ? "unknown" : "primary"));
            options.setPlacementRule((dockable, target) -> {
                if (dockable.getId().value().equals("a")) return Optional.empty();
                if (fallback.equals("throws") && ((ResolvedDockTarget.Insertion) target).groupId().value().equals("primary")) throw policyFailure;
                return Optional.of(new DockFailure(DockFailure.Code.PLACEMENT_REJECTED, dockable.getId(), "No eligible group", null, "app.no-group"));
            });
            DockingManager manager = manager(options);
            var before = manager.layout(); var beforeSnapshot = manager.snapshot();
            AtomicInteger discarded = new AtomicInteger();
            RestoreResult.Rejected rejected = assertInstanceOf(RestoreResult.Rejected.class, manager.restore(saved, id -> {
                Dockable dockable = new Dockable(id, new TextArea(id.value()));
                dockable.setOnDiscarded(() -> { discarded.incrementAndGet(); if (id.value().equals("a")) throw cleanupFailure; });
                return new DockableResolution.Available(dockable);
            }));
            assertEquals(new DockableId("b"), rejected.failure().dockableId());
            assertEquals(switch (fallback) {
                case "missing" -> DockFailure.Code.TARGET_NOT_FOUND;
                case "throws" -> DockFailure.Code.PLACEMENT_CHECK_FAILED;
                default -> DockFailure.Code.PLACEMENT_REJECTED;
            }, rejected.failure().code());
            if (fallback.equals("throws")) assertSame(policyFailure, rejected.failure().cause());
            if (fallback.equals("absent") || fallback.equals("denied")) assertEquals("app.no-group", rejected.failure().messageKey());
            assertEquals(2, discarded.get());
            assertEquals(1, rejected.cleanupFailures().size());
            assertSame(cleanupFailure, rejected.cleanupFailures().getFirst().cause());
            assertSame(before, manager.layout());
            assertEquals(beforeSnapshot, manager.snapshot());
        });
    }

    private static DockingManager manager(DockingOptions options) {
        DockLayout initial = new DockLayout(); initial.setCenter(new DockGroupDefinition("start"));
        DockPane pane = new DockPane(); pane.setInitialLayout(initial);
        Stage stage = new Stage(); stage.setScene(new Scene(pane, 900, 600));
        return DockingManager.attach(stage, pane, options);
    }
}
