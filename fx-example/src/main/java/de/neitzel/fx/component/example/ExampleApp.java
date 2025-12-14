package de.neitzel.fx.component.example;

import de.neitzel.fx.component.ComponentLoader;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Example JavaFX application demonstrating the {@link de.neitzel.fx.component.ComponentLoader} usage.
 */
public class ExampleApp extends Application {

    /**
     * Default constructor only
     */
    public ExampleApp() {
        // default constructor only
    }

    /**
     * Launches the JavaFX application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Initializes sample model data, loads the FX component and shows the primary stage.
     *
     * @param primaryStage the primary stage for this application
     */
    @Override
    public void start(Stage primaryStage) throws Exception {
        // Beispielmodel initialisieren
        Address address = new Address();
        address.setStreet("Sample Street 1");
        address.setCity("Sample City");

        Person person = new Person();
        person.setName("Max Mustermann");
        person.setAddress(address);

        // ComponentLoader verwenden
        ComponentLoader loader = new ComponentLoader();
        Parent root = loader.load(person, "/person.fxml");

        // Scene erstellen und anzeigen
        Scene scene = new Scene(root);
        primaryStage.setTitle("ComponentLoader Example");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}