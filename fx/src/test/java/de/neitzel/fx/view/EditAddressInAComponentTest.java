package de.neitzel.fx.view;

import de.neitzel.fx.view.component.AddressForm;
import de.neitzel.fx.view.component.Envelope;
import de.neitzel.fx.view.component.Letter;
import de.neitzel.fx.view.fixture.Address;
import de.neitzel.fx.view.fixture.Person;
import de.neitzel.injection.ApplicationContext;
import javafx.application.Platform;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A component edits one model instance, and a nested component edits the next.
 */
class EditAddressInAComponentTest {

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
     * Editing the street updates that address.
     */
    @Test
    void editingTheStreetUpdatesThatAddress() throws Exception {
        onFx(() -> {
            Address address = new Address("Main");
            Person person = new Person("Ada");
            person.setAddress(address);
            URL fxml = EditAddressInAComponentTest.class.getResource("address-component.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.component");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            AddressForm form = (AddressForm) loaded.getRoot().lookup("#addressForm");
            assertNotNull(form);
            assertSame(address, form.getModel());
            TextField streetField = (TextField) form.lookup("#streetField");
            assertEquals("Main", streetField.getText());
            streetField.setText("Oak");
            assertEquals("Oak", address.getStreet());
        });
    }

    /**
     * Null address leaves the model unset and keeps the street text.
     */
    @Test
    void nullAddressLeavesTheModelUnsetAndKeepsTheStreetText() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL fxml = EditAddressInAComponentTest.class.getResource("address-component.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.component");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            AddressForm form = (AddressForm) loaded.getRoot().lookup("#addressForm");
            assertNotNull(form);
            assertNull(form.getModel());
            TextField streetField = (TextField) form.lookup("#streetField");
            assertEquals("kept", streetField.getText());
            assertNull(person.getAddress());
        });
    }

    /**
     * Street label shows the injected prefix.
     */
    @Test
    void streetLabelShowsTheInjectedPrefix() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAddress(new Address("Main"));
            URL fxml = EditAddressInAComponentTest.class.getResource("address-component.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.component");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            AddressForm form = (AddressForm) loaded.getRoot().lookup("#addressForm");
            Label streetLabel = (Label) form.lookup("#streetLabel");
            assertEquals("Lane", streetLabel.getText());
        });
    }

    /**
     * Component receives the address view model before its bindings run.
     */
    @Test
    void componentReceivesTheAddressViewModelBeforeItsBindingsRun() throws Exception {
        onFx(() -> {
            Address address = new Address("Main");
            Person person = new Person("Ada");
            person.setAddress(address);
            URL fxml = EditAddressInAComponentTest.class.getResource("address-component.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.component");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            AddressForm form = (AddressForm) loaded.getRoot().lookup("#addressForm");
            assertSame(loaded.property("address").getValue(), form.receivedViewModel());
            assertEquals("kept", form.streetTextAtHandOff());
            TextField streetField = (TextField) form.lookup("#streetField");
            assertEquals("Main", streetField.getText());
        });
    }

    /**
     * Nested component shows the inner street.
     */
    @Test
    void nestedComponentShowsTheInnerStreet() throws Exception {
        onFx(() -> {
            Address address = new Address("Main");
            Letter letter = new Letter(new Envelope(address));
            URL fxml = EditAddressInAComponentTest.class.getResource("letter-envelope.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.component");
            LoadedView loaded = ViewLoader.load(fxml, letter, context);
            TextField streetField = (TextField) loaded.getRoot().lookup("#streetField");
            assertEquals("Main", streetField.getText());
            streetField.setText("Oak");
            assertEquals("Oak", address.getStreet());
        });
    }

    /**
     * Attributes retain FXML conversion and identity while injection precedes setters.
     */
    @Test
    void componentAttributesApplyAfterInitialization() throws Exception {
        onFx(() -> {
            var context = new ApplicationContext("de.neitzel.fx.view.component");
            var loaded = ViewLoader.load(getClass().getResource("address-component-attributes.fxml"),
                    new Person("Ada"), context);
            var first = (AddressForm) loaded.getRoot().lookup("#first");
            var second = (AddressForm) loaded.getRoot().lookup("#second");
            assertEquals("Home & work", first.getCaption());
            assertEquals(190, first.getPrefWidth());
            assertEquals(360, first.getMaxWidth());
            assertEquals(false, first.isVisible());
            assertEquals(javafx.geometry.Pos.CENTER_RIGHT, first.getAlignment());
            assertEquals(1, javafx.scene.layout.GridPane.getColumnIndex(first));
            assertSame(first.getModel(), second.getModel());
            assertEquals("Second", second.getCaption());
            assertEquals(280, second.getPrefWidth());
            assertSame(first, context.getComponent(AddressForm.class));
            TextField field = (TextField) first.lookup("#streetField");
            assertEquals("Main", field.getText());
            field.setText("Oak");
            assertEquals("Oak", ((Address) first.getModel()).getStreet());
        });
    }

    /**
     * Invalid names and malformed values fail rather than disappearing in the builder.
     */
    @Test
    void invalidComponentAttributesFailTheLoad() throws Exception {
        onFx(() -> {
            for (String kind : java.util.List.of("unknown", "number")) {
                var context = new ApplicationContext("de.neitzel.fx.view.component");
                org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                        () -> ViewLoader.load(getClass().getResource("address-component-invalid-" + kind + ".fxml"),
                                new Person("Ada"), context));
            }
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
