package de.neitzel.fx.control;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;

/** Saved HTML viewer with offline defaults, isolated SVG images and per-document image permission.
 * All instance methods must be called on the FX thread. Constructing the control performs no I/O.
 * Loading a replacement, clearing or closing cancels pending work and revokes permission.
 */
public final class HtmlViewer extends BorderPane implements AutoCloseable {
    private final WebView browser = new WebView();
    private final ReadOnlyBooleanWrapper preparing = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper loading = new ReadOnlyBooleanWrapper(this, "loading");
    private final ReadOnlyObjectWrapper<Throwable> error = new ReadOnlyObjectWrapper<>(this, "error");
    private final ReadOnlyBooleanWrapper externalImagesAvailable = new ReadOnlyBooleanWrapper(this, "externalImagesAvailable");
    private final ReadOnlyBooleanWrapper externalImagesAllowed = new ReadOnlyBooleanWrapper(this, "externalImagesAllowed");
    private final BooleanProperty mediaButtonVisible = new SimpleBooleanProperty(this, "mediaButtonVisible", true);
    private final StringProperty mediaLoadText = new SimpleStringProperty(this, "mediaLoadText", "Load external media");
    private final StringProperty mediaReloadText = new SimpleStringProperty(this, "mediaReloadText", "Reload external media");
    private final StringProperty mediaTooltipText = new SimpleStringProperty(this, "mediaTooltipText", "Loads external images for this viewer only and contacts their servers.");
    private final StringProperty noExternalMediaText = new SimpleStringProperty(this, "noExternalMediaText", "No resolvable external image addresses found.");
    private HtmlDocument document;
    private Task<HtmlDocument> pending;
    private long generation;
    private boolean closed;

    public HtmlViewer() {
        getStyleClass().add("html-viewer");
        browser.setContextMenuEnabled(false);
        browser.getEngine().setJavaScriptEnabled(false);
        loading.bind(preparing.or(browser.getEngine().getLoadWorker().runningProperty()));
        browser.getEngine().getLoadWorker().stateProperty().addListener((property, old, state) -> {
            if (state == Worker.State.FAILED && !preparing.get() && !closed) {
                error.set(browser.getEngine().getLoadWorker().getException());
            }
        });
        var media = new Button();
        media.getStyleClass().add("html-viewer-media-load");
        media.textProperty().bind(Bindings.when(externalImagesAllowed).then(mediaReloadText).otherwise(mediaLoadText));
        media.disableProperty().bind(loading.or(externalImagesAvailable.not()));
        var tooltip = new Tooltip();
        tooltip.textProperty().bind(Bindings.when(externalImagesAvailable).then(mediaTooltipText).otherwise(noExternalMediaText));
        media.setTooltip(tooltip);
        media.setOnAction(event -> loadExternalImages());
        var toolbar = new HBox(media);
        toolbar.setPadding(new Insets(8, 10, 5, 10));
        toolbar.visibleProperty().bind(mediaButtonVisible);
        toolbar.managedProperty().bind(mediaButtonVisible);
        setTop(toolbar);
        var progress = new ProgressIndicator();
        progress.setMaxSize(36, 36);
        progress.visibleProperty().bind(loading);
        progress.managedProperty().bind(loading);
        var failure = new Label();
        failure.setWrapText(true);
        failure.textProperty().bind(error.map(problem -> Objects.toString(problem.getMessage(), problem.getClass().getSimpleName())));
        failure.visibleProperty().bind(error.isNotNull());
        failure.managedProperty().bind(failure.visibleProperty());
        setCenter(new StackPane(browser, progress, failure));
    }

    /** Prepare a file on an owned virtual thread, then display it offline. */
    public void load(Path file) {
        Objects.requireNonNull(file, "file");
        prepare(() -> HtmlDocument.read(file));
    }

    public void loadHtml(String html) { loadHtml(html, null); }

    /** Prepare HTML off the FX thread; source is an optional HTTP(S) base, never a URL to fetch. */
    public void loadHtml(String html, URI source) {
        Objects.requireNonNull(html, "html");
        prepare(() -> HtmlDocument.parse(html, source));
    }

    /** Display an immutable document already prepared by the caller. Permission starts revoked. */
    public void display(HtmlDocument value) {
        requireOpen();
        Objects.requireNonNull(value, "document");
        clear();
        show(value);
    }

    private void show(HtmlDocument value) {
        document = value;
        externalImagesAvailable.set(value.hasExternalImages());
        browser.getEngine().loadContent(value.offlineHtml());
    }

    @FunctionalInterface private interface Preparation { HtmlDocument run() throws Exception; }

    private void prepare(Preparation work) {
        requireOpen();
        clear();
        long request = generation;
        preparing.set(true);
        var task = new Task<HtmlDocument>() {
            @Override protected HtmlDocument call() throws Exception { return work.run(); }
        };
        pending = task;
        task.setOnSucceeded(event -> {
            if (closed || request != generation) return;
            pending = null;
            show(task.getValue());
            preparing.set(false);
        });
        task.setOnFailed(event -> {
            if (closed || request != generation) return;
            pending = null;
            error.set(task.getException());
            preparing.set(false);
        });
        Thread.ofVirtual().name("html-viewer-prepare").start(task);
    }

    /** Explicitly grants HTTP(S) image requests for the currently displayed document only. */
    public void loadExternalImages() {
        requireOpen();
        if (document == null || !externalImagesAvailable.get()) return;
        error.set(null);
        externalImagesAllowed.set(true);
        browser.getEngine().loadContent(document.externalImagesHtml());
    }

    public void clear() {
        requireOpen();
        generation++;
        if (pending != null) { pending.cancel(true); pending = null; }
        preparing.set(false);
        document = null;
        externalImagesAvailable.set(false);
        externalImagesAllowed.set(false);
        error.set(null);
        browser.getEngine().getLoadWorker().cancel();
        browser.getEngine().load(null);
    }

    @Override public void close() {
        requireFxThread();
        if (closed) return;
        clear();
        closed = true;
    }

    private void requireOpen() {
        requireFxThread();
        if (closed) throw new IllegalStateException("HtmlViewer is closed.");
    }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("HtmlViewer requires the JavaFX application thread.");
    }

    public ReadOnlyBooleanProperty loadingProperty() { return loading.getReadOnlyProperty(); }
    public boolean isLoading() { return loading.get(); }
    public ReadOnlyObjectProperty<Throwable> errorProperty() { return error.getReadOnlyProperty(); }
    public Throwable getError() { return error.get(); }
    public ReadOnlyBooleanProperty externalImagesAvailableProperty() { return externalImagesAvailable.getReadOnlyProperty(); }
    public boolean isExternalImagesAvailable() { return externalImagesAvailable.get(); }
    public ReadOnlyBooleanProperty externalImagesAllowedProperty() { return externalImagesAllowed.getReadOnlyProperty(); }
    public boolean isExternalImagesAllowed() { return externalImagesAllowed.get(); }
    public BooleanProperty mediaButtonVisibleProperty() { return mediaButtonVisible; }
    public boolean isMediaButtonVisible() { return mediaButtonVisible.get(); }
    public void setMediaButtonVisible(boolean value) { mediaButtonVisible.set(value); }
    public StringProperty mediaLoadTextProperty() { return mediaLoadText; }
    public String getMediaLoadText() { return mediaLoadText.get(); }
    public void setMediaLoadText(String value) { mediaLoadText.set(value); }
    public StringProperty mediaReloadTextProperty() { return mediaReloadText; }
    public String getMediaReloadText() { return mediaReloadText.get(); }
    public void setMediaReloadText(String value) { mediaReloadText.set(value); }
    public StringProperty mediaTooltipTextProperty() { return mediaTooltipText; }
    public String getMediaTooltipText() { return mediaTooltipText.get(); }
    public void setMediaTooltipText(String value) { mediaTooltipText.set(value); }
    public StringProperty noExternalMediaTextProperty() { return noExternalMediaText; }
    public String getNoExternalMediaText() { return noExternalMediaText.get(); }
    public void setNoExternalMediaText(String value) { noExternalMediaText.set(value); }
}
