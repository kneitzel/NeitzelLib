package de.neitzel.fx.view;

import de.neitzel.fx.view.fixture.Person;
import de.neitzel.fx.view.readable.ReadableSourcesController.Sources;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DirectBinding reads declared observables toward the view. */
class BindReadOnlySourcesTest {
    private static final URL DOCUMENT = BindReadOnlySourcesTest.class.getResource("readable-sources.fxml");

    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        FxThread.start();
    }

    @Test
    void followsOnlyDependenciesOfTheViewModelSource() throws Exception {
        FxThread.onFx(() -> {
            LoadedView view = TestViews.load(DOCUMENT, new Person("Ada"), context());
            Sources sources = (Sources) view.getViewModel();
            Button commit = (Button) view.getRoot().lookup("#commit");
            Label title = (Label) view.getRoot().lookup("#title");
            Label caption = (Label) view.getRoot().lookup("#caption");
            TextField unrelated = (TextField) view.getRoot().lookup("#unrelated");

            assertTrue(commit.isDisable());
            assertEquals("First", title.getText());
            assertEquals("Caption: First", caption.getText());
            assertNull(view.property("title"));
            int evaluations = sources.getEvaluations();
            unrelated.setText("A change outside the binding's dependencies");
            assertEquals(evaluations, sources.getEvaluations());

            sources.getChoices().add("A");
            assertFalse(commit.isDisable());
            sources.getChoices().clear();
            assertTrue(commit.isDisable());
            assertEquals(evaluations + 2, sources.getEvaluations());

            title.setText("A local edit");
            assertEquals("First", sources.titleProperty().get());
            sources.setTitle("");
            assertEquals("", title.getText());
            assertEquals("Caption: First", caption.getText());
            assertEquals("observableTitle", sources.getLatestMessage().getSource());
            sources.setTitle("Second");
            assertEquals("Second", title.getText());
            assertEquals("Caption: Second", caption.getText());
            assertNull(sources.getLatestMessage());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"both", "model"})
    void identifiesAnInvalidWriteBackWithItsDocumentAndEndpoints(String direction, @TempDir Path temporary)
            throws Exception {
        Path invalid = temporary.resolve("invalid-" + direction + ".fxml");
        String fxml = Files.readString(Path.of(DOCUMENT.toURI()));
        Files.writeString(invalid, fxml.replace("path=\"observableTitle\" direction=\"view\"",
                "path=\"observableTitle\" direction=\"" + direction + "\""));
        FxThread.onFx(() -> {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> TestViews.load(invalid.toUri().toURL(), new Person("Ada"), context()));
            assertTrue(failure.getMessage().contains("Read-only source"), failure.getMessage());
            assertTrue(failure.getMessage().contains("observableTitle"), failure.getMessage());
            assertTrue(failure.getMessage().contains("caption.text"), failure.getMessage());
            assertTrue(failure.getMessage().contains(invalid.getFileName().toString()), failure.getMessage());
        });
    }

    private static TestViews context() {
        return new TestViews();
    }
}
