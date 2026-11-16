package de.neitzel.fx.example.docking;

import javafx.application.Application;

/** Plain launcher for the application-owned shutdown/save workflow. */
public final class ShutdownDockingMain {
    private ShutdownDockingMain() { }
    public static void main(String[] args) { Application.launch(ShutdownDockingApp.class, args); }
}
