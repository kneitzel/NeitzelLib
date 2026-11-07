package de.neitzel.fx.view;

import de.neitzel.fx.view.fixture.Person;
import de.neitzel.injection.ApplicationContext;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A direct binding shows one attribute and writes it when the direction says so.
 */
class ShowAndEditOneAttributeTest {

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
     * Both ways text field shows the name and typing updates the model.
     */
    @Test
    void bothWaysTextFieldShowsTheNameAndTypingUpdatesTheModel() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL fxml = ShowAndEditOneAttributeTest.class.getResource("name-both-ways.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            TextField nameField = (TextField) loaded.getRoot().lookup("#nameField");
            assertEquals("Ada", nameField.getText());
            nameField.setText("Grace");
            assertEquals("Grace", person.getName());
        });
    }

    /**
     * Toward view label shows the name follows the field and does not write the model.
     */
    @Test
    void towardViewLabelShowsTheNameFollowsTheFieldAndDoesNotWriteTheModel() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL fxml = ShowAndEditOneAttributeTest.class.getResource("name-both-and-view.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            TextField nameField = (TextField) loaded.getRoot().lookup("#nameField");
            Label nameLabel = (Label) loaded.getRoot().lookup("#nameLabel");
            assertEquals("Ada", nameLabel.getText());
            nameField.setText("Grace");
            assertEquals("Grace", nameLabel.getText());
            assertEquals("Grace", person.getName());
            nameLabel.setText("Hopper");
            assertEquals("Grace", person.getName());
        });
    }

    /**
     * Entity setter after load leaves the text control unchanged.
     */
    @Test
    void entitySetterAfterLoadLeavesTheTextControlUnchanged() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL fxml = ShowAndEditOneAttributeTest.class.getResource("name-both-ways.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            person.setName("Grace");
            TextField nameField = (TextField) loaded.getRoot().lookup("#nameField");
            assertEquals("Ada", nameField.getText());
        });
    }

    /**
     * Binding a read only getter fails the load.
     */
    @Test
    void bindingAReadOnlyGetterFailsTheLoad() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL fxml = ShowAndEditOneAttributeTest.class.getResource("read-only-title.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> ViewLoader.load(fxml, person, context));
            assertTrue(failure.getMessage().contains("title"));
        });
    }

    /**
     * Binding a method declared on object fails the load.
     */
    @Test
    void bindingAMethodDeclaredOnObjectFailsTheLoad() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL fxml = ShowAndEditOneAttributeTest.class.getResource("object-class.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> ViewLoader.load(fxml, person, context));
            assertTrue(failure.getMessage().contains("class"));
        });
    }

    /**
     * Null integer attribute uses an integer property.
     */
    @Test
    void nullIntegerAttributeUsesAnIntegerProperty() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL fxml = ShowAndEditOneAttributeTest.class.getResource("name-both-ways.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            assertInstanceOf(IntegerProperty.class, loaded.property("age"));
        });
    }

    /**
     * Runs one check on the JavaFX application thread.
     *
     * @param action the check
     */
    private static void onFx(FxAction action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable failure) {
                thrown.set(failure);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(10, TimeUnit.SECONDS)) {
            fail("JavaFX thread did not finish");
        }
        Throwable failure = thrown.get();
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure != null) {
            throw new AssertionError(failure);
        }
    }

    /**
     * One check that runs on the JavaFX application thread.
     */
    @FunctionalInterface
    private interface FxAction {
        /**
         * Runs the check.
         *
         * @throws Exception when the check fails
         */
        void run() throws Exception;
    }
}
