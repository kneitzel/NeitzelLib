package de.neitzel.fx.injection;

import de.neitzel.fx.i18n.Localizer;
import de.neitzel.fx.injection.fixture.Card;
import de.neitzel.fx.injection.fixture.Controller;
import de.neitzel.fx.injection.fixture.Entry;
import de.neitzel.fx.injection.fixture.Lifecycle;
import de.neitzel.fx.test.FxFixture;
import de.neitzel.fx.test.FxTest;
import de.neitzel.fx.view.ViewLoader;
import de.neitzel.fx.view.ViewModelContext;
import de.neitzel.injection.ApplicationContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@FxTest
class NeitzelViewDependenciesTest {
    @Getter
    @Setter
    public static final class Model {
        private String name = "Ada";
        private List<Entry> entries = new ArrayList<>(List.of(new Entry()));
    }

    @Test
    void loadsRepeatTableAndStandaloneComponentWithOneApplicationContext(FxFixture fx) throws Exception {
        fx.run(() -> {
            var context = new ApplicationContext(Controller.class);
            var lifecycle = context.getComponent(Lifecycle.class);
            context.addSingleton(Localizer.class, new Localizer(Locale.ENGLISH, Locale.ENGLISH,
                    new Localizer.Catalog(Controller.class, "View")));
            var dependencies = new NeitzelViewDependencies(context, Set.of(Card.class));
            var cached = context.getComponent(Card.class);
            assertSame(cached, context.getComponent(VBox.class));
            assertFalse(dependencies.isComponent(VBox.class));
            try (var models = new ViewModelContext()) {
                var model = new Model();
                var fxml = Controller.class.getResource("View.fxml");
                var first = ViewLoader.loadWithDependencies(fxml, model, dependencies, models);
                var second = ViewLoader.loadWithDependencies(fxml, new Model(), dependencies, models);
                assertEquals(VBox.class, first.getRoot().getClass());
                var caption = (Label) first.getRoot().lookup("#caption");
                assertEquals("[Ada]", caption.getText());
                assertEquals("Localized", caption.getAccessibleText());
                var controller = (Controller) caption.getUserData();
                var other = (Controller) second.getRoot().lookup("#caption").getUserData();
                assertNotSame(controller, other);
                assertSame(lifecycle, controller.getLifecycle());
                assertSame(lifecycle, other.getLifecycle());
                var embedded = (Card) first.getRoot().lookup("#card");
                var repeated = (Card) ((VBox) first.getRoot().lookup("#repeat")).getChildren().getFirst();
                var table = first.getRoot().lookup("#table");
                var rows = (VBox) ((ScrollPane) table.lookup(".table-scroll")).getContent();
                var row = (Card) rows.getChildren().getFirst();
                assertNotSame(cached, embedded);
                assertNotSame(embedded, repeated);
                assertNotSame(repeated, row);
                model.getEntries().clear();
                assertEquals(1, lifecycle.closeCount(repeated));
                assertEquals(1, lifecycle.closeCount(row));

                var separate = ViewLoader.loadComponentWithDependencies(Card.class, new Entry(), dependencies, models);
                assertEquals("Ada", ((Label) separate.getRoot().lookup("#name")).getText());
                separate.close();
                assertEquals(1, lifecycle.closeCount(separate.getRoot()));
                first.close();
                first.close();
                assertEquals(1, lifecycle.closeCount(embedded));
                assertEquals(1, lifecycle.closeCount(controller));
                assertEquals(0, lifecycle.closeCount(other));
                assertSame(cached, context.getComponent(Card.class));
            }
        });
    }

    @Test
    void rollsBackAndDistinguishesMissingServicesFromOverrides(FxFixture fx) throws Exception {
        fx.run(() -> {
            var failedLookup = new IllegalArgumentException("Lookup override failed");
            var context = new ApplicationContext(Controller.class) {
                @Override
                public <T> T getComponent(Class<? extends T> type) {
                    if (type == String.class) return type.cast("override");
                    if (type == Integer.class) throw failedLookup;
                    return super.getComponent(type);
                }
            };
            var dependencies = new NeitzelViewDependencies(context, Set.of(Card.class));
            assertTrue(dependencies.findService(Localizer.class).isEmpty());
            assertEquals("override", dependencies.findService(String.class).orElseThrow());
            assertSame(failedLookup, assertThrows(IllegalArgumentException.class,
                    () -> dependencies.findService(Integer.class)));
            var acquired = new ArrayList<>();
            var tracking = new de.neitzel.fx.view.ViewDependencies() {
                public boolean isComponent(Class<?> type) { return dependencies.isComponent(type); }

                public <T> java.util.Optional<T> findService(Class<T> type) {
                    return dependencies.findService(type);
                }

                public <T> de.neitzel.fx.view.ViewObject<T> createViewObject(Class<T> type) {
                    var found = dependencies.createViewObject(type);
                    acquired.add(found.instance());
                    return found;
                }
            };
            try (var models = new ViewModelContext()) {
                assertThrows(IllegalStateException.class, () -> ViewLoader.loadWithDependencies(
                        Controller.class.getResource("Invalid.fxml"), new Model(), tracking, models));
            }
            var lifecycle = context.getComponent(Lifecycle.class);
            assertEquals(2, acquired.size());
            assertTrue(acquired.stream().allMatch(value -> lifecycle.closeCount(value) == 1));
        });
    }
}
