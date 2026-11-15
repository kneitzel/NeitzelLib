package de.neitzel.fx.example.docking.mvvm;

import javafx.application.Application;

/** Classpath launcher that does not extend JavaFX Application. */
public final class MvvmDockingMain {
    private MvvmDockingMain() { }

    public static void main(String[] args) { Application.launch(MvvmDockingApp.class, args); }
}
