package de.neitzel.fx.example;

import de.neitzel.fx.example.model.Address;
import de.neitzel.fx.example.model.Note;
import de.neitzel.fx.example.model.Person;
import de.neitzel.fx.example.model.Role;
import de.neitzel.fx.example.view.DeskController;
import de.neitzel.fx.view.LoadedView;
import de.neitzel.fx.view.MessagePopup;
import de.neitzel.fx.view.ViewLoader;
import de.neitzel.injection.ApplicationContext;
import de.neitzel.injection.annotation.Config;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens the person desk for one ordinary {@link Person}.
 * <p>
 * {@link Config} names the package the container scans. The same package would be used without
 * the annotation, because this class lives there. The attribute is set so the annotation is what
 * chooses the package.
 */
@Config(basePackage = "de.neitzel.fx.example")
public class DeskApp extends Application {

    /**
     * Creates the JavaFX application.
     */
    public DeskApp() {
    }

    /**
     * Builds the person the desk edits.
     *
     * @return a person with a home address, three visits, and two tags
     */
    public static Person sample() {
        Address home = new Address("Sample Street 1", "Sample City");
        home.setNote(new Note("Kept at home"));
        Address visit = new Address("Work Road 2", "Office City");
        visit.setVisitedOn(LocalDate.of(2020, 1, 2));
        Address berlin = new Address("Sample Lane", "Berlin");
        berlin.setVisitedOn(LocalDate.of(2020, 3, 4));
        Address bonn = new Address("Oak Street", "Bonn");
        bonn.setVisitedOn(LocalDate.of(2019, 12, 1));
        Person person = new Person();
        person.setName("Ada Lovelace");
        person.setAge(36);
        person.setActive(true);
        person.setRole(Role.AUTHOR);
        person.setHome(home);
        person.setVisits(new ArrayList<>(List.of(visit, berlin, bonn)));
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
