package de.neitzel.fx.view;

import de.neitzel.fx.view.command.CommandController;
import de.neitzel.injection.ApplicationContext;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.scene.control.Button;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindCommandTest {

    @TempDir
    Path directory;

    @BeforeAll
    static void start() throws InterruptedException {
        FxThread.start();
    }

    @Test
    void connectsOneCommandToButtonMenuAndKeyboardUntilClosed() throws Exception {
        var url = view("commands.fxml", """
                <CommandBinding control="button" path="clear"/>
                <CommandBinding control="item" path="clear"/>
                """);
        FxThread.onFx(() -> {
            try (var models = new ViewModelContext()) {
                var view = ViewLoader.load(url, new CommandController.Subject(), services(), models);
                var model = (CommandController.Model) view.getViewModel();
                var button = (Button) view.getRoot().lookup("#button");
                var menu = ((MenuBar) view.getRoot().lookup("#menu")).getMenus().getFirst();
                var item = menu.getItems().getFirst();
                var dynamic = new MenuItem();
                try (var registration = CommandBindings.bind(dynamic, model.getClear())) {
                    assertTrue(button.isDisable());
                    assertTrue(item.isDisable());
                    item.fire();
                    model.getClear().execute();
                    assertEquals(0, model.getCalls());

                    model.getEnabled().set(true);
                    assertFalse(button.isDisable());
                    assertFalse(item.isDisable());
                    button.fire();
                    item.fire();
                    model.getClear().execute();
                    dynamic.fire();
                    assertEquals(4, model.getCalls());
                }
                dynamic.fire();
                assertEquals(4, model.getCalls());

                view.close();
                assertNull(button.getOnAction());
                assertNull(item.getOnAction());
                model.getEnabled().set(false);
                assertFalse(button.isDisable());
                assertFalse(item.isDisable());
            }
        });
    }

    @Test
    void rejectsUnknownTargetsAndConflictingOwners() throws Exception {
        var scenarios = new String[][] {
            {"<CommandBinding control='unknown' path='clear'/>", "unknown"},
            {"<CommandBinding control='button' path='unknown'/>", "unknown"},
            {"<CommandBinding control='menu' path='clear'/>", "ButtonBase or MenuItem"},
            {"<CommandBinding control='button' path='clear'/><CommandBinding control='button' path='clear'/>", "duplicate"},
            {"<CommandBinding control='button' path='clear'/><ComputedBinding control='button' property='disable' method='#unused'/>", "disable"}
        };
        for (var scenario : scenarios) {
            var url = view("invalid-command.fxml", scenario[0]);
            FxThread.onFx(() -> {
                try (var models = new ViewModelContext()) {
                    var failure = assertThrows(IllegalArgumentException.class,
                            () -> ViewLoader.load(url, new CommandController.Subject(), services(), models));
                    assertTrue(failure.getMessage().contains("invalid-command.fxml"));
                    assertTrue(failure.getMessage().contains(scenario[1]));
                }
            });
        }
    }

    private ApplicationContext services() {
        return new ApplicationContext("de.neitzel.fx.view.command");
    }

    private java.net.URL view(String name, String bindings) throws Exception {
        return Files.writeString(directory.resolve(name), """
                <?import de.neitzel.fx.view.*?>
                <?import javafx.scene.control.*?>
                <?import javafx.scene.layout.VBox?>
                <VBox xmlns:fx="http://javafx.com/fxml/1" fx:controller="de.neitzel.fx.view.command.CommandController">
                    <fx:define><BindingList>%s</BindingList></fx:define>
                    <Button fx:id="button"/>
                    <MenuBar fx:id="menu"><menus><Menu><items><MenuItem fx:id="item"/></items></Menu></menus></MenuBar>
                </VBox>
                """.formatted(bindings)).toUri().toURL();
    }
}
