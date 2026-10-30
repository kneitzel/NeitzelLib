package de.neitzel.fx.example;

import de.neitzel.fx.example.model.Address;
import de.neitzel.fx.example.model.Person;
import de.neitzel.fx.example.view.DeskController;
import de.neitzel.fx.view.LoadedView;
import de.neitzel.fx.view.MessagePopup;
import de.neitzel.fx.view.ViewLoader;
import de.neitzel.injection.ApplicationContext;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * Opens the person desk for one ordinary {@link Person}.
 */
public class DeskApp extends Application {

    /**
     * Creates the JavaFX application.
     */
    public DeskApp() {
    }

    /**
     * Builds the person the desk edits.
     *
     * @return a person with a home address, one visit, and two tags
     */
    public static Person sample() {
        Address home = new Address("Sample Street 1", "Sample City");
        Address visit = new Address("Work Road 2", "Office City");
        Person person = new Person();
        person.setName("Ada Lovelace");
        person.setAge(36);
        person.setHome(home);
        person.setVisits(new ArrayList<>(List.of(visit)));
        person.setSelectedVisit(visit);
        person.setTags(new ArrayList<>(List.of("dns", "math")));
        return person;
    }

    /**
     * Launches the desk.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Loads the desk for {@link #sample()} and shows it.
     *
     * @param stage the primary stage
     */
    @Override
    public void start(Stage stage) {
        ApplicationContext context = new ApplicationContext(DeskApp.class);
        Person person = sample();
        LoadedView loaded = ViewLoader.load(DeskController.class.getResource("Desk.fxml"), person, context);
        MessagePopup.showNewMessages(loaded.getViewModel());
        stage.setTitle("fx example");
        stage.setScene(new Scene(loaded.getRoot()));
        stage.show();
    }
}
