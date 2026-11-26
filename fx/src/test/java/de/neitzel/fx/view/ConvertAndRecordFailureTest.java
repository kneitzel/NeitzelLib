package de.neitzel.fx.view;

import de.neitzel.fx.view.fixture.Person;
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

/**
 * A converter failure keeps the previous value and records one error message.
 */
class ConvertAndRecordFailureTest {

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
     * Injected converter prefixes the age text.
     */
    @Test
    void injectedConverterPrefixesTheAgeText() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAge(42);
            URL fxml = ConvertAndRecordFailureTest.class.getResource("age-both-converted.fxml");
            assertNotNull(fxml);
            TestViews context = new TestViews();
            LoadedView loaded = TestViews.load(fxml, person, context);
            TextField ageField = (TextField) loaded.getRoot().lookup("#ageField");
            assertEquals("years:42", ageField.getText());
        });
    }

    /**
     * Typing a prefixed age updates the model.
     */
    @Test
    void typingAPrefixedAgeUpdatesTheModel() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAge(42);
            URL fxml = ConvertAndRecordFailureTest.class.getResource("age-both-converted.fxml");
            assertNotNull(fxml);
            TestViews context = new TestViews();
            LoadedView loaded = TestViews.load(fxml, person, context);
            TextField ageField = (TextField) loaded.getRoot().lookup("#ageField");
            ageField.setText("years:11");
            assertEquals(11, person.getAge());
        });
    }

    /**
     * Toward view loads when the reverse method is absent.
     */
    @Test
    void towardViewLoadsWhenTheReverseMethodIsAbsent() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAge(42);
            URL fxml = ConvertAndRecordFailureTest.class.getResource("age-view-converted.fxml");
            assertNotNull(fxml);
            TestViews context = new TestViews();
            LoadedView loaded = TestViews.load(fxml, person, context);
            Label ageLabel = (Label) loaded.getRoot().lookup("#ageLabel");
            assertEquals("shown 42", ageLabel.getText());
            ageLabel.setText("shown 99");
            assertEquals(42, person.getAge());
        });
    }

    /**
     * Toward view converter follows a later age.
     */
    @Test
    void towardViewConverterFollowsALaterAge() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAge(42);
            URL fxml = ConvertAndRecordFailureTest.class.getResource("age-both-and-view-converted.fxml");
            assertNotNull(fxml);
            TestViews context = new TestViews();
            LoadedView loaded = TestViews.load(fxml, person, context);
            TextField ageField = (TextField) loaded.getRoot().lookup("#ageField");
            Label ageLabel = (Label) loaded.getRoot().lookup("#ageLabel");
            assertEquals("shown 42", ageLabel.getText());
            ageField.setText("years:11");
            assertEquals(11, person.getAge());
            assertEquals("shown 11", ageLabel.getText());
        });
    }

    /**
     * Toward model converts typed text without following the attribute.
     */
    @Test
    void towardModelConvertsTypedTextWithoutFollowingTheAttribute() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAge(42);
            URL fxml = ConvertAndRecordFailureTest.class.getResource("age-model-converted.fxml");
            assertNotNull(fxml);
            TestViews context = new TestViews();
            LoadedView loaded = TestViews.load(fxml, person, context);
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

    /**
     * Bad age text keeps the previous int and one error message.
     */
    @Test
    void badAgeTextKeepsThePreviousIntAndOneErrorMessage() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAge(42);
            URL fxml = ConvertAndRecordFailureTest.class.getResource("age-both-converted.fxml");
            assertNotNull(fxml);
            TestViews context = new TestViews();
            LoadedView loaded = TestViews.load(fxml, person, context);
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

    /**
     * Another bad age updates the same message.
     */
    @Test
    void anotherBadAgeUpdatesTheSameMessage() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAge(42);
            URL fxml = ConvertAndRecordFailureTest.class.getResource("age-both-converted.fxml");
            assertNotNull(fxml);
            TestViews context = new TestViews();
            LoadedView loaded = TestViews.load(fxml, person, context);
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

    /**
     * A valid age removes the failure message.
     */
    @Test
    void aValidAgeRemovesTheFailureMessage() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAge(42);
            URL fxml = ConvertAndRecordFailureTest.class.getResource("age-both-converted.fxml");
            assertNotNull(fxml);
            TestViews context = new TestViews();
            LoadedView loaded = TestViews.load(fxml, person, context);
            TextField ageField = (TextField) loaded.getRoot().lookup("#ageField");
            ageField.setText("nope");
            ageField.setText("years:11");
            assertEquals(Integer.valueOf(11), person.getAge());
            assertEquals("years:11", ageField.getText());
            assertEquals(0, loaded.getViewModel().getMessages().size());
            assertNull(loaded.getViewModel().getLatestMessage());
        });
    }

    /**
     * Developer info stays latest and is not logged.
     */
    @Test
    void developerInfoStaysLatestAndIsNotLogged() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAge(42);
            URL fxml = ConvertAndRecordFailureTest.class.getResource("age-both-converted.fxml");
            assertNotNull(fxml);
            TestViews context = new TestViews();
            LoadedView loaded = TestViews.load(fxml, person, context);
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

    /**
     * Error and fatal messages are written to the log.
     */
    @Test
    void errorAndFatalMessagesAreWrittenToTheLog() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAge(42);
            URL fxml = ConvertAndRecordFailureTest.class.getResource("age-both-converted.fxml");
            assertNotNull(fxml);
            TestViews context = new TestViews();
            LoadedView loaded = TestViews.load(fxml, person, context);
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
     * Runs one check and returns what it wrote to standard error.
     *
     * @param action the check
     * @return the text written to standard error
     */
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
