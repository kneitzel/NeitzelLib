package de.neitzel.fx.view;

import de.neitzel.grid.*;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;
import java.util.concurrent.*;

/** One FX view's opt-in storage adapter. Closing it never discards accepted application saves. */
public final class GridPersistenceAttachment<K, T> implements AutoCloseable {
    private final DataGrid<K, T> grid;
    private final GridApplicationConfiguration application;
    private final GridSetupStore.Key key;
    private final Label status = new Label("Grid settings not loaded");
    private final Button retry = new Button("Retry settings"),
            reload = new Button("Reload saved setup"),
            reset = new Button("Reset setup"),
            overwrite = new Button("Overwrite saved setup");
    private final VBox feedback = new VBox(4);
    private final HBox recovery = new HBox(8);
    private final List<MenuItem> menu;
    private final ChangeListener<Number> commits;
    private final AutoCloseable observation;
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private boolean initialized, closed, restoreFailed;
    private long generation, ownSave = -1, operationGeneration = -1;
    private Throwable restoreError;

    GridPersistenceAttachment(
            DataGrid<K, T> grid, GridApplicationConfiguration application, GridSetupStore.Key key) {
        this.grid = grid;
        this.application = application;
        this.key = key;
        status.setWrapText(true);
        status.getStyleClass().add("grid-persistence-status");
        status.setAccessibleText("Grid settings status");
        recovery.getChildren().addAll(retry, reload, reset, overwrite);
        recovery.setVisible(false);
        recovery.setManaged(false);
        feedback.getChildren().addAll(status, recovery);
        MenuItem reloadItem = new MenuItem("Reload saved setup"),
                resetItem = new MenuItem("Reset setup");
        reloadItem.setOnAction(e -> loadAsync());
        resetItem.setOnAction(e -> resetAsync());
        menu = List.of(reloadItem, resetItem);
        grid.addPersistenceControls(feedback, menu);
        retry.setOnAction(e -> {
            if (!initialized || restoreFailed) loadAsync();
            else application.retryAsync(key);
        });
        reload.setOnAction(e -> loadAsync());
        reset.setOnAction(e -> resetAsync());
        overwrite.setOnAction(e -> overwriteAsync());
        commits =
                (o, b, a) -> {
                    if (initialized && !closed) {
                        try {
                            ownSave = application.accept(key, grid.snapshotSetup(), this);
                        } catch (RuntimeException failure) {
                            fail(new CompletableFuture<>(), failure);
                        }
                    }
                };
        grid.committedRevisionProperty().addListener(commits);
        observation =
                application.observe(
                        key,
                        state ->
                                Platform.runLater(() -> {
                                    if (!closed) show(state);
                                }));
    }

    public CompletionStage<Void> ready() {
        return ready;
    }

    public CompletionStage<Void> loadAsync() {
        requireFx();
        check();
        long token = ++generation;
        long discarded = ownSave;
        restoreFailed = false;
        restoreError = null;
        CompletableFuture<Void> answer = new CompletableFuture<>();
        CompletionStage<GridApplicationConfiguration.Loaded> loading;
        try {
            loading = application.loadAsync(key);
        } catch (RuntimeException failure) {
            fail(answer, failure);
            return answer;
        }
        loading.whenComplete((loaded, error) ->
                Platform.runLater(() -> {
                    if (!live(token)) {
                        answer.completeExceptionally(
                                new CancellationException("Attachment closed or superseded"));
                        return;
                    }
                    if (error != null) {
                        fail(answer, error);
                        return;
                    }
                    GridSetup setup =
                            loaded.stored().setup() == null
                                    ? grid.developerDefaults()
                                    : loaded.stored().setup();
                    CompletionStage<Void> restore = grid.restoreSetupAsync(setup);
                    operationGeneration = grid.stateProperty().get().generation();
                    restore.whenComplete((ok, failure) ->
                            Platform.runLater(() -> {
                                if (!live(token)) {
                                    answer.completeExceptionally(
                                            new CancellationException(
                                                    "Attachment closed or superseded"));
                                    return;
                                }
                                if (failure != null) {
                                    fail(answer, failure);
                                    return;
                                }
                                try {
                                    application.adopt(loaded, discarded);
                                } catch (RuntimeException adoptionFailure) {
                                    fail(answer, adoptionFailure);
                                    return;
                                }
                                initialized = true;
                                restoreFailed = false;
                                restoreError = null;
                                show(application.state(key));
                                ready.complete(null);
                                answer.complete(null);
                            }));
                }));
        return answer;
    }

    public CompletionStage<Void> resetAsync() {
        requireFx();
        check();
        long token = ++generation;
        boolean recover = !initialized || restoreFailed;
        CompletableFuture<Void> answer = new CompletableFuture<>();
        CompletionStage<Void> restore = grid.restoreSetupAsync(grid.developerDefaults());
        operationGeneration = grid.stateProperty().get().generation();
        restore.whenComplete((ok, error) ->
                Platform.runLater(() -> {
                    if (!live(token)) {
                        answer.completeExceptionally(
                                new CancellationException("Attachment closed or superseded"));
                        return;
                    }
                    if (error != null) {
                        fail(answer, error);
                        return;
                    }
                    initialized = true;
                    restoreFailed = false;
                    restoreError = null;
                    try {
                        ownSave =
                                recover
                                        ? application.acceptOverwrite(
                                                key, grid.snapshotSetup(), this)
                                        : application.accept(key, grid.snapshotSetup(), this);
                    } catch (RuntimeException failure) {
                        fail(answer, failure);
                        return;
                    }
                    ready.complete(null);
                    answer.complete(null);
                }));
        return answer;
    }

    public CompletionStage<Void> overwriteAsync() {
        requireFx();
        check();
        if (!initialized)
            throw new IllegalStateException("Initialize or reset the view before saving");
        ownSave = application.acceptOverwrite(key, grid.snapshotSetup(), this);
        return application.retryAsync(key);
    }

    private void fail(CompletableFuture<Void> future, Throwable failure) {
        restoreFailed = true;
        restoreError = unwrap(failure);
        status.setText("Grid settings unavailable: " + restoreError.getMessage());
        recovery.setVisible(true);
        recovery.setManaged(true);
        overwrite.setDisable(!initialized);
        future.completeExceptionally(restoreError);
    }

    private void show(GridApplicationConfiguration.State state) {
        if (restoreFailed) {
            status.setText("Grid settings unavailable: " + restoreError.getMessage());
            return;
        }
        status.setText(
                switch (state.phase()) {
                    case IDLE -> "Grid settings not loaded";
                    case LOADING -> "Loading grid settings…";
                    case PENDING -> "Grid settings have unsaved changes";
                    case SAVING -> "Saving grid settings…";
                    case SAVED -> "Grid settings saved";
                    case CLOSED -> "Grid settings closed";
                    case ERROR, CONFLICT -> "Grid settings: " + state.error().getMessage();
                });
        boolean failed =
                state.phase() == GridApplicationConfiguration.Phase.ERROR
                        || state.phase() == GridApplicationConfiguration.Phase.CONFLICT;
        recovery.setVisible(failed);
        recovery.setManaged(failed);
        overwrite.setDisable(!initialized);
    }

    private boolean live(long token) {
        return !closed && generation == token;
    }

    private void check() {
        if (closed) throw new IllegalStateException("Attachment is closed");
    }

    private static void requireFx() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Use FX thread");
    }

    private static Throwable unwrap(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException)
                && failure.getCause() != null) failure = failure.getCause();
        return failure;
    }

    @Override
    public void close() {
        requireFx();
        if (closed) return;
        closed = true;
        generation++;
        grid.committedRevisionProperty().removeListener(commits);
        try {
            observation.close();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
        grid.cancelOwnedRestore(operationGeneration);
        grid.removePersistenceControls(feedback, menu);
        grid.detached(this);
        if (!ready.isDone())
            ready.completeExceptionally(new CancellationException("Attachment closed"));
    }
}
