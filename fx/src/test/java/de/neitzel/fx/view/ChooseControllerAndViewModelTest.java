package de.neitzel.fx.view;

import de.neitzel.fx.view.fixture.Address;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ChooseControllerAndViewModelTest {

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
    void labelShowsTheNameRecordedWhenTheViewModelWasHandedOver() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL fxml = ChooseControllerAndViewModelTest.class.getResource("handed-name.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.chosen");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            Label handedLabel = (Label) loaded.getRoot().lookup("#handedLabel");
            assertEquals("handed-Ada", handedLabel.getText());
        });
    }

    @Test
    void labelShowsTheGreetingInjectedIntoTheController() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL fxml = ChooseControllerAndViewModelTest.class.getResource("desk-greeting.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.chosen");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            Label deskLabel = (Label) loaded.getRoot().lookup("#deskLabel");
            assertEquals("from-the-desk", deskLabel.getText());
        });
    }

    @Test
    void labelShowsTheTitleFromTheNamedViewModel() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL fxml = ChooseControllerAndViewModelTest.class.getResource("titled-name.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.chosen");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            Label titleLabel = (Label) loaded.getRoot().lookup("#titleLabel");
            assertEquals("titled-Ada", titleLabel.getText());
            @SuppressWarnings("unchecked")
            Property<String> title = (Property<String>) loaded.property("title");
            assertNotNull(title);
            assertEquals("titled-Ada", title.getValue());
            title.setValue("titled-Grace");
            assertEquals("titled-Grace", titleLabel.getText());
        });
    }

    @Test
    void labelShowsTheBadgeFromAReplacementViewModel() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL fxml = ChooseControllerAndViewModelTest.class.getResource("badge-name.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.chosen");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            Label badgeLabel = (Label) loaded.getRoot().lookup("#badgeLabel");
            assertEquals("badge-Ada", badgeLabel.getText());
            @SuppressWarnings("unchecked")
            Property<String> badge = (Property<String>) loaded.property("badge");
            assertNotNull(badge);
            assertEquals("badge-Ada", badge.getValue());
            badge.setValue("badge-Grace");
            assertEquals("badge-Grace", badgeLabel.getText());
        });
    }

    @Test
    void componentLabelShowsTheCaptionFromTheNamedViewModel() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAddress(new Address("Main"));
            URL fxml = ChooseControllerAndViewModelTest.class.getResource("caption-address.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.chosen");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            Label captionLabel = (Label) loaded.getRoot().lookup("#captionLabel");
            assertEquals("caption-Main", captionLabel.getText());
        });
    }

    @Test
    void aLaterViewThatNamesADifferentClassFailsAndLeavesTheFirstView() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL titled = ChooseControllerAndViewModelTest.class.getResource("titled-name.fxml");
            URL badge = ChooseControllerAndViewModelTest.class.getResource("badge-name.fxml");
            assertNotNull(titled);
            assertNotNull(badge);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.chosen");
            LoadedView first = ViewLoader.load(titled, person, context);
            Label titleLabel = (Label) first.getRoot().lookup("#titleLabel");
            assertEquals("titled-Ada", titleLabel.getText());
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> ViewLoader.load(badge, person, context));
            assertTrue(failure.getMessage().contains("TitledViewModel"), failure.getMessage());
            assertTrue(failure.getMessage().contains("BadgeViewModel"), failure.getMessage());
            assertEquals("titled-Ada", titleLabel.getText());
        });
    }

    @Test
    void aLaterViewWithNoAnnotationReusesTheViewModel() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL named = ChooseControllerAndViewModelTest.class.getResource("named-person.fxml");
            URL plain = ChooseControllerAndViewModelTest.class.getResource("name-both-ways.fxml");
            assertNotNull(named);
            assertNotNull(plain);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.chosen");
            LoadedView first = ViewLoader.load(named, person, context);
            LoadedView second = ViewLoader.load(plain, person, context);
            TextField firstName = (TextField) first.getRoot().lookup("#nameField");
            TextField secondName = (TextField) second.getRoot().lookup("#nameField");
            assertEquals("Ada", firstName.getText());
            assertEquals("Ada", secondName.getText());
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
