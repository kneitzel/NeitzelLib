package de.neitzel.fx.test;

import de.neitzel.fx.view.ViewDependencies;
import de.neitzel.fx.view.ViewObject;
import java.util.Optional;
import java.io.IOException;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@FxTest
public class FxFixtureTest {
    @Getter @Setter
    public static class Person {
        private String name = "Ada";
    }

    @Test
    void teardownDetachesLoadedViewsAndReportsUnhandledCallbacks(FxFixture fx) throws Exception {
        var person = new Person();
        var nested = new FxFixture();
        var problem = new IllegalStateException("callback failed");
        nested.start();
        var view = nested.loadWithDependencies(getClass().getResource("name.fxml"), person, new ViewDependencies() {
            public boolean isComponent(Class<?> type) { return false; }
            public <T> Optional<T> findService(Class<T> type) { return Optional.empty(); }
            public <T> ViewObject<T> createViewObject(Class<T> type) {
                throw new AssertionError("Unexpected view type: " + type);
            }
        });
        var field = nested.call(() -> (TextField) view.getRoot());
        var stage = nested.call(() -> {
            assertEquals("Ada", field.getText());
            field.setText("Lin");
            assertEquals("Lin", person.getName());
            var window = new Stage();
            window.setScene(new Scene(field));
            window.show();
            return window;
        });
        nested.run(() -> Platform.runLater(() -> { throw problem; }));
        assertSame(problem, assertThrows(AssertionError.class, nested::finish).getCause());

        fx.run(() -> {
            assertFalse(stage.isShowing());
            field.setText("Detached");
            assertEquals("Lin", person.getName());
        });
    }

    @Test
    void nestedCallsPropagateTheOriginalFailureAndCannotBlockTheFxThread(FxFixture fx) throws Exception {
        var problem = new IOException("failed");
        assertSame(problem, assertThrows(IOException.class, () -> fx.run(() -> {
            assertTrue(fx.call(Platform::isFxApplicationThread));
            assertThrows(IllegalStateException.class, () -> fx.await("next pulse", () -> false));
            throw problem;
        })));
    }
}
