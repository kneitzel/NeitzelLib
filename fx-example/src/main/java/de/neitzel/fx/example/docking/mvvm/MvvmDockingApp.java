package de.neitzel.fx.example.docking.mvvm;

import javafx.application.Application;
import javafx.stage.Stage;

/** Visible example of optional MVVM content hosted by the plain docking API. */
public final class MvvmDockingApp extends Application {
    private MvvmDockingWorkspace workspace;

    @Override public void start(Stage stage) {
        workspace = new MvvmDockingWorkspace(stage);
        stage.setTitle("NeitzelLib — MVVM editor and local/global bars");
        stage.show();
    }

    @Override public void stop() {
        if (workspace != null) workspace.close();
    }
}
