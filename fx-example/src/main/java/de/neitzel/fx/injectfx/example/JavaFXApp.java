package de.neitzel.fx.injectfx.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Minimal JavaFX application launcher used by the examples.
 *
 * <p>This class initializes the JavaFX runtime, loads {@code MainWindow.fxml} and shows
 * the primary stage. It is intended as a small bootstrap used by IDEs or test runs
 * to start the JavaFX UI for demonstration purposes.
 */
public class JavaFXApp extends Application {

    private static final Logger LOGGER = Logger.getLogger(JavaFXApp.class.getName());

    /**
     * Default constructor only.
     *
     * <p>The constructor does not perform UI initialization. UI setup happens in {@link #start(Stage)}
     * after JavaFX has set up the application thread.
     */
    public JavaFXApp() {
        // default constructor only
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            primaryStage.setTitle("Hello World!");
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("MainWindow.fxml"));
            Parent root = fxmlLoader.load();
            primaryStage.setScene(new Scene(root));
            primaryStage.show();
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Exception while starting JavaFXApp", ex);
        }
    }
}
