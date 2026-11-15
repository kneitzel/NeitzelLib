package de.neitzel.fx.docking;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.stage.Window;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Keeps consent, live-layout removal and application resource release in order. */
final class DockCloseCoordinator {
    interface Host {
        Optional<Dockable> find(DockableId id);
        Window owner(DockableId id);
        void remove(DockableId id);
    }

    private final Host host;
    private final ReadOnlyObjectWrapper<CloseScope> scope = new ReadOnlyObjectWrapper<>(this, "closingScope");
    private CompletableFuture<CloseResult> completion;
    private CompletionStage<CloseResult> outcome;
    private final LinkedHashMap<DockableId, CompletableFuture<CloseResult>> queued = new LinkedHashMap<>();

    DockCloseCoordinator(Host host) {
        this.host = host;
    }

    ReadOnlyObjectProperty<CloseScope> closingScopeProperty() {
        return scope.getReadOnlyProperty();
    }

    CloseScope scope() {
        return scope.get();
    }

    CompletionStage<CloseResult> requestTabClose(DockableId id) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Docking operations require the JavaFX Application Thread");
        }
        Objects.requireNonNull(id, "id");
        if (completion != null) {
            if (scope.get().dockableIds().contains(id)) return outcome;
            return enqueue(id);
        }
        if (!queued.isEmpty()) {
            CompletionStage<CloseResult> result = enqueue(id);
            Platform.runLater(this::nextQueued);
            return result;
        }
        return begin(id);
    }

    private CompletionStage<CloseResult> enqueue(DockableId id) {
        return queued.computeIfAbsent(id, ignored -> new CompletableFuture<>()).minimalCompletionStage();
    }

    private void nextQueued() {
        if (completion != null || queued.isEmpty()) return;
        var next = queued.pollFirstEntry();
        begin(next.getKey()).whenComplete((result, failure) -> {
            if (failure != null) next.getValue().completeExceptionally(failure);
            else next.getValue().complete(result);
        });
        if (completion == null && !queued.isEmpty()) Platform.runLater(this::nextQueued);
    }

    private CompletionStage<CloseResult> begin(DockableId id) {
        Dockable dockable = host.find(id).orElse(null);
        if (dockable == null) return CompletableFuture.completedFuture(new CloseResult.NoChange());
        CompletableFuture<CloseResult> request = new CompletableFuture<>();
        CompletionStage<CloseResult> result = request.minimalCompletionStage();
        completion = request;
        outcome = result;
        scope.set(new CloseScope(CloseScope.Kind.TAB, List.of(id)));
        try {
            CompletionStage<CloseConsent> answer = dockable.getCloseCheck() == null
                    ? CompletableFuture.completedFuture(CloseConsent.ALLOW)
                    : Objects.requireNonNull(dockable.getCloseCheck().check(new CloseRequest(scope.get(), host.owner(id))),
                    "Close check returned no stage");
            answer.whenComplete((consent, failure) -> onFx(() -> answered(request, dockable, consent, failure)));
        } catch (RuntimeException failure) {
            answered(request, dockable, null, failure);
        }
        return result;
    }

    private void answered(CompletableFuture<CloseResult> request, Dockable dockable,
                          CloseConsent consent, Throwable failure) {
        if (completion != request) return;
        if (failure != null || consent == null) {
            Throwable cause = failure == null ? new IllegalStateException("Close check returned no consent") : failure;
            DockFailure checkFailure = new DockFailure(DockFailure.Code.CLOSE_CHECK_FAILED,
                    dockable.getId(), "Close consent check failed", cause);
            finish(request, new CloseResult.Vetoed(List.of(dockable.getId()), List.of(checkFailure)));
        } else if (consent == CloseConsent.VETO) {
            finish(request, new CloseResult.Vetoed(List.of(dockable.getId()), List.of()));
        } else {
            host.remove(dockable.getId());
            List<DockFailure> cleanupFailures = List.of();
            if (dockable.getOnClosed() != null) {
                try {
                    dockable.getOnClosed().run();
                } catch (RuntimeException cleanup) {
                    cleanupFailures = List.of(new DockFailure(DockFailure.Code.CLEANUP_FAILED,
                            dockable.getId(), "Closed Dockable resource release failed", cleanup));
                }
            }
            finish(request, new CloseResult.Closed(cleanupFailures));
        }
    }

    private void finish(CompletableFuture<CloseResult> request, CloseResult result) {
        completion = null;
        outcome = null;
        scope.set(null);
        request.complete(result);
        if (!queued.isEmpty()) Platform.runLater(this::nextQueued);
    }

    private static void onFx(Runnable action) {
        if (Platform.isFxApplicationThread()) action.run();
        else Platform.runLater(action);
    }
}
