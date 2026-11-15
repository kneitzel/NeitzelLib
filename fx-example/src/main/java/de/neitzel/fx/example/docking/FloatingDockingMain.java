package de.neitzel.fx.example.docking;

import javafx.application.Application;

/** Plain Java launcher for the floating editor example. */
public final class FloatingDockingMain {
    private FloatingDockingMain() { }

    public static void main(String[] args) {
        Application.launch(FloatingDockingApp.class, args);
    }
}
