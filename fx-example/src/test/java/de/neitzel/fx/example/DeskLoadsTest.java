package de.neitzel.fx.example;

import de.neitzel.fx.example.model.Person;
import de.neitzel.fx.example.view.AddressForm;
import de.neitzel.fx.example.view.DeskController;
import de.neitzel.fx.view.LoadedView;
import de.neitzel.fx.view.ViewLoader;
import de.neitzel.injection.ApplicationContext;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Loads the person desk and checks the bindings an application developer can see.
 */
class DeskLoadsTest {

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

    @Test
    void deskEditsThePersonAndKeepsTheGreetingCurrent() throws Exception {
        onFx(() -> {
            Person person = DeskApp.sample();
            ApplicationContext context = new ApplicationContext(DeskApp.class);
            LoadedView loaded = ViewLoader.load(DeskController.class.getResource("Desk.fxml"), person, context);
            Parent root = loaded.getRoot();

            TextField nameField = (TextField) root.lookup("#nameField");
            TextField ageField = (TextField) root.lookup("#ageField");
            Label greeting = (Label) root.lookup("#greetingLabel");
            Label homeStreet = (Label) root.lookup("#homeStreetLabel");
            TextField street = (TextField) homeForm(root).lookup("#streetField");
            VBox visits = (VBox) root.lookup("#visitPane");
            ListView<?> tags = (ListView<?>) root.lookup("#tagList");

            assertEquals("Ada Lovelace", nameField.getText());
            assertEquals("Hello, Ada Lovelace", greeting.getText());
            assertEquals("years 36", ageField.getText());
            assertEquals("Sample Street 1", street.getText());
            assertEquals("Sample Street 1", homeStreet.getText());
            assertEquals(1, visits.getChildren().size());
            assertEquals(2, tags.getItems().size());

            nameField.setText("Grace");
            assertEquals("Grace", person.getName());
            assertEquals("Hello, Grace", greeting.getText());

            ageField.setText("years 41");
            assertEquals(Integer.valueOf(41), person.getAge());
            ageField.setText("nope");
            assertEquals(Integer.valueOf(41), person.getAge());
            assertNotNull(loaded.getViewModel().getLatestMessage());
            assertEquals("age", loaded.getViewModel().getLatestMessage().getSource());

            street.setText("Other Street");
            assertEquals("Other Street", person.getHome().getStreet());
            assertEquals("Other Street", homeStreet.getText());

            person.getTags().add("fx");
            assertEquals(3, tags.getItems().size());
        });
    }

    private static AddressForm homeForm(Parent root) {
        for (Node child : root.getChildrenUnmodifiable()) {
            if (child instanceof AddressForm form) {
                return form;
            }
        }
        fail("home form was not loaded");
        return null;
    }

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
