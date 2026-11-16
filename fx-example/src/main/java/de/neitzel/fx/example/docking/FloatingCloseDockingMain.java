package de.neitzel.fx.example.docking;

import javafx.application.Application;

/** Plain Java launcher for the floating-window consent example. */
public final class FloatingCloseDockingMain {
    private FloatingCloseDockingMain() { }

    public static void main(String[] args) {
        Application.launch(FloatingCloseDockingApp.class, args);
    }
}
