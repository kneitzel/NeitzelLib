package de.neitzel.fx.view;

import de.neitzel.fx.view.fixture.Address;
import de.neitzel.fx.view.fixture.Host;
import de.neitzel.fx.view.fixture.Person;
import de.neitzel.fx.view.fixture.Visit;
import de.neitzel.fx.view.managed.ViewController;
import de.neitzel.injection.ApplicationContext;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManagedViewTest {

    @BeforeAll
    static void start() throws InterruptedException {
        FxThread.start();
    }

    @Test
    void sharesViewModelWithinContextAndClosesOnlyTheRequestedView() throws Exception {
        FxThread.onFx(() -> {
            var model = new Person("Ada");
            var injection = new ApplicationContext("de.neitzel.fx.view.managed");
            try (var models = new ViewModelContext(); var otherModels = new ViewModelContext()) {
                var first = ViewLoader.load(fxml("managed-view.fxml"), model, injection, models);
                var second = ViewLoader.load(fxml("managed-view.fxml"), model, injection, models);
                var other = ViewLoader.load(fxml("managed-view.fxml"), model, injection, otherModels);
                assertSame(first.getViewModel(), second.getViewModel());
                assertNotSame(first.getViewModel(), other.getViewModel());

                var firstInput = field(first.getRoot(), "#name");
                var secondInput = field(second.getRoot(), "#name");
                assertNotSame(firstInput, secondInput);
                var firstController = (ViewController) firstInput.getUserData();
                var secondController = (ViewController) secondInput.getUserData();
                assertNotSame(firstController, secondController);

                firstInput.setText("Grace");
                assertEquals("Grace", secondInput.getText());
                first.close();
                first.close();

                firstInput.setText("Detached");
                assertEquals("Grace", model.getName());
                secondInput.setText("Lin");
                assertEquals("Lin", model.getName());
                assertEquals("Detached", firstInput.getText());
                assertEquals(1, firstController.getCloseCount());
                assertEquals(0, secondController.getCloseCount());
                models.close();

                secondInput.setText("Also detached");
                assertEquals("Lin", model.getName());
                assertEquals(1, secondController.getCloseCount());
                assertThrows(IllegalStateException.class,
                        () -> ViewLoader.load(fxml("managed-view.fxml"), model, injection, models));
            }
        });
    }

    @Test
    void failedBindingClosesControllerAndRollsBackEarlierBinding() throws Exception {
        FxThread.onFx(() -> {
            var model = new Person("Ada");
            var injection = new ApplicationContext("de.neitzel.fx.view.managed");
            try (var models = new ViewModelContext()) {
                var registry = injection.getComponent(ViewController.Registry.class);
                assertThrows(IllegalArgumentException.class,
                        () -> ViewLoader.load(fxml("managed-invalid.fxml"), model, injection, models));
                var abandoned = registry.getLastCreated();
                assertEquals(1, abandoned.getCloseCount());
                assertNull(abandoned.getInput().getOnAction());
                abandoned.getInput().setText("Detached");
                assertEquals("Ada", model.getName());

                var loaded = ViewLoader.load(fxml("managed-view.fxml"), model, injection, models);
                field(loaded.getRoot(), "#name").setText("Working");
                assertEquals("Detached", abandoned.getInput().getText());
                assertEquals("Working", model.getName());
            }
        });
    }

    @Test
    void removingARepeatChildDetachesItsBindings() throws Exception {
        FxThread.onFx(() -> {
            var person = new Person("Ada");
            var first = new Address("Main");
            var second = new Address("Water");
            person.setAddresses(new ArrayList<>(List.of(first, second)));
            try (var models = new ViewModelContext()) {
                var context = new ApplicationContext("de.neitzel.fx.view.component");
                var loaded = ViewLoader.load(fxml("repeat-addresses.fxml"), person, context, models);
                var pane = (VBox) loaded.getRoot().lookup("#addressPane");
                var detached = field(pane.getChildren().getFirst(), "#streetField");
                var active = field(pane.getChildren().getLast(), "#streetField");
                person.getAddresses().removeFirst();
                detached.setText("Detached");
                active.setText("Still active");
                assertEquals("Main", first.getStreet());
                assertEquals("Still active", second.getStreet());

                loaded.close();
                person.getAddresses().add(new Address("New"));
                assertEquals(0, pane.getChildren().size());
                active.setText("Also detached");
                assertEquals("Still active", second.getStreet());
            }
        });
    }

    @Test
    void removingATableRowDetachesItsBindings() throws Exception {
        FxThread.onFx(() -> {
            var host = new Host();
            var visit = new Visit("Main", "Darmstadt");
            host.setVisits(new ArrayList<>(List.of(visit)));
            try (var models = new ViewModelContext()) {
                var context = new ApplicationContext("de.neitzel.fx.view.component");
                var loaded = ViewLoader.load(fxml("show-visits.fxml"), host, context, models);
                var table = (Table) loaded.getRoot().lookup("#visitsTable");
                var scroll = (ScrollPane) table.lookup("." + Table.SCROLL_STYLE);
                var input = field(scroll.getContent(), "#streetField");
                host.getVisits().clear();
                input.setText("Detached");
                assertEquals("Main", visit.getStreet());

                loaded.close();
                host.getVisits().add(visit);
                assertEquals(0, ((VBox) scroll.getContent()).getChildren().size());
            }
        });
    }

    private static URL fxml(String path) {
        return ManagedViewTest.class.getResource(path);
    }

    private static TextField field(javafx.scene.Node root, String id) {
        return (TextField) root.lookup(id);
    }
}
