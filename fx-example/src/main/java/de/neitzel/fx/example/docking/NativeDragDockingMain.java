package de.neitzel.fx.example.docking;

import javafx.application.Application;

/** Plain Java launcher for native cross-window tab dragging. */
public final class NativeDragDockingMain {
    private NativeDragDockingMain() { }

    public static void main(String[] args) {
        Application.launch(NativeDragDockingApp.class, args);
    }
}
