package de.neitzel.fx.example.docking.mvvm;

import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.example.model.Note;
import de.neitzel.fx.view.LoadedView;
import de.neitzel.fx.view.ViewLoader;
import de.neitzel.injection.ApplicationContext;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import lombok.Getter;

/** Application adapter: one loaded View becomes ordinary Dockable content. */
public final class MvvmEditor implements AutoCloseable {
    @Getter private final Note model = new Note("Edit this note, then move or float its tab.");
    @Getter private final LoadedView view;
    @Getter private final Dockable dockable;
    private final Property<String> text;
    private final Label status;
    private final Button clear;
    private final StringBinding titleBinding;
    private final StringBinding statusBinding;
    private String checkpoint;
    private boolean released;
    private final ReadOnlyBooleanWrapper dirty = new ReadOnlyBooleanWrapper(this, "dirty");
    private final ChangeListener<String> changed = (observable, old, current) -> dirty.set(!java.util.Objects.equals(checkpoint, current));

    @SuppressWarnings("unchecked")
    public MvvmEditor(String id) {
        view = ViewLoader.load(MvvmEditor.class.getResource("MvvmEditor.fxml"), model,
                new ApplicationContext(MvvmEditor.class));
        text = (Property<String>) view.property("text");
        TextArea input = (TextArea) view.getRoot().lookup("#text");
        dockable = new Dockable(new DockableId(id), view.getRoot());
        dockable.setFocusTarget(() -> input);
        checkpoint = model.getText();
        text.addListener(changed);
        titleBinding = Bindings.createStringBinding(() -> "Notes" + (dirty.get() ? " *" : ""), dirty);
        dockable.titleProperty().bind(titleBinding);
        status = (Label) view.getRoot().lookup("#localMessage");
        statusBinding = Bindings.createStringBinding(() -> java.util.Objects.toString(text.getValue(), "").length() + " characters", text);
        status.textProperty().bind(statusBinding);
        ToolBar tools = (ToolBar) view.getRoot().lookup("#localTools");
        clear = (Button) tools.getItems().getFirst();
        clear.setOnAction(event -> input.clear());
        dockable.setOnClosed(this::close);
        dockable.setOnDiscarded(this::close);
    }

    public ReadOnlyBooleanProperty dirtyProperty() { return dirty.getReadOnlyProperty(); }

    /** Records an in-memory checkpoint; this example does not write a file. */
    public void checkpoint() {
        if (released) return;
        checkpoint = text.getValue();
        dirty.set(false);
    }

    /** Releases this adapter's resources, not the LoadedView or global ViewModel identity cache. */
    @Override public void close() {
        if (released) return;
        released = true;
        text.removeListener(changed);
        dockable.titleProperty().unbind();
        titleBinding.dispose();
        status.textProperty().unbind();
        statusBinding.dispose();
        status.setText("Released application listeners");
        clear.setOnAction(null);
        clear.setDisable(true);
    }
}
