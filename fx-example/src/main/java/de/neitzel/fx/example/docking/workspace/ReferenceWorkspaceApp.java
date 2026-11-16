package de.neitzel.fx.example.docking.workspace;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import java.nio.file.Path;
import java.util.Optional;

/** Reads application data off the FX thread, then restores before showing any workspace windows. */
public final class ReferenceWorkspaceApp extends Application {
    private ReferenceWorkspace workspace;
    private volatile boolean stopped;
    @Override public void start(Stage stage) {
        String location = getParameters().getNamed().get("store");
        if (location == null || location.isBlank()) {
            stage.setScene(new Scene(new Label("Supply --store=/absolute/path/workspace.json; optional: java, mvvm"), 720, 100));
            stage.show(); return;
        }
        WorkspaceStore store = new WorkspaceStore(Path.of(location));
        boolean fxml = !getParameters().getUnnamed().contains("java");
        boolean mvvm = getParameters().getUnnamed().contains("mvvm");
        Thread.ofVirtual().name("workspace-startup").start(() -> {
            try {
                Optional<StoredWorkspace> saved = store.read();
                Platform.runLater(() -> {
                    if (stopped) return;
                    try { workspace = new ReferenceWorkspace(stage, fxml, mvvm, store, saved); }
                    catch (Exception failure) { showFailure(stage, failure); }
                });
            } catch (Exception failure) { Platform.runLater(() -> { if (!stopped) showFailure(stage, failure); }); }
        });
    }
    private void showFailure(Stage stage, Exception failure) {
        stage.setScene(new Scene(new Label("Workspace was not loaded: " + failure.getMessage()
                + "\nThe existing file has not been changed."), 850, 120)); stage.show();
    }
    @Override public void stop() { stopped = true; if (workspace != null) workspace.close(); }
}
