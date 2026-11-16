package de.neitzel.fx.example.bars;

import javafx.application.Application;

/** Plain launcher for the standalone bars example, usable on the classpath. */
public final class BarsMain {
    private BarsMain() {
    }

    /**
     * Starts the example.
     *
     * @param args include {@code fxml} to use the FXML screen
     */
    public static void main(String[] args) {
        Application.launch(BarsApp.class, args);
    }
}
