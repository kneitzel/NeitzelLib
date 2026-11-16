package de.neitzel.fx.grid.filter;

import de.neitzel.grid.FilterCondition;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.*;

import java.util.*;
import java.util.function.Consumer;

/** One transactional edit session. All methods run on the FX thread; close always rolls back. */
public final class FilterPopup implements AutoCloseable {
    public enum Outside {
        CANCEL,
        ACCEPT
    }

    public record Options(Outside outside, boolean showOk, boolean showCancel) {
        public Options {
            Objects.requireNonNull(outside);
        }

        public static Options defaults() {
            return new Options(Outside.CANCEL, true, true);
        }
    }

    private final Node opener;
    private final Window owner;
    private final FilterEditor editor;
    private final Consumer<FilterCondition> validate, commit;
    private final Options options;
    private final Popup popup = new Popup();
    private final Label error = new Label();
    private final ReadOnlyObjectWrapper<RuntimeException> disposalFailure =
            new ReadOnlyObjectWrapper<>();
    private final VBox root;
    private boolean closed, reopening;
    private final EventHandler<WindowEvent> ownerHidden = e -> close();
    private final ChangeListener<Scene> detached =
            (o, a, b) -> {
                if (a != b) close();
            };

    public FilterPopup(
            Node opener,
            FilterEditor editor,
            Options options,
            Consumer<FilterCondition> validate,
            Consumer<FilterCondition> commit) {
        requireFx();
        this.opener = Objects.requireNonNull(opener);
        this.editor = Objects.requireNonNull(editor);
        this.options = Objects.requireNonNull(options);
        this.validate = Objects.requireNonNull(validate);
        this.commit = Objects.requireNonNull(commit);
        if (opener.getScene() == null
                || opener.getScene().getWindow() == null
                || !opener.getScene().getWindow().isShowing())
            throw new IllegalArgumentException("Filter opener needs a visible owner window");
        owner = opener.getScene().getWindow();
        Button ok = new Button("OK"),
                cancel = new Button("Cancel"),
                clear = new Button("Clear filter");
        ok.setId("filter-ok");
        cancel.setId("filter-cancel");
        clear.setId("filter-clear");
        ok.setOnAction(e -> accept());
        cancel.setOnAction(e -> cancel());
        clear.setOnAction(e -> finish(true, true, true));
        ok.setVisible(options.showOk());
        ok.setManaged(options.showOk());
        cancel.setVisible(options.showCancel());
        cancel.setManaged(options.showCancel());
        HBox actions = new HBox(8, ok, cancel, clear);
        actions.getStyleClass().add("filter-actions");
        error.setWrapText(true);
        error.getStyleClass().add("filter-validation");
        root = new VBox(8, editor.view(), error, actions);
        root.getStyleClass().addAll("root", "filter-popup");
        root.setFocusTraversable(true);
        root.getStylesheets().add(FilterPopup.class.getResource("filters.css").toExternalForm());
        root.getStylesheets()
                .addAll(de.neitzel.fx.theme.ThemeManager.applicationStyles(opener.getScene()));
        popup.getContent().add(root);
        popup.setAutoHide(true);
        popup.setHideOnEscape(false);
        popup.setConsumeAutoHidingEvents(false);
        popup.setOnAutoHide(e -> outside());
        popup.setOnHidden(
                e ->
                        Platform.runLater(
                                () -> {
                                    if (!closed && !reopening && !popup.isShowing()) close();
                                }));
        root.addEventFilter(
                KeyEvent.KEY_PRESSED,
                e -> {
                    if (e.getCode() != KeyCode.ENTER && e.getCode() != KeyCode.ESCAPE) return;
                    if (nested()) {
                        if (e.getCode() == KeyCode.ESCAPE) {
                            hideNested();
                            e.consume();
                        }
                        return;
                    }
                    e.consume();
                    if (e.getCode() == KeyCode.ENTER) accept();
                    else cancel();
                });
        owner.addEventHandler(WindowEvent.WINDOW_HIDING, ownerHidden);
        opener.sceneProperty().addListener(detached);
    }

    public void show() {
        requireFx();
        if (closed) throw new IllegalStateException("Filter session closed");
        var bounds = opener.localToScreen(opener.getBoundsInLocal());
        if (bounds == null || !owner.isShowing()) {
            close();
            return;
        }
        popup.show(opener, bounds.getMinX(), bounds.getMaxY());
        Platform.runLater(
                () -> {
                    if (!closed && popup.isShowing()) {
                        Node field = editor.view().lookup(".text-field");
                        (field == null || !visible(field) ? root : field).requestFocus();
                    }
                });
    }

    public boolean isOpen() {
        return !closed;
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    public ReadOnlyObjectProperty<RuntimeException> disposalFailureProperty() {
        return disposalFailure.getReadOnlyProperty();
    }

    public String errorMessage() {
        return error.getText();
    }

    public void accept() {
        finish(true, true, false);
    }

    public void cancel() {
        finish(false, true, false);
    }

    /** Used for outside dismissal and an opener re-click; leaves focus with the clicked target. */
    public void outside() {
        finish(options.outside() == Outside.ACCEPT, false, false);
    }

    private void finish(boolean accept, boolean focus, boolean clear) {
        requireFx();
        if (closed) return;
        if (accept) {
            try {
                FilterCondition condition = clear ? null : editor.draft();
                validate.accept(condition);
                commit.accept(condition);
            } catch (RuntimeException invalid) {
                error.setText(
                        Objects.toString(invalid.getMessage(), invalid.getClass().getSimpleName()));
                if (!popup.isShowing() && owner.isShowing()) {
                    reopening = true;
                    Platform.runLater(
                            () -> {
                                if (!closed) show();
                                reopening = false;
                            });
                }
                return;
            }
        }
        close();
        if (focus && owner.isShowing() && opener.getScene() != null) opener.requestFocus();
    }

    private static boolean visible(Node node) {
        for (Node current = node; current != null; current = current.getParent())
            if (!current.isVisible() || current.isDisabled()) return false;
        return true;
    }

    private List<ComboBoxBase<?>> choosers() {
        List<ComboBoxBase<?>> found = new ArrayList<>();
        collect(editor.view(), found);
        return found;
    }

    private static void collect(Node node, List<ComboBoxBase<?>> found) {
        if (node instanceof ComboBoxBase<?> chooser) found.add(chooser);
        if (node instanceof Parent parent)
            for (Node child : parent.getChildrenUnmodifiable()) collect(child, found);
    }

    private boolean nested() {
        return editor.nestedPopupShowing() || choosers().stream().anyMatch(ComboBoxBase::isShowing);
    }

    private void hideNested() {
        editor.closeNestedPopup();
        choosers().forEach(ComboBoxBase::hide);
    }

    public void close() {
        requireFx();
        if (closed) return;
        closed = true;
        owner.removeEventHandler(WindowEvent.WINDOW_HIDING, ownerHidden);
        opener.sceneProperty().removeListener(detached);
        popup.setOnAutoHide(null);
        popup.setOnHidden(null);
        popup.hide();
        try {
            editor.close();
        } catch (RuntimeException failure) {
            disposalFailure.set(failure);
        } finally {
            popup.getContent().clear();
        }
    }

    private static void requireFx() {
        if (!Platform.isFxApplicationThread())
            throw new IllegalStateException("Use the FX application thread");
    }
}
