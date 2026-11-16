package de.neitzel.fx.example.docking.workspace;

import javafx.application.Application;

/** Launcher: --store=/absolute/path/workspace.json [java] [mvvm]. */
public final class ReferenceWorkspaceMain {
    private ReferenceWorkspaceMain() { }
    public static void main(String[] args) { Application.launch(ReferenceWorkspaceApp.class, args); }
}
