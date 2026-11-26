package de.neitzel.fx.example;

import de.neitzel.fx.example.model.Person;
import de.neitzel.fx.example.model.Role;
import de.neitzel.fx.example.view.AddressForm;
import de.neitzel.fx.example.view.DeskController;
import de.neitzel.fx.example.view.PersonViewModel;
import de.neitzel.fx.example.view.VisitForm;
import de.neitzel.fx.example.view.VisitRow;
import de.neitzel.fx.view.LoadedView;
import de.neitzel.fx.view.ViewLoader;
import de.neitzel.fx.view.ViewDependencies;
import de.neitzel.fx.view.ViewModelContext;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Loads the person desk and checks the bindings an application developer can see.
 */
class DeskLoadsTest {

    /**
     * Starts the JavaFX toolkit once for this class.
     */
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        if (!started.await(10, TimeUnit.SECONDS)) {
            fail("JavaFX toolkit did not start");
        }
    }

    /**
     * The desk shows the sample person, including directions, the title, and the home note.
     */
    @Test
    void deskShowsThePerson() throws Exception {
        onFx(() -> {
            Loaded loaded = load();
            Parent root = loaded.root();
            AddressForm home = homeForm(root);

            assertEquals("Ada Lovelace", text(root, "nameField"));
            assertEquals("Ada Lovelace", text(root, "nameEcho"));
            assertEquals("Ada Lovelace", text(root, "nameDraft"));
            assertEquals("Hello, Ada Lovelace (36)", text(root, "greetingLabel"));
            assertEquals("years 36", text(root, "ageField"));
            assertEquals("titled-Ada Lovelace", text(root, "titleLabel"));
            assertTrue(((CheckBox) root.lookup("#activeBox")).isSelected());
            assertEquals(Role.AUTHOR, ((ChoiceBox<?>) root.lookup("#roleChoice")).getValue());
            assertEquals("Sample Street 1", text(home, "streetField"));
            assertEquals("Sample City", text(home, "cityField"));
            assertEquals("Sample Street 1", text(root, "homeStreetLabel"));
            assertEquals("Address", text(home, "headingLabel"));
            assertEquals("Opened as Sample City", text(home, "openedAsLabel"));
            assertEquals("Kept at home", text(home, "noteField"));
            assertEquals(3, ((VBox) root.lookup("#visitPane")).getChildren().size());
            assertEquals(3, tableBody(root).getChildren().size());
            assertEquals("Street", headerText(root, 0));
            assertEquals("City", headerText(root, 1));
            assertEquals("Visited", headerText(root, 2));
            HBox header = tableHeader(root);
            assertEquals(300, ((Region) header.getChildren().get(0)).getPrefWidth());
            assertEquals(76, ((Region) header.getChildren().get(1)).getPrefWidth());
            assertEquals(120, ((Region) header.getChildren().get(2)).getPrefWidth());
            assertEquals(8, header.getSpacing());
            VisitRow first = (VisitRow) tableBody(root).getChildren().get(0);
            assertSame(loaded.person().getVisits().get(0), first.getModel());
            assertEquals("Work Road 2", text(first, "streetField"));
            assertEquals("Office City", text(first, "cityField"));
            assertEquals("2020-01-02", text(first, "visitedField"));
            assertTrue(first.getStyleClass().contains("selected"));
            assertEquals(2, ((ListView<?>) root.lookup("#tagList")).getItems().size());
            assertInstanceOf(PersonViewModel.class, loaded.view().getViewModel());
        });
    }

    /**
     * A later call to the entity setter leaves the controls on the view-model value.
     */
    @Test
    void entitySetterLeavesTheControls() throws Exception {
        onFx(() -> {
            Loaded loaded = load();
            loaded.person().setName("Ignored");
            assertEquals("Ignored", loaded.person().getName());
            assertEquals("Ada Lovelace", text(loaded.root(), "nameField"));
            assertEquals("Ada Lovelace", text(loaded.root(), "nameEcho"));
            assertEquals("Ada Lovelace", text(loaded.root(), "nameDraft"));
        });
    }

    /**
     * A second view of the same person reuses the view model, so a name edit shows on both.
     */
    @Test
    void secondViewReusesTheViewModel() throws Exception {
        onFx(() -> {
            Loaded first = load();
            LoadedView second = ViewLoader.loadWithDependencies(
                    DeskLoadsTest.class.getResource("NameLine.fxml"), first.person(), first.context(), first.models());
            assertSame(first.view().getViewModel(), second.getViewModel());
            textField(first.root(), "nameField").setText("Grace");
            assertEquals("Grace", first.person().getName());
            assertEquals("Grace", text(second.getRoot(), "nameField"));
            assertEquals("titled-Grace", text(first.root(), "titleLabel"));
        });
    }

    /**
     * Both, view, and model directions on the name, and the title follows the view model.
     */
    @Test
    void nameDirectionsUpdateTheTitleAndTheGreeting() throws Exception {
        onFx(() -> {
            Loaded loaded = load();
            Parent root = loaded.root();
            textField(root, "nameField").setText("Grace");
            assertEquals("Grace", loaded.person().getName());
            assertEquals("Grace", text(root, "nameEcho"));
            assertEquals("Ada Lovelace", text(root, "nameDraft"));
            assertEquals("Hello, Grace (36)", text(root, "greetingLabel"));
            assertEquals("titled-Grace", text(root, "titleLabel"));

            textField(root, "nameEcho").setText("Hopper");
            assertEquals("Grace", loaded.person().getName());
            assertEquals("Grace", text(root, "nameField"));

            textField(root, "nameDraft").setText("Byron");
            assertEquals("Byron", loaded.person().getName());
            assertEquals("Byron", text(root, "nameField"));
            assertEquals("Byron", text(root, "nameEcho"));
            assertEquals("titled-Byron", text(root, "titleLabel"));
        });
    }

    /**
     * A bad age keeps the previous value, records one error, and a later success clears it.
     * Changing the age also refreshes the greeting.
     */
    @Test
    void ageConverterRecordsAndClearsAFailure() throws Exception {
        onFx(() -> {
            Loaded loaded = load();
            Parent root = loaded.root();
            TextField age = textField(root, "ageField");
            age.setText("years 41");
            assertEquals(Integer.valueOf(41), loaded.person().getAge());
            assertEquals("Hello, Ada Lovelace (41)", text(root, "greetingLabel"));

            age.setText("nope");
            assertEquals(Integer.valueOf(41), loaded.person().getAge());
            assertEquals("nope", age.getText());
            assertEquals("age", loaded.view().getViewModel().getLatestMessage().getSource());
            assertEquals(de.neitzel.fx.view.Message.Severity.ERROR,
                    loaded.view().getViewModel().getLatestMessage().getSeverity());
            assertEquals(loaded.view().getViewModel().getLatestMessage().getText(), text(root, "noticeLabel"));

            age.setText("years 41");
            assertNull(loaded.view().getViewModel().getLatestMessage());
            assertNull(text(root, "noticeLabel"));
        });
    }

    /**
     * An empty home street is carried onto the person as {@code home.street}, then cleared.
     */
    @Test
    void emptyStreetIsCarriedFromTheHomeAddress() throws Exception {
        onFx(() -> {
            Loaded loaded = load();
            Parent root = loaded.root();
            AddressForm home = homeForm(root);
            TextField street = textField(home, "streetField");
            street.setText(" ");
            assertEquals("Sample Street 1", loaded.person().getHome().getStreet());
            assertEquals(" ", street.getText());
            assertEquals("Sample Street 1", text(root, "homeStreetLabel"));
            assertEquals("home.street", loaded.view().getViewModel().getLatestMessage().getSource());
            assertEquals("Street is required", text(root, "noticeLabel"));

            street.setText("Other Street");
            assertEquals("Other Street", loaded.person().getHome().getStreet());
            assertEquals("Other Street", text(root, "homeStreetLabel"));
            assertNull(loaded.view().getViewModel().getLatestMessage());

            textField(home, "cityField").setText("Other City");
            assertEquals("Opened as Sample City", text(home, "openedAsLabel"));
            textField(home, "noteField").setText("Moved");
            assertEquals("Moved", loaded.person().getHome().getNote().getText());
        });
    }

    /**
     * Editing a table row updates the visit and the repeated form. Removing the row drops it.
     */
    @Test
    void tableRowEditsTheVisitAndRemovesIt() throws Exception {
        onFx(() -> {
            Loaded loaded = load();
            Parent root = loaded.root();
            VBox visits = (VBox) root.lookup("#visitPane");
            VisitRow first = (VisitRow) tableBody(root).getChildren().get(0);
            textField(first, "streetField").setText("Changed Road");
            assertEquals("Changed Road", loaded.person().getVisits().get(0).getStreet());
            VisitForm repeated = (VisitForm) visits.getChildren().get(0);
            assertEquals("Changed Road", text(repeated, "streetField"));

            ListView<?> tags = (ListView<?>) root.lookup("#tagList");
            loaded.person().getTags().add("fx");
            assertEquals(3, tags.getItems().size());

            Button remove = (Button) first.lookup(".button");
            assertEquals(Region.USE_COMPUTED_SIZE, remove.getPrefWidth());
            remove.fire();
            assertEquals(2, loaded.person().getVisits().size());
            assertNull(loaded.person().getSelectedVisit());
            assertEquals(2, visits.getChildren().size());
            assertEquals(2, tableBody(root).getChildren().size());
        });
    }

    /**
     * Info, warning, and fatal notices show on the notice label.
     */
    @Test
    void noticesShowInfoWarningAndFatal() throws Exception {
        onFx(() -> {
            Loaded loaded = load();
            Parent root = loaded.root();
            fire(root, "Leave a note");
            assertEquals(de.neitzel.fx.view.Message.Severity.INFO,
                    loaded.view().getViewModel().getLatestMessage().getSeverity());
            assertEquals("The model already has the name Ada Lovelace.", text(root, "noticeLabel"));
            fire(root, "Leave a warning");
            assertEquals(de.neitzel.fx.view.Message.Severity.WARN,
                    loaded.view().getViewModel().getLatestMessage().getSeverity());
            assertEquals("Check the age before leaving the desk.", text(root, "noticeLabel"));
            fire(root, "Leave a fatal notice");
            assertEquals(de.neitzel.fx.view.Message.Severity.FATAL,
                    loaded.view().getViewModel().getLatestMessage().getSeverity());
            assertEquals("The desk cannot store this person.", text(root, "noticeLabel"));
        });
    }

    /**
     * The active flag and the role write back to the person.
     */
    @Test
    void activeAndRoleWriteBack() throws Exception {
        onFx(() -> {
            Loaded loaded = load();
            Parent root = loaded.root();
            CheckBox active = (CheckBox) root.lookup("#activeBox");
            active.setSelected(false);
            assertFalse(loaded.person().isActive());
            @SuppressWarnings("unchecked")
            ChoiceBox<Role> role = (ChoiceBox<Role>) root.lookup("#roleChoice");
            role.setValue(Role.READER);
            assertEquals(Role.READER, loaded.person().getRole());
        });
    }

    /**
     * One loaded desk: the person, the view, and the container that built it.
     *
     * @param person  the model
     * @param view    the loaded view
     * @param context the adapter
     * @param models the ViewModel context
     */
    private record Loaded(Person person, LoadedView view, ViewDependencies context, ViewModelContext models) {

        /**
         * Returns the scene-graph root.
         *
         * @return the root
         */
        private Parent root() {
            return view.getRoot();
        }
    }

    /**
     * Loads the desk for a fresh sample person.
     *
     * @return the loaded desk
     */
    private static Loaded load() {
        Person person = DeskApp.sample();
        var context = DeskApp.dependencies(person);
        var models = new ViewModelContext();
        var view = ViewLoader.loadWithDependencies(
                DeskController.class.getResource("Desk.fxml"), person, context, models);
        return new Loaded(person, view, context, models);
    }

    private static void fire(Parent root, String text) {
        root.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> text.equals(button.getText())).findFirst().orElseThrow().fire();
    }

    /**
     * Returns the text of a labeled control.
     *
     * @param parent the parent to search
     * @param id     the control id
     * @return the text
     */
    private static String text(Parent parent, String id) {
        Node node = parent.lookup("#" + id);
        if (node instanceof TextField field) {
            return field.getText();
        }
        if (node instanceof Label label) {
            return label.getText();
        }
        fail("no text on " + id);
        return null;
    }

    /**
     * Returns a text field.
     *
     * @param parent the parent to search
     * @param id     the field id
     * @return the field
     */
    private static TextField textField(Parent parent, String id) {
        return (TextField) parent.lookup("#" + id);
    }

    /**
     * Returns the visit table body.
     *
     * @param root the loaded scene
     * @return the rows currently showing
     */
    private static VBox tableBody(Parent root) {
        ScrollPane scroll = (ScrollPane) root.lookup("#visitTable").lookup(".table-scroll");
        return (VBox) scroll.getContent();
    }

    /**
     * Returns the header text of one visit-table column.
     *
     * @param root  the loaded scene
     * @param index the column index
     * @return the header text
     */
    private static String headerText(Parent root, int index) {
        Label label = (Label) tableHeader(root).getChildren().get(index).lookup(".column-header");
        return label.getText();
    }

    /**
     * Returns the visit-table header.
     *
     * @param root the loaded scene
     * @return the header
     */
    private static HBox tableHeader(Parent root) {
        ScrollPane headerScroll = (ScrollPane) root.lookup("#visitTable").lookup(".table-header-scroll");
        return (HBox) headerScroll.getContent();
    }

    /**
     * Returns the home-address component in the loaded desk.
     *
     * @param root the loaded scene
     * @return the home-address component
     */
    private static AddressForm homeForm(Parent root) {
        for (Node child : root.getChildrenUnmodifiable()) {
            if (child instanceof AddressForm form) {
                return form;
            }
        }
        fail("home form was not loaded");
        return null;
    }

    /**
     * Runs one check on the JavaFX application thread.
     *
     * @param check the check
     */
    private static void onFx(Runnable check) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                check.run();
            } catch (Throwable thrown) {
                failure.set(thrown);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(10, TimeUnit.SECONDS)) {
            fail("JavaFX check did not finish");
        }
        if (failure.get() != null) {
            fail("desk check failed", failure.get());
        }
    }
}
