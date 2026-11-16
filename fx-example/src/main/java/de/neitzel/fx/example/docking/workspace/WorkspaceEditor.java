package de.neitzel.fx.example.docking.workspace;

import de.neitzel.fx.control.StatusBar;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.example.docking.mvvm.MvvmEditor;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import lombok.Getter;

/** Application-owned editor resources. Moving content never rebuilds this adapter. */
final class WorkspaceEditor implements AutoCloseable {
    @Getter private final Dockable dockable;
    @Getter private final TextArea input;
    private final String title;
    private final MvvmEditor mvvm;
    private final StringBinding titleBinding;
    private final StringBinding countBinding;
    private final Label count;
    private final Button clear;
    private String saved;
    private boolean released;

    WorkspaceEditor(String id, StoredWorkspace.Document document, boolean useMvvm) {
        title = document.title(); saved = document.text();
        if (useMvvm) {
            mvvm = new MvvmEditor(id); dockable = mvvm.getDockable();
            input = (TextArea) mvvm.getView().getRoot().lookup("#text");
            input.setText(saved); mvvm.checkpoint();
            mvvm.getView().getRoot().lookup("#localTools").setId(id + "LocalTools");
            mvvm.getView().getRoot().lookup("#localStatus").setId(id + "LocalStatus");
            titleBinding = null; countBinding = null; count = null; clear = null;
        } else {
            mvvm = null; input = new TextArea(saved);
            clear = new Button("Clear " + title); clear.setOnAction(event -> input.clear());
            ToolBar local = new ToolBar(clear); local.setId(id + "LocalTools");
            count = new Label(); countBinding = Bindings.createStringBinding(() -> input.getLength() + " characters", input.textProperty());
            count.textProperty().bind(countBinding);
            StatusBar status = new StatusBar(); status.setId(id + "LocalStatus"); status.getLeftItems().add(count);
            BorderPane root = new BorderPane(input); root.setTop(local); root.setBottom(status);
            dockable = new Dockable(new DockableId(id), root);
            titleBinding = Bindings.createStringBinding(() -> title + (input.getText().equals(saved) ? "" : " *"), input.textProperty());
            dockable.titleProperty().bind(titleBinding);
        }
        input.setId(id + "Text"); dockable.setFocusTarget(() -> input);
    }
    StoredWorkspace.Document document() { return new StoredWorkspace.Document(title, input.getText()); }
    void checkpoint() {
        saved = input.getText();
        if (mvvm != null) mvvm.checkpoint(); else titleBinding.invalidate();
    }
    @Override public void close() {
        if (released) return; released = true;
        if (mvvm != null) mvvm.close();
        else {
            dockable.titleProperty().unbind(); titleBinding.dispose();
            count.textProperty().unbind(); countBinding.dispose(); clear.setOnAction(null);
        }
    }
}
