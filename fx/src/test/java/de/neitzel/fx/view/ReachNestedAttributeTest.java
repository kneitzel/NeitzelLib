package de.neitzel.fx.view;

import de.neitzel.fx.view.fixture.Address;
import de.neitzel.fx.view.fixture.ObjectHolder;
import de.neitzel.fx.view.fixture.Person;
import de.neitzel.fx.view.fixture.Role;
import de.neitzel.injection.ApplicationContext;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

class ReachNestedAttributeTest {

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
    void complexAttributeHoldsTheViewModelForTheNestedAddress() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAddress(new Address("Main"));
            URL fxml = ReachNestedAttributeTest.class.getResource("name-both-ways.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            Object addressValue = loaded.property("address").getValue();
            assertInstanceOf(ViewModel.class, addressValue);
            assertEquals("Main", ((ViewModel) addressValue).property("street").getValue());
        });
    }

    @Test
    void bothWaysTextFieldReadsAndWritesTheNestedStreet() throws Exception {
        onFx(() -> {
            Address address = new Address("Main");
            Person person = new Person("Ada");
            person.setAddress(address);
            URL fxml = ReachNestedAttributeTest.class.getResource("address-street-both.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            TextField streetField = (TextField) loaded.getRoot().lookup("#streetField");
            assertEquals("Main", streetField.getText());
            streetField.setText("Oak");
            assertEquals("Oak", address.getStreet());
        });
    }

    @Test
    void towardViewLabelFollowsTheNestedStreetAndDoesNotWrite() throws Exception {
        onFx(() -> {
            Address address = new Address("Main");
            Person person = new Person("Ada");
            person.setAddress(address);
            URL fxml = ReachNestedAttributeTest.class.getResource("address-street-both-and-view.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            TextField streetField = (TextField) loaded.getRoot().lookup("#streetField");
            Label streetLabel = (Label) loaded.getRoot().lookup("#streetLabel");
            assertEquals("Main", streetLabel.getText());
            streetField.setText("Oak");
            assertEquals("Oak", streetLabel.getText());
            assertEquals("Oak", address.getStreet());
            streetLabel.setText("Side");
            assertEquals("Oak", address.getStreet());
        });
    }

    @Test
    void bothWaysBindingOnAComplexPathYieldsTheModelInstance() throws Exception {
        onFx(() -> {
            Address address = new Address("Main");
            Person person = new Person("Ada");
            person.setAddress(address);
            URL fxml = ReachNestedAttributeTest.class.getResource("address-object.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            ObjectHolder addressHolder = (ObjectHolder) loaded.getRoot().lookup("#addressHolder");
            assertSame(address, addressHolder.getValue());
            Address moved = new Address("Oak");
            addressHolder.setValue(moved);
            assertSame(moved, person.getAddress());
        });
    }

    @Test
    void fillingAMissingAddressStoresTheViewModelAndWritesTheModel() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL fxml = ReachNestedAttributeTest.class.getResource("address-object.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            ObjectHolder addressHolder = (ObjectHolder) loaded.getRoot().lookup("#addressHolder");
            assertNull(addressHolder.getValue());
            assertNull(person.getAddress());
            Address address = new Address("Main");
            addressHolder.setValue(address);
            assertSame(address, person.getAddress());
            Object addressValue = loaded.property("address").getValue();
            assertInstanceOf(ViewModel.class, addressValue);
            assertEquals("Main", ((ViewModel) addressValue).property("street").getValue());
        });
    }

    @Test
    void missingAddressLeavesTheControlAndTheModelUnchanged() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL fxml = ReachNestedAttributeTest.class.getResource("address-street-kept.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            TextField streetField = (TextField) loaded.getRoot().lookup("#streetField");
            assertEquals("kept", streetField.getText());
            assertNull(person.getAddress());
        });
    }

    @Test
    void nullComplexAttributeHoldsNull() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL fxml = ReachNestedAttributeTest.class.getResource("name-both-ways.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            assertNull(loaded.property("address").getValue());
        });
    }

    @Test
    void attributesThatReachTheSameAddressShareItsViewModel() throws Exception {
        onFx(() -> {
            Address shared = new Address("Main");
            Person person = new Person("Ada");
            person.setHome(shared);
            person.setWork(shared);
            URL fxml = ReachNestedAttributeTest.class.getResource("home-work-street.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            assertSame(loaded.property("home").getValue(), loaded.property("work").getValue());
            TextField homeField = (TextField) loaded.getRoot().lookup("#homeField");
            TextField workField = (TextField) loaded.getRoot().lookup("#workField");
            homeField.setText("Oak");
            assertEquals("Oak", workField.getText());
            assertEquals("Oak", shared.getStreet());
        });
    }

    @Test
    void equalAddressesKeepSeparateViewModels() throws Exception {
        onFx(() -> {
            Address home = new Address("Main");
            Address work = new Address("Main");
            assertEquals(home, work);
            Person person = new Person("Ada");
            person.setHome(home);
            person.setWork(work);
            URL fxml = ReachNestedAttributeTest.class.getResource("home-work-street.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            assertNotSame(loaded.property("home").getValue(), loaded.property("work").getValue());
            TextField homeField = (TextField) loaded.getRoot().lookup("#homeField");
            TextField workField = (TextField) loaded.getRoot().lookup("#workField");
            homeField.setText("Oak");
            assertEquals("Oak", home.getStreet());
            assertEquals("Main", work.getStreet());
            assertEquals("Main", workField.getText());
        });
    }

    @Test
    void aPersonWhoIsTheirOwnFriendReusesTheSameViewModel() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setFriend(person);
            URL fxml = ReachNestedAttributeTest.class.getResource("name-both-ways.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            assertSame(loaded.getViewModel(), loaded.property("friend").getValue());
        });
    }

    @Test
    void twoPeopleWhoPointAtEachOtherReuseTheViewModelsAlreadyBuilt() throws Exception {
        onFx(() -> {
            Person ada = new Person("Ada");
            Person grace = new Person("Grace");
            ada.setFriend(grace);
            grace.setFriend(ada);
            URL fxml = ReachNestedAttributeTest.class.getResource("name-both-ways.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, ada, context);
            ViewModel graceModel = (ViewModel) loaded.property("friend").getValue();
            assertEquals("Grace", graceModel.property("name").getValue());
            assertSame(loaded.getViewModel(), graceModel.property("friend").getValue());
        });
    }

    @Test
    void enumsBooleansAndDateValuesStaySimpleValues() throws Exception {
        onFx(() -> {
            Date updated = new Date(0L);
            Calendar hired = new GregorianCalendar(2020, Calendar.JANUARY, 2);
            LocalDate born = LocalDate.of(1815, 12, 10);
            Person person = new Person("Ada");
            person.setActive(true);
            person.setRole(Role.ADMIN);
            person.setBorn(born);
            person.setUpdated(updated);
            person.setHired(hired);
            URL fxml = ReachNestedAttributeTest.class.getResource("name-both-ways.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            assertEquals(true, loaded.property("active").getValue());
            assertSame(Role.ADMIN, loaded.property("role").getValue());
            assertEquals(born, loaded.property("born").getValue());
            assertSame(updated, loaded.property("updated").getValue());
            assertSame(hired, loaded.property("hired").getValue());
        });
    }

    @Test
    void aCollectionStaysTheCollectionRatherThanAViewModel() throws Exception {
        onFx(() -> {
            List<String> tags = new ArrayList<>();
            tags.add("friend");
            Person person = new Person("Ada");
            person.setTags(tags);
            URL fxml = ReachNestedAttributeTest.class.getResource("name-both-ways.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            assertInstanceOf(ObservableList.class, loaded.property("tags").getValue());
            @SuppressWarnings("unchecked")
            ObservableList<String> shown = (ObservableList<String>) loaded.property("tags").getValue();
            assertEquals(List.of("friend"), shown);
            assertSame(person.getTags(), shown);
            shown.add("kin");
            assertEquals(List.of("friend", "kin"), tags);
        });
    }

    @Test
    void twoLoadsOfTheSamePersonShareTheViewModel() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL fxml = ReachNestedAttributeTest.class.getResource("name-both-ways.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView first = ViewLoader.load(fxml, person, context);
            LoadedView second = ViewLoader.load(fxml, person, context);
            assertSame(first.getViewModel(), second.getViewModel());
            TextField firstName = (TextField) first.getRoot().lookup("#nameField");
            TextField secondName = (TextField) second.getRoot().lookup("#nameField");
            firstName.setText("Grace");
            assertEquals("Grace", secondName.getText());
            assertEquals("Grace", person.getName());
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
