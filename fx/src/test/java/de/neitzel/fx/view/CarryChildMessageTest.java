package de.neitzel.fx.view;

import de.neitzel.fx.view.fixture.Address;
import de.neitzel.fx.view.fixture.Household;
import de.neitzel.fx.view.fixture.Person;
import de.neitzel.injection.ApplicationContext;
import javafx.application.Platform;
import javafx.beans.property.Property;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

class CarryChildMessageTest {

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
    void aStreetNoticeOnTheAddressAppearsOnThePersonAsAddressStreet() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAddress(new Address("Main"));
            URL fxml = CarryChildMessageTest.class.getResource("name-both-ways.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            ViewModel personModel = loaded.getViewModel();
            ViewModel addressModel = (ViewModel) personModel.property("address").getValue();
            addressModel.addMessage(new Message(Message.Severity.INFO, "lane is closed", "street"));

            assertEquals(1, addressModel.getMessages().size());
            assertEquals("street", addressModel.getMessages().get(0).getSource());
            assertEquals(1, personModel.getMessages().size());
            Message carried = personModel.getMessages().get(0);
            assertEquals("address.street", carried.getSource());
            assertEquals("lane is closed", carried.getText());
            assertEquals(Message.Severity.INFO, carried.getSeverity());
            assertNotSame(addressModel.getMessages().get(0), carried);
            assertEquals(carried, personModel.getLatestMessage());
        });
    }

    @Test
    void aNestedStreetNoticeReadsAsPersonAddressStreet() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAddress(new Address("Main"));
            Household household = new Household();
            household.setPerson(person);
            URL fxml = CarryChildMessageTest.class.getResource("plain-view.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, household, context);
            ViewModel householdModel = loaded.getViewModel();
            ViewModel personModel = (ViewModel) householdModel.property("person").getValue();
            ViewModel addressModel = (ViewModel) personModel.property("address").getValue();
            addressModel.addMessage(new Message(Message.Severity.INFO, "lane is closed", "street"));

            assertEquals("street", addressModel.getMessages().get(0).getSource());
            assertEquals("address.street", personModel.getMessages().get(0).getSource());
            assertEquals(1, householdModel.getMessages().size());
            Message carried = householdModel.getMessages().get(0);
            assertEquals("person.address.street", carried.getSource());
            assertEquals("lane is closed", carried.getText());
            assertEquals(Message.Severity.INFO, carried.getSeverity());
        });
    }

    @Test
    void aBadStreetIsOwnedByTheAddressAndCarriedAsAddressStreet() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAddress(new Address("Main"));
            URL fxml = CarryChildMessageTest.class.getResource("address-street-converted.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            TextField streetField = (TextField) loaded.getRoot().lookup("#streetField");
            streetField.setText("nope");

            assertEquals("Main", person.getAddress().getStreet());
            assertEquals("Main", streetField.getText());
            ViewModel personModel = loaded.getViewModel();
            ViewModel addressModel = (ViewModel) personModel.property("address").getValue();
            assertEquals(1, addressModel.getMessages().size());
            Message owned = addressModel.getMessages().get(0);
            assertEquals(Message.Severity.ERROR, owned.getSeverity());
            assertEquals("street", owned.getSource());
            assertEquals("not a street: nope", owned.getText());
            assertEquals(1, personModel.getMessages().size());
            Message carried = personModel.getMessages().get(0);
            assertEquals("address.street", carried.getSource());
            assertEquals("not a street: nope", carried.getText());
            assertEquals(Message.Severity.ERROR, carried.getSeverity());
            assertNotSame(owned, carried);
        });
    }

    @Test
    void aFixedStreetDropsTheNoticeFromTheAddressAndThePerson() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAddress(new Address("Main"));
            URL fxml = CarryChildMessageTest.class.getResource("address-street-converted.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            TextField streetField = (TextField) loaded.getRoot().lookup("#streetField");
            streetField.setText("nope");
            streetField.setText("ok Oak");

            assertEquals("Oak", person.getAddress().getStreet());
            ViewModel personModel = loaded.getViewModel();
            ViewModel addressModel = (ViewModel) personModel.property("address").getValue();
            assertEquals(0, addressModel.getMessages().size());
            assertNull(addressModel.getLatestMessage());
            assertEquals(0, personModel.getMessages().size());
            assertNull(personModel.getLatestMessage());
        });
    }

    @Test
    void anotherBadStreetUpdatesTheCarriedNoticeInPlace() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAddress(new Address("Main"));
            Household household = new Household();
            household.setPerson(person);
            URL fxml = CarryChildMessageTest.class.getResource("household-street-converted.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, household, context);
            TextField streetField = (TextField) loaded.getRoot().lookup("#streetField");
            streetField.setText("nope");

            ViewModel householdModel = loaded.getViewModel();
            ViewModel personModel = (ViewModel) householdModel.property("person").getValue();
            ViewModel addressModel = (ViewModel) personModel.property("address").getValue();
            assertEquals(1, addressModel.getMessages().size());
            assertEquals(1, personModel.getMessages().size());
            assertEquals(1, householdModel.getMessages().size());
            Message owned = addressModel.getMessages().get(0);
            Message personCopy = personModel.getMessages().get(0);
            Message householdCopy = householdModel.getMessages().get(0);
            assertEquals("street", owned.getSource());
            assertEquals("address.street", personCopy.getSource());
            assertEquals("person.address.street", householdCopy.getSource());

            streetField.setText("still-bad");

            assertEquals("Main", person.getAddress().getStreet());
            assertEquals(1, addressModel.getMessages().size());
            assertEquals(1, personModel.getMessages().size());
            assertEquals(1, householdModel.getMessages().size());
            assertSame(owned, addressModel.getMessages().get(0));
            assertSame(personCopy, personModel.getMessages().get(0));
            assertSame(householdCopy, householdModel.getMessages().get(0));
            assertEquals("not a street: still-bad", owned.getText());
            assertEquals("not a street: still-bad", personCopy.getText());
            assertEquals("not a street: still-bad", householdCopy.getText());
            assertSame(householdCopy, householdModel.getLatestMessage());
        });
    }

    @Test
    void clearingTheAddressDropsTheCarriedNoticeAndANewAddressBringsItsOwn() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAddress(new Address("Main"));
            URL fxml = CarryChildMessageTest.class.getResource("plain-view.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            ViewModel personModel = loaded.getViewModel();
            ViewModel addressModel = (ViewModel) personModel.property("address").getValue();
            addressModel.addMessage(new Message(Message.Severity.INFO, "lane is closed", "street"));
            assertEquals("address.street", personModel.getMessages().get(0).getSource());

            @SuppressWarnings("unchecked")
            Property<Object> address = (Property<Object>) personModel.property("address");
            address.setValue(null);

            assertNull(person.getAddress());
            assertEquals(0, personModel.getMessages().size());
            assertNull(personModel.getLatestMessage());
            assertEquals(1, addressModel.getMessages().size());
            assertEquals("lane is closed", addressModel.getMessages().get(0).getText());

            Address moved = new Address("Oak");
            LoadedView movedView = ViewLoader.load(fxml, moved, context);
            movedView.getViewModel().addMessage(new Message(Message.Severity.WARN, "under repair", "street"));
            address.setValue(movedView.getViewModel());

            assertSame(moved, person.getAddress());
            assertEquals(1, personModel.getMessages().size());
            Message carried = personModel.getMessages().get(0);
            assertEquals("address.street", carried.getSource());
            assertEquals("under repair", carried.getText());
            assertEquals(Message.Severity.WARN, carried.getSeverity());
            assertSame(carried, personModel.getLatestMessage());
        });
    }

    @Test
    void fixingTheStreetLeavesThePersonsOwnNotice() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAddress(new Address("Main"));
            URL fxml = CarryChildMessageTest.class.getResource("address-street-converted.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            ViewModel personModel = loaded.getViewModel();
            personModel.addMessage(new Message(Message.Severity.INFO, "keep me", "form"));
            TextField streetField = (TextField) loaded.getRoot().lookup("#streetField");
            streetField.setText("nope");

            ViewModel addressModel = (ViewModel) personModel.property("address").getValue();
            assertEquals(1, addressModel.getMessages().size());
            assertEquals(2, personModel.getMessages().size());
            Message carried = personModel.getMessages().get(1);
            assertEquals("address.street", carried.getSource());
            assertEquals("not a street: nope", carried.getText());
            assertSame(carried, personModel.getLatestMessage());

            streetField.setText("still-bad");

            assertEquals(2, personModel.getMessages().size());
            assertSame(carried, personModel.getMessages().get(1));
            assertEquals("not a street: still-bad", carried.getText());
            assertSame(carried, personModel.getLatestMessage());
            assertEquals("keep me", personModel.getMessages().get(0).getText());

            streetField.setText("ok Oak");

            assertEquals("Oak", person.getAddress().getStreet());
            assertEquals(0, addressModel.getMessages().size());
            assertEquals(1, personModel.getMessages().size());
            assertEquals("keep me", personModel.getLatestMessage().getText());
            assertEquals("form", personModel.getLatestMessage().getSource());
        });
    }

    @Test
    void aFixedNestedStreetDropsTheNoticeUpThePath() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAddress(new Address("Main"));
            Household household = new Household();
            household.setPerson(person);
            URL fxml = CarryChildMessageTest.class.getResource("household-street-converted.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, household, context);
            TextField streetField = (TextField) loaded.getRoot().lookup("#streetField");
            streetField.setText("nope");
            streetField.setText("ok Oak");

            assertEquals("Oak", person.getAddress().getStreet());
            ViewModel householdModel = loaded.getViewModel();
            ViewModel personModel = (ViewModel) householdModel.property("person").getValue();
            ViewModel addressModel = (ViewModel) personModel.property("address").getValue();
            assertEquals(0, addressModel.getMessages().size());
            assertEquals(0, personModel.getMessages().size());
            assertEquals(0, householdModel.getMessages().size());
            assertNull(addressModel.getLatestMessage());
            assertNull(personModel.getLatestMessage());
            assertNull(householdModel.getLatestMessage());
        });
    }

    @Test
    void aSharedAddressCarriesTheStreetNoticeOnHomeAndOnWork() throws Exception {
        onFx(() -> {
            Address shared = new Address("Main");
            Person person = new Person("Ada");
            person.setHome(shared);
            person.setWork(shared);
            URL fxml = CarryChildMessageTest.class.getResource("plain-view.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            ViewModel personModel = loaded.getViewModel();
            ViewModel homeModel = (ViewModel) personModel.property("home").getValue();
            homeModel.addMessage(new Message(Message.Severity.INFO, "lane is closed", "street"));

            assertEquals(2, personModel.getMessages().size());
            Set<String> sources = new HashSet<>();
            for (Message message : personModel.getMessages()) {
                sources.add(message.getSource());
                assertEquals("lane is closed", message.getText());
            }
            assertEquals(Set.of("home.street", "work.street"), sources);
        });
    }

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

    @FunctionalInterface
    private interface FxAction {
        void run() throws Exception;
    }
}
