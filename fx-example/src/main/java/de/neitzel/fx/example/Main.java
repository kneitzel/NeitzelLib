package de.neitzel.fx.example;

import javafx.application.Application;

/**
 * Launches the example without extending {@link Application}.
 * <p>
 * JavaFX on the classpath is started from a class that is not itself an {@link Application}.
 */
public final class Main {

    /**
     * Prevents instantiation. The class is only a launch entry point.
     */
    private Main() {
    }

    /**
     * Starts the person desk.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        Application.launch(DeskApp.class, args);
    }
}
