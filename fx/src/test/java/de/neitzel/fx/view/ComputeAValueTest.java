package de.neitzel.fx.view;

import de.neitzel.fx.view.fixture.Person;
import de.neitzel.injection.ApplicationContext;
import javafx.application.Platform;
import javafx.beans.property.Property;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A computed binding writes a controller method result and refreshes it when another property changes.
 */
class ComputeAValueTest {

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
     * Computed binding shows the method result and leaves the model unchanged.
     */
    @Test
    void computedBindingShowsTheMethodResultAndLeavesTheModelUnchanged() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAge(36);
            URL fxml = ComputeAValueTest.class.getResource("working-age.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.computed");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            Label workingAgeLabel = (Label) loaded.getRoot().lookup("#workingAgeLabel");
            assertEquals("initial-age", workingAgeLabel.getText());
            assertEquals("Ada", person.getName());
            assertEquals(36, person.getAge());
        });
    }

    /**
     * Typing in another text field updates the computed label.
     */
    @Test
    void typingInAnotherTextFieldUpdatesTheComputedLabel() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL fxml = ComputeAValueTest.class.getResource("working-age.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.computed");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            TextField nameField = (TextField) loaded.getRoot().lookup("#nameField");
            Label workingAgeLabel = (Label) loaded.getRoot().lookup("#workingAgeLabel");
            nameField.setText("Grace");
            assertEquals("typed-age", workingAgeLabel.getText());
            assertEquals("Grace", person.getName());
        });
    }

    /**
     * Setting a view model property updates the computed label.
     */
    @Test
    void settingAViewModelPropertyUpdatesTheComputedLabel() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL fxml = ComputeAValueTest.class.getResource("working-age.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.computed");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            Label workingAgeLabel = (Label) loaded.getRoot().lookup("#workingAgeLabel");
            @SuppressWarnings("unchecked")
            Property<String> name = (Property<String>) loaded.property("name");
            name.setValue("Edsger");
            assertEquals("model-age", workingAgeLabel.getText());
            assertEquals("Edsger", person.getName());
        });
    }

    /**
     * Changing the computed label does not run the method again.
     */
    @Test
    void changingTheComputedLabelDoesNotRunTheMethodAgain() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL fxml = ComputeAValueTest.class.getResource("working-age.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.computed");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            Label workingAgeLabel = (Label) loaded.getRoot().lookup("#workingAgeLabel");
            assertEquals("initial-age", workingAgeLabel.getText());
            workingAgeLabel.setText("sentinel");
            assertEquals("sentinel", workingAgeLabel.getText());
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
