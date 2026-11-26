package de.neitzel.fx.view;

import de.neitzel.fx.view.component.AddressForm;
import de.neitzel.fx.view.fixture.Address;
import de.neitzel.fx.view.fixture.Person;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A repeat places one component in a pane for each model instance.
 */
class RepeatAddressComponentsTest {

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
     * Addresses are the address instances on the shared list.
     */
    @Test
    void addressesAreTheAddressInstancesOnTheSharedList() throws Exception {
        onFx(() -> {
            Address main = new Address("Main");
            Address oak = new Address("Oak");
            Person person = new Person("Ada");
            person.setAddresses(new ArrayList<>(List.of(main, oak)));
            URL fxml = RepeatAddressComponentsTest.class.getResource("plain-view.fxml");
            assertNotNull(fxml);
            TestViews context = new TestViews();
            LoadedView loaded = TestViews.load(fxml, person, context);
            assertInstanceOf(ObservableList.class, loaded.property("addresses").getValue());
            @SuppressWarnings("unchecked")
            ObservableList<Address> addresses = (ObservableList<Address>) loaded.property("addresses").getValue();
            assertSame(main, addresses.get(0));
            assertSame(oak, addresses.get(1));
            assertEquals("Main", addresses.get(0).getStreet());
            assertEquals("Oak", addresses.get(1).getStreet());
            assertSame(person.getAddresses(), addresses);
            addresses.add(new Address("Elm"));
            assertEquals(List.of("Main", "Oak", "Elm"), streets(person.getAddresses()));
        });
    }

    /**
     * Each address appears as its own form in list order.
     */
    @Test
    void eachAddressAppearsAsItsOwnFormInListOrder() throws Exception {
        onFx(() -> {
            Address main = new Address("Main");
            Address oak = new Address("Oak");
            Person person = new Person("Ada");
            person.setAddresses(new ArrayList<>(List.of(main, oak)));
            URL fxml = RepeatAddressComponentsTest.class.getResource("repeat-addresses.fxml");
            assertNotNull(fxml);
            TestViews context = new TestViews();
            LoadedView loaded = TestViews.load(fxml, person, context);
            VBox pane = (VBox) loaded.getRoot().lookup("#addressPane");
            assertEquals(2, pane.getChildren().size());
            AddressForm first = (AddressForm) pane.getChildren().get(0);
            AddressForm second = (AddressForm) pane.getChildren().get(1);
            assertNotSame(first, second);
            assertSame(main, first.getModel());
            assertSame(oak, second.getModel());
            assertEquals("Main", ((TextField) first.lookup("#streetField")).getText());
            assertEquals("Oak", ((TextField) second.lookup("#streetField")).getText());
            assertEquals("Lane", ((Label) first.lookup("#streetLabel")).getText());
            assertEquals("Lane", ((Label) second.lookup("#streetLabel")).getText());
        });
    }

    /**
     * Adding and removing an address updates the pane and the entity.
     */
    @Test
    void addingAndRemovingAnAddressUpdatesThePaneAndTheEntity() throws Exception {
        onFx(() -> {
            Address main = new Address("Main");
            Address oak = new Address("Oak");
            Person person = new Person("Ada");
            person.setAddresses(new ArrayList<>(List.of(main, oak)));
            URL fxml = RepeatAddressComponentsTest.class.getResource("repeat-addresses.fxml");
            assertNotNull(fxml);
            TestViews context = new TestViews();
            LoadedView loaded = TestViews.load(fxml, person, context);
            VBox pane = (VBox) loaded.getRoot().lookup("#addressPane");
            @SuppressWarnings("unchecked")
            ObservableList<Address> addresses = (ObservableList<Address>) loaded.property("addresses").getValue();
            Address elm = new Address("Elm");
            addresses.add(elm);
            assertEquals(3, pane.getChildren().size());
            AddressForm added = (AddressForm) pane.getChildren().get(2);
            assertSame(elm, added.getModel());
            assertEquals("Elm", ((TextField) added.lookup("#streetField")).getText());
            assertEquals(List.of("Main", "Oak", "Elm"), streets(person.getAddresses()));
            addresses.remove(oak);
            assertEquals(2, pane.getChildren().size());
            assertSame(main, ((AddressForm) pane.getChildren().get(0)).getModel());
            assertSame(elm, ((AddressForm) pane.getChildren().get(1)).getModel());
            assertEquals(List.of("Main", "Elm"), streets(person.getAddresses()));
            Address pine = new Address("Pine");
            person.getAddresses().add(pine);
            assertEquals(3, pane.getChildren().size());
            AddressForm pineForm = (AddressForm) pane.getChildren().get(2);
            assertSame(pine, pineForm.getModel());
            assertEquals("Pine", ((TextField) pineForm.lookup("#streetField")).getText());
        });
    }

    /**
     * Selection is a separate attribute and the forms do not store it.
     */
    @Test
    void selectionIsASeparateAttributeAndTheFormsDoNotStoreIt() throws Exception {
        onFx(() -> {
            Address main = new Address("Main");
            Address oak = new Address("Oak");
            Person person = new Person("Ada");
            person.setAddresses(new ArrayList<>(List.of(main, oak)));
            person.setSelectedAddress(oak);
            URL fxml = RepeatAddressComponentsTest.class.getResource("repeat-addresses.fxml");
            assertNotNull(fxml);
            TestViews context = new TestViews();
            LoadedView loaded = TestViews.load(fxml, person, context);
            @SuppressWarnings("unchecked")
            ComboBox<Address> selection = (ComboBox<Address>) loaded.getRoot().lookup("#selection");
            assertSame(oak, selection.getValue());
            selection.setValue(null);
            assertNull(person.getSelectedAddress());
            selection.setValue(main);
            assertSame(main, person.getSelectedAddress());
            VBox pane = (VBox) loaded.getRoot().lookup("#addressPane");
            assertEquals(2, pane.getChildren().size());
            AddressForm first = (AddressForm) pane.getChildren().get(0);
            AddressForm second = (AddressForm) pane.getChildren().get(1);
            assertSame(main, first.getModel());
            assertSame(oak, second.getModel());
            assertEquals("Main", ((TextField) first.lookup("#streetField")).getText());
            assertEquals("Oak", ((TextField) second.lookup("#streetField")).getText());
        });
    }

    /**
     * A second address form in the same document is another component.
     */
    @Test
    void aSecondAddressFormInTheSameDocumentIsAnotherComponent() throws Exception {
        onFx(() -> {
            Address home = new Address("Main");
            Address work = new Address("Oak");
            Person person = new Person("Ada");
            person.setHome(home);
            person.setWork(work);
            URL fxml = RepeatAddressComponentsTest.class.getResource("two-address-forms.fxml");
            assertNotNull(fxml);
            TestViews context = new TestViews();
            LoadedView loaded = TestViews.load(fxml, person, context);
            AddressForm homeForm = (AddressForm) loaded.getRoot().lookup("#homeForm");
            AddressForm workForm = (AddressForm) loaded.getRoot().lookup("#workForm");
            assertNotNull(homeForm);
            assertNotNull(workForm);
            assertNotSame(homeForm, workForm);
            assertSame(home, homeForm.getModel());
            assertSame(work, workForm.getModel());
            assertEquals("Main", ((TextField) homeForm.lookup("#streetField")).getText());
            assertEquals("Oak", ((TextField) workForm.lookup("#streetField")).getText());
            assertEquals("Lane", ((Label) homeForm.lookup("#streetLabel")).getText());
            assertEquals("Lane", ((Label) workForm.lookup("#streetLabel")).getText());
        });
    }

    /**
     * Returns the street of each address, in list order.
     *
     * @param addresses the addresses
     * @return the streets, in list order
     */
    private static List<String> streets(List<Address> addresses) {
        return addresses.stream().map(Address::getStreet).toList();
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
