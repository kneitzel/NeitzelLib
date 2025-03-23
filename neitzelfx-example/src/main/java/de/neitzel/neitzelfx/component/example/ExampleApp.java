package de.neitzel.neitzelfx.component.example;

import de.neitzel.neitzelfx.component.ComponentLoader;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ExampleApp extends Application {

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

    public static void main(String[] args) {
        launch();
    }
}