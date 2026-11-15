package de.neitzel.fx.docking;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Collects every consent before removing content, then releases application resources. */
final class DockCloseCoordinator {
    interface Host {
        Optional<Dockable> find(DockableId id);
        Optional<List<DockableId>> floatingDockables(DockWindowId id);
        Window owner(DockableId id);
        void remove(List<DockableId> ids);
    }

    private final Host host;
    private final ReadOnlyObjectWrapper<CloseScope> scope = new ReadOnlyObjectWrapper<>(this, "closingScope");
    private final LinkedHashMap<RequestKey, Pending> queued = new LinkedHashMap<>();
    private Running running;

    DockCloseCoordinator(Host host) { this.host = host; }

    ReadOnlyObjectProperty<CloseScope> closingScopeProperty() { return scope.getReadOnlyProperty(); }
    CloseScope scope() { return scope.get(); }
    boolean coversWindow(DockWindowId id) {
        return id != null && running != null && id.equals(running.pending.key.windowId);
    }

    CompletionStage<CloseResult> requestTabClose(DockableId id) {
        return request(new RequestKey(Objects.requireNonNull(id, "id"), null));
    }

    CompletionStage<CloseResult> requestFloatingWindowClose(DockWindowId id) {
        return request(new RequestKey(null, Objects.requireNonNull(id, "id")));
    }

    private CompletionStage<CloseResult> request(RequestKey key) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Docking operations require the JavaFX Application Thread");
        }
        if (running != null && (running.pending.key.equals(key)
                || key.tabId != null && scope.get().dockableIds().contains(key.tabId))) {
            return running.pending.outcome;
        }
        if (running != null || !queued.isEmpty()) {
            Pending pending = queued.computeIfAbsent(key, Pending::new);
            if (running == null) Platform.runLater(this::nextQueued);
            return pending.outcome;
        }
        Pending pending = new Pending(key);
        begin(pending);
        return pending.outcome;
    }

    private void nextQueued() {
        if (running != null || queued.isEmpty()) return;
        begin(queued.pollFirstEntry().getValue());
    }

    private void begin(Pending pending) {
        RequestKey key = pending.key;
        List<DockableId> ids = key.windowId == null
                ? host.find(key.tabId).map(ignored -> List.of(key.tabId)).orElse(List.of())
                : host.floatingDockables(key.windowId).orElse(List.of());
        if (ids.isEmpty()) {
            pending.completion.complete(new CloseResult.NoChange());
            if (!queued.isEmpty()) Platform.runLater(this::nextQueued);
            return;
        }
        Running request = new Running(pending, ids.stream().map(id -> host.find(id).orElseThrow()).toList());
        running = request;
        scope.set(new CloseScope(key.windowId == null ? CloseScope.Kind.TAB : CloseScope.Kind.FLOATING_WINDOW, ids));
        askNext(request);
    }

    /** Synchronous answers drain iteratively; asynchronous answers resume on the FX thread. */
    private void askNext(Running request) {
        request.checking = true;
        try {
            while (!request.awaiting && request.index < request.dockables.size()) {
                Dockable dockable = request.dockables.get(request.index);
                request.awaiting = true;
                try {
                    CompletionStage<CloseConsent> answer = dockable.getCloseCheck() == null
                            ? CompletableFuture.completedFuture(CloseConsent.ALLOW)
                            : Objects.requireNonNull(dockable.getCloseCheck().check(
                                    new CloseRequest(scope.get(), host.owner(dockable.getId()))), "Close check returned no stage");
                    answer.whenComplete((consent, failure) -> onFx(() -> answered(request, dockable, consent, failure)));
                } catch (RuntimeException failure) {
                    answered(request, dockable, null, failure);
                }
            }
        } finally {
            request.checking = false;
        }
        if (!request.awaiting && request.index == request.dockables.size()) commit(request);
    }

    private void answered(Running request, Dockable dockable, CloseConsent consent, Throwable failure) {
        if (running != request || !request.awaiting) return;
        if (failure != null || consent == null) {
            Throwable cause = failure == null ? new IllegalStateException("Close check returned no consent") : failure;
            request.failures.add(new DockFailure(DockFailure.Code.CLOSE_CHECK_FAILED,
                    dockable.getId(), "Close consent check failed", cause));
            request.vetoes.add(dockable.getId());
        } else if (consent == CloseConsent.VETO) {
            request.vetoes.add(dockable.getId());
        }
        request.index++;
        request.awaiting = false;
        if (!request.checking) askNext(request);
    }

    private void commit(Running request) {
        if (!request.vetoes.isEmpty()) {
            finish(request, new CloseResult.Vetoed(request.vetoes, request.failures));
            return;
        }
        host.remove(request.dockables.stream().map(Dockable::getId).toList());
        List<DockFailure> cleanupFailures = new ArrayList<>();
        for (Dockable dockable : request.dockables) {
            if (dockable.getOnClosed() == null) continue;
            try {
                dockable.getOnClosed().run();
            } catch (RuntimeException cleanup) {
                cleanupFailures.add(new DockFailure(DockFailure.Code.CLEANUP_FAILED,
                        dockable.getId(), "Closed Dockable resource release failed", cleanup));
            }
        }
        finish(request, new CloseResult.Closed(cleanupFailures));
    }

    private void finish(Running request, CloseResult result) {
        running = null;
        scope.set(null);
        request.pending.completion.complete(result);
        if (!queued.isEmpty()) Platform.runLater(this::nextQueued);
    }

    private static void onFx(Runnable action) {
        if (Platform.isFxApplicationThread()) action.run();
        else Platform.runLater(action);
    }

    private record RequestKey(DockableId tabId, DockWindowId windowId) { }
    private static final class Pending {
        final RequestKey key;
        final CompletableFuture<CloseResult> completion = new CompletableFuture<>();
        final CompletionStage<CloseResult> outcome = completion.minimalCompletionStage();
        Pending(RequestKey key) { this.key = key; }
    }
    private static final class Running {
        final Pending pending;
        final List<Dockable> dockables;
        final List<DockableId> vetoes = new ArrayList<>();
        final List<DockFailure> failures = new ArrayList<>();
        int index;
        boolean awaiting;
        boolean checking;
        Running(Pending pending, List<Dockable> dockables) {
            this.pending = pending;
            this.dockables = dockables;
        }
    }
}
