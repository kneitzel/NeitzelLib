package de.neitzel.fx.view;

import de.neitzel.fx.view.fixture.Person;
import de.neitzel.injection.ApplicationContext;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A message stays on the view model until the developer asks for a dialog.
 */
class ShowMessagesWhenAskedTest {

    /**
     * Starts the JavaFX toolkit once for this class.
     */
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        Platform.setImplicitExit(false);
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
     * Adding a message does not open a popup or fill a status bar.
     */
    @Test
    void addingAMessageDoesNotOpenAPopupOrFillAStatusBar() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL fxml = ShowMessagesWhenAskedTest.class.getResource("name-both-ways.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            Parent root = loaded.getRoot();
            Set<Node> nodesBefore = new HashSet<>(root.lookupAll("*"));
            Set<Window> windowsBefore = snapshotWindows();
            loaded.getViewModel().addMessage(
                    new Message(Message.Severity.INFO, "saved the person", "form"));
            assertEquals(windowsBefore, snapshotWindows());
            assertEquals(nodesBefore, new HashSet<>(root.lookupAll("*")));
            assertMessageIsNotShown(root, "saved the person");
        });
    }

    /**
     * Direct binding shows the latest message text.
     */
    @Test
    void directBindingShowsTheLatestMessageText() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL fxml = ShowMessagesWhenAskedTest.class.getResource("latest-message.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            Label messageLabel = (Label) loaded.getRoot().lookup("#messageLabel");
            loaded.getViewModel().addMessage(
                    new Message(Message.Severity.INFO, "saved the person", "form"));
            assertEquals("saved the person", messageLabel.getText());
            loaded.getViewModel().addMessage(
                    new Message(Message.Severity.WARN, "name is required", "name"));
            assertEquals("name is required", messageLabel.getText());
        });
    }

    /**
     * Opted in helper shows a new message in a popup.
     */
    @Test
    void optedInHelperShowsANewMessageInAPopup() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            URL fxml = ShowMessagesWhenAskedTest.class.getResource("name-both-ways.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            Set<Window> windowsBefore = snapshotWindows();
            MessagePopup.showNewMessages(loaded.getViewModel());
            assertEquals(windowsBefore, snapshotWindows());
            try {
                loaded.getViewModel().addMessage(
                        new Message(Message.Severity.INFO, "saved the person", "form"));
                assertNotNull(windowShowing("saved the person", windowsBefore));
                assertMessageIsNotShown(loaded.getRoot(), "saved the person");
            } finally {
                closeWindowsOpenedSince(windowsBefore);
            }
        });
    }

    /**
     * Updating a binding failure does not open another popup.
     */
    @Test
    void updatingABindingFailureDoesNotOpenAnotherPopup() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setAge(42);
            URL fxml = ShowMessagesWhenAskedTest.class.getResource("age-both-converted.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            TextField ageField = (TextField) loaded.getRoot().lookup("#ageField");
            Set<Window> windowsBefore = snapshotWindows();
            MessagePopup.showNewMessages(loaded.getViewModel());
            try {
                ageField.setText("nope");
                ViewModel viewModel = loaded.getViewModel();
                Message failure = viewModel.getLatestMessage();
                assertEquals("not a whole number: nope", failure.getText());
                assertEquals(1, windowsOpenedSince(windowsBefore).size());
                assertNotNull(windowShowing("not a whole number: nope", windowsBefore));

                ageField.setText("still-bad");
                assertEquals(1, viewModel.getMessages().size());
                assertSame(failure, viewModel.getLatestMessage());
                assertEquals("not a whole number: still-bad", failure.getText());
                assertEquals(1, windowsOpenedSince(windowsBefore).size());

                viewModel.addMessage(new Message(Message.Severity.INFO, "saved the person", "form"));
                assertEquals(2, windowsOpenedSince(windowsBefore).size());
                assertNotNull(windowShowing("saved the person", windowsBefore));
                assertEquals("saved the person", viewModel.getLatestMessage().getText());
            } finally {
                closeWindowsOpenedSince(windowsBefore);
            }
        });
    }

    /**
     * Returns the windows open before a check.
     *
     * @return the windows that are open
     */
    private static Set<Window> snapshotWindows() {
        return new HashSet<>(Window.getWindows());
    }

    /**
     * Returns the window opened during a check whose content is the given text.
     *
     * @param text the text
     * @param alreadyOpen the windows open before the check
     * @return the window, or {@code null} when none shows the text
     */
    private static Window windowShowing(String text, Set<Window> alreadyOpen) {
        for (Window window : Window.getWindows()) {
            if (!alreadyOpen.contains(window) && windowShows(window, text)) {
                return window;
            }
        }
        return null;
    }

    /**
     * Returns whether a window's scene shows the given text.
     *
     * @param window the window
     * @param text the text
     * @return {@code true} when the window shows the text
     */
    private static boolean windowShows(Window window, String text) {
        if (window.getScene() == null || !(window.getScene().getRoot() instanceof Parent root)) {
            return false;
        }
        if (root instanceof DialogPane pane && nodeShows(pane.getContent(), text)) {
            return true;
        }
        for (Node node : root.lookupAll("*")) {
            if (nodeShows(node, text)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether a labeled node shows the given text.
     *
     * @param node the node
     * @param text the text
     * @return {@code true} when the node shows the text
     */
    private static boolean nodeShows(Node node, String text) {
        return node instanceof Labeled labeled && text.equals(labeled.getText());
    }

    /**
     * Returns the windows that were not open in the earlier snapshot.
     *
     * @param alreadyOpen the windows open before the check
     * @return the windows opened since the snapshot
     */
    private static List<Window> windowsOpenedSince(Set<Window> alreadyOpen) {
        List<Window> opened = new ArrayList<>();
        for (Window window : Window.getWindows()) {
            if (!alreadyOpen.contains(window)) {
                opened.add(window);
            }
        }
        return opened;
    }

    /**
     * Closes the windows opened during a check.
     *
     * @param alreadyOpen the windows open before the check
     */
    private static void closeWindowsOpenedSince(Set<Window> alreadyOpen) {
        List<Window> open = new ArrayList<>(Window.getWindows());
        for (Window window : open) {
            if (!alreadyOpen.contains(window)) {
                window.hide();
            }
        }
    }

    /**
     * Fails when the given text is visible in the scene or in a dialog.
     *
     * @param root the loaded scene
     * @param text the text
     */
    private static void assertMessageIsNotShown(Parent root, String text) {
        for (Node node : root.lookupAll("*")) {
            if (node instanceof Labeled labeled) {
                assertNotEquals(text, labeled.getText());
            }
            if (node instanceof TextInputControl input) {
                assertNotEquals(text, input.getText());
            }
        }
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
