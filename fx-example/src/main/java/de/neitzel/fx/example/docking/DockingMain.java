package de.neitzel.fx.example.docking;

import javafx.application.Application;

/** Plain launcher for the docking example; pass --fxml to load the equivalent FXML layout. */
public final class DockingMain {
    private DockingMain() { }

    public static void main(String[] args) {
        Application.launch(DockingApp.class, args);
    }
}
