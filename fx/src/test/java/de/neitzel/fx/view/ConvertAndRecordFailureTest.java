package de.neitzel.fx.view;

import de.neitzel.fx.view.fixture.Person;
import de.neitzel.injection.ApplicationContext;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ConvertAndRecordFailureTest {

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
    void injectedConverterPrefixesTheAgeText() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAge(42);
            URL fxml = ConvertAndRecordFailureTest.class.getResource("age-both-converted.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            TextField ageField = (TextField) loaded.getRoot().lookup("#ageField");
            assertEquals("years:42", ageField.getText());
        });
    }

    @Test
    void typingAPrefixedAgeUpdatesTheModel() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAge(42);
            URL fxml = ConvertAndRecordFailureTest.class.getResource("age-both-converted.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            TextField ageField = (TextField) loaded.getRoot().lookup("#ageField");
            ageField.setText("years:11");
            assertEquals(11, person.getAge());
        });
    }

    @Test
    void towardViewLoadsWhenTheReverseMethodIsAbsent() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAge(42);
            URL fxml = ConvertAndRecordFailureTest.class.getResource("age-view-converted.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            Label ageLabel = (Label) loaded.getRoot().lookup("#ageLabel");
            assertEquals("shown 42", ageLabel.getText());
            ageLabel.setText("shown 99");
            assertEquals(42, person.getAge());
        });
    }

    @Test
    void towardViewConverterFollowsALaterAge() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAge(42);
            URL fxml = ConvertAndRecordFailureTest.class.getResource("age-both-and-view-converted.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            TextField ageField = (TextField) loaded.getRoot().lookup("#ageField");
            Label ageLabel = (Label) loaded.getRoot().lookup("#ageLabel");
            assertEquals("shown 42", ageLabel.getText());
            ageField.setText("years:11");
            assertEquals(11, person.getAge());
            assertEquals("shown 11", ageLabel.getText());
        });
    }

    @Test
    void towardModelConvertsTypedTextWithoutFollowingTheAttribute() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAge(42);
            URL fxml = ConvertAndRecordFailureTest.class.getResource("age-model-converted.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            TextField ageField = (TextField) loaded.getRoot().lookup("#ageField");
            assertEquals("years:42", ageField.getText());
            ((IntegerProperty) loaded.property("age")).set(7);
            assertEquals("years:42", ageField.getText());
            assertEquals(Integer.valueOf(7), person.getAge());
            ageField.setText("years:8");
            assertEquals(Integer.valueOf(8), person.getAge());
            assertEquals("years:8", ageField.getText());
        });
    }

    @Test
    void badAgeTextKeepsThePreviousIntAndOneErrorMessage() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAge(42);
            URL fxml = ConvertAndRecordFailureTest.class.getResource("age-both-converted.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            TextField ageField = (TextField) loaded.getRoot().lookup("#ageField");
            ageField.setText("nope");
            assertEquals(Integer.valueOf(42), person.getAge());
            assertEquals("nope", ageField.getText());
            ViewModel viewModel = loaded.getViewModel();
            assertEquals(1, viewModel.getMessages().size());
            Message message = viewModel.getMessages().get(0);
            assertEquals(Message.Severity.ERROR, message.getSeverity());
            assertEquals("age", message.getSource());
            assertEquals("not a whole number: nope", message.getText());
            assertSame(message, viewModel.getLatestMessage());
        });
    }

    @Test
    void anotherBadAgeUpdatesTheSameMessage() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAge(42);
            URL fxml = ConvertAndRecordFailureTest.class.getResource("age-both-converted.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            TextField ageField = (TextField) loaded.getRoot().lookup("#ageField");
            ageField.setText("nope");
            ViewModel viewModel = loaded.getViewModel();
            Message first = viewModel.getMessages().get(0);
            ageField.setText("still-bad");
            assertEquals(1, viewModel.getMessages().size());
            assertSame(first, viewModel.getMessages().get(0));
            assertEquals("not a whole number: still-bad", first.getText());
            assertEquals(Integer.valueOf(42), person.getAge());
            assertEquals("still-bad", ageField.getText());
        });
    }

    @Test
    void aValidAgeRemovesTheFailureMessage() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAge(42);
            URL fxml = ConvertAndRecordFailureTest.class.getResource("age-both-converted.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            TextField ageField = (TextField) loaded.getRoot().lookup("#ageField");
            ageField.setText("nope");
            ageField.setText("years:11");
            assertEquals(Integer.valueOf(11), person.getAge());
            assertEquals("years:11", ageField.getText());
            assertEquals(0, loaded.getViewModel().getMessages().size());
            assertNull(loaded.getViewModel().getLatestMessage());
        });
    }

    @Test
    void developerInfoStaysLatestAndIsNotLogged() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAge(42);
            URL fxml = ConvertAndRecordFailureTest.class.getResource("age-both-converted.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            TextField ageField = (TextField) loaded.getRoot().lookup("#ageField");
            ViewModel viewModel = loaded.getViewModel();
            ageField.setText("nope");
            String log = captureErr(() -> viewModel.addMessage(
                    new Message(Message.Severity.INFO, "developer-info-notice", "form")));
            assertEquals(2, viewModel.getMessages().size());
            assertEquals("developer-info-notice", viewModel.getLatestMessage().getText());
            assertEquals(Message.Severity.INFO, viewModel.getLatestMessage().getSeverity());
            assertFalse(log.contains("developer-info-notice"));
            ageField.setText("still-bad");
            assertEquals(2, viewModel.getMessages().size());
            assertEquals("not a whole number: still-bad", viewModel.getMessages().get(0).getText());
            assertEquals("developer-info-notice", viewModel.getLatestMessage().getText());
        });
    }

    @Test
    void errorAndFatalMessagesAreWrittenToTheLog() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAge(42);
            URL fxml = ConvertAndRecordFailureTest.class.getResource("age-both-converted.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            TextField ageField = (TextField) loaded.getRoot().lookup("#ageField");
            ViewModel viewModel = loaded.getViewModel();
            String log = captureErr(() -> {
                viewModel.addMessage(new Message(Message.Severity.WARN, "developer-warn-notice", "form"));
                viewModel.addMessage(new Message(Message.Severity.ERROR, "developer-error-notice", "form"));
                viewModel.addMessage(new Message(Message.Severity.FATAL, "developer-fatal-notice", "form"));
                ageField.setText("nope");
                ageField.setText("still-bad");
            });
            assertFalse(log.contains("developer-warn-notice"));
            assertTrue(log.contains("developer-error-notice"));
            assertTrue(log.contains("developer-fatal-notice"));
            assertTrue(log.contains("not a whole number: nope"));
            assertTrue(log.contains("not a whole number: still-bad"));
            assertEquals("not a whole number: still-bad", viewModel.getLatestMessage().getText());
            assertEquals(4, viewModel.getMessages().size());
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

    private static String captureErr(FxAction action) throws Exception {
        PrintStream previous = System.err;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream captured = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        System.setErr(captured);
        try {
            action.run();
            captured.flush();
            return buffer.toString(StandardCharsets.UTF_8);
        } finally {
            System.setErr(previous);
        }
    }

    @FunctionalInterface
    private interface FxAction {
        void run() throws Exception;
    }
}
