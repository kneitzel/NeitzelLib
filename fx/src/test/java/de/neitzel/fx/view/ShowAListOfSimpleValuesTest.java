package de.neitzel.fx.view;

import de.neitzel.fx.view.fixture.Person;
import de.neitzel.injection.ApplicationContext;
import javafx.application.Platform;
import javafx.scene.control.ListView;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A list of simple values is one shared list on the control and the model.
 */
class ShowAListOfSimpleValuesTest {

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
     * List view and person share the tag list.
     */
    @Test
    void listViewAndPersonShareTheTagList() throws Exception {
        onFx(() -> {
            Person person = new Person("Ada");
            person.setTags(new ArrayList<>(List.of("red", "blue")));
            URL fxml = ShowAListOfSimpleValuesTest.class.getResource("tags-both-ways.fxml");
            assertNotNull(fxml);
            ApplicationContext context = new ApplicationContext("de.neitzel.fx.view.fixture");
            LoadedView loaded = ViewLoader.load(fxml, person, context);
            VBox root = (VBox) loaded.getRoot();
            assertEquals(1, root.getChildren().size());
            @SuppressWarnings("unchecked")
            ListView<String> tags = (ListView<String>) root.lookup("#tags");
            assertEquals(List.of("red", "blue"), List.copyOf(tags.getItems()));
            assertEquals("red", tags.getItems().get(0));
            assertEquals("blue", tags.getItems().get(1));
            assertSame(person.getTags(), tags.getItems());
            tags.getItems().add("green");
            assertEquals(List.of("red", "blue", "green"), List.copyOf(person.getTags()));
            person.getTags().add("gold");
            assertEquals(List.of("red", "blue", "green", "gold"), List.copyOf(tags.getItems()));
            tags.getItems().remove("blue");
            assertEquals(List.of("red", "green", "gold"), List.copyOf(person.getTags()));
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
