package de.neitzel.fx.view;

import de.neitzel.fx.view.component.VisitRow;
import de.neitzel.fx.view.fixture.Host;
import de.neitzel.fx.view.fixture.Visit;
import de.neitzel.injection.ApplicationContext;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Column checklist shows only the confirmed values.
 */
class FilterByChecklistTest {

    /**
     * Starts the JavaFX toolkit once for this class.
     */
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        FxThread.start();
    }

    /**
     * Confirming a subset hides the rest, and the same Row comes back when the Filter is cleared.
     */
    @Test
    void confirmingASubsetHidesTheOtherRows() throws Exception {
        FxThread.onFx(() -> {
            Visit main = new Visit("Main", "Berlin");
            Visit oak = new Visit("Oak", "Bonn");
            Host host = new Host();
            host.setVisits(new ArrayList<>(List.of(main, oak)));
            host.setSelectedVisit(oak);
            LoadedView loaded = load(host);
            Table table = table(loaded);
            VisitRow oakRow = (VisitRow) body(table).getChildren().get(1);
            openCity(table);
            uncheck(table, "Bonn");
            command(table, "Confirm").fire();
            assertEquals(List.of("Main"), streets(table));
            assertTrue(opener(table, 1).getStyleClass().contains("filtered"));
            assertEquals(List.of("Main", "Oak"), streets(host.getVisits()));
            assertSame(oak, host.getSelectedVisit());
            assertFalse(body(table).getChildren().stream()
                    .anyMatch(node -> node.getStyleClass().contains(Table.SELECTED_STYLE)));
            openCity(table);
            command(table, "Clear filter").fire();
            assertSame(oakRow, body(table).getChildren().get(1));
            assertEquals(List.of("Main", "Oak"), streets(table));
            assertFalse(opener(table, 1).getStyleClass().contains("filtered"));
        });
    }

    /**
     * All checked is no Filter. A later value is shown. None checked hides every Row.
     */
    @Test
    void allCheckedLeavesNoFilterAndNoneCheckedHidesEveryRow() throws Exception {
        FxThread.onFx(() -> {
            Visit main = new Visit("Main", "Berlin");
            Host host = new Host();
            host.setVisits(new ArrayList<>(List.of(main, new Visit("Oak", null))));
            LoadedView loaded = load(host);
            Table table = table(loaded);
            openCity(table);
            List<String> labels = checks(table).stream().map(CheckBox::getText).toList();
            assertEquals(List.of("", "Berlin"), labels);
            command(table, "Confirm").fire();
            assertFalse(opener(table, 1).getStyleClass().contains("filtered"));
            @SuppressWarnings("unchecked")
            ObservableList<Visit> visits = (ObservableList<Visit>) loaded.property("visits").getValue();
            visits.add(new Visit("Elm", "Hamburg"));
            assertEquals(List.of("Main", "Oak", "Elm"), streets(table));
            openCity(table);
            for (CheckBox box : checks(table)) {
                box.setSelected(false);
            }
            command(table, "Confirm").fire();
            assertEquals(List.of(), streets(table));
            assertTrue(opener(table, 1).getStyleClass().contains("filtered"));
            visits.add(new Visit("Pine", "Kiel"));
            assertEquals(List.of(), streets(table));
        });
    }

    /**
     * An edit that fails the Filter hides that Row and keeps the new value on the entity.
     */
    @Test
    void anEditThatFailsTheFilterHidesTheRow() throws Exception {
        FxThread.onFx(() -> {
            Visit main = new Visit("Main", "Berlin");
            Host host = new Host();
            host.setVisits(new ArrayList<>(List.of(main, new Visit("Oak", "Bonn"))));
            Table table = table(load(host));
            openCity(table);
            uncheck(table, "Bonn");
            command(table, "Confirm").fire();
            TextField city = (TextField) body(table).getChildren().get(0).lookup("#cityField");
            city.setText("Bonn");
            assertEquals(List.of(), streets(table));
            assertEquals("Bonn", main.getCity());
            openCity(table);
            assertTrue(checks(table).stream().anyMatch(box -> "Bonn".equals(box.getText())));
        });
    }

    /**
     * A click outside the popup keeps the previous Filter.
     */
    @Test
    void aClickOutsideKeepsThePreviousFilter() throws Exception {
        FxThread.onFx(() -> {
            Host host = new Host();
            host.setVisits(new ArrayList<>(List.of(new Visit("Main", "Berlin"), new Visit("Oak", "Bonn"))));
            Table table = table(load(host));
            openCity(table);
            uncheck(table, "Bonn");
            table.fireEvent(click());
            assertEquals(List.of("Main", "Oak"), streets(table));
            assertFalse(popup(table).isVisible());
        });
    }

    /**
     * Loads the selection view, which names the same Columns.
     *
     * @param host the model
     * @return the loaded view
     */
    private static LoadedView load(Host host) {
        URL fxml = FilterByChecklistTest.class.getResource("show-visits-selection.fxml");
        assertNotNull(fxml);
        return ViewLoader.load(fxml, host, new ApplicationContext("de.neitzel.fx.view.component"));
    }

    /**
     * Returns the table.
     *
     * @param loaded the loaded view
     * @return the table
     */
    private static Table table(LoadedView loaded) {
        return (Table) loaded.getRoot().lookup("#visitsTable");
    }

    /**
     * Returns the body.
     *
     * @param table the table
     * @return the body
     */
    private static VBox body(Table table) {
        ScrollPane scroll = (ScrollPane) table.lookup("." + Table.SCROLL_STYLE);
        return (VBox) scroll.getContent();
    }

    /**
     * Returns the opener of one Column.
     *
     * @param table the table
     * @param index the Column index
     * @return the opener
     */
    private static javafx.scene.control.Button opener(Table table, int index) {
        ScrollPane headerScroll = (ScrollPane) table.lookup(".table-header-scroll");
        HBox header = (HBox) headerScroll.getContent();
        return (javafx.scene.control.Button) header.getChildren().get(index).lookup("." + Table.OPENER_STYLE);
    }

    /**
     * Opens the City popup.
     *
     * @param table the table
     */
    private static void openCity(Table table) {
        ScrollPane headerScroll = (ScrollPane) table.lookup(".table-header-scroll");
        HBox header = (HBox) headerScroll.getContent();
        ((javafx.scene.control.Button) header.getChildren().get(1).lookup("." + Table.OPENER_STYLE)).fire();
    }

    /**
     * Returns the popup.
     *
     * @param table the table
     * @return the popup
     */
    private static VBox popup(Table table) {
        return (VBox) table.lookup("." + Table.POPUP_STYLE);
    }

    /**
     * Returns the checklist boxes.
     *
     * @param table the table
     * @return the boxes
     */
    private static List<CheckBox> checks(Table table) {
        List<CheckBox> boxes = new ArrayList<>();
        for (Node node : popup(table).getChildren()) {
            if (node instanceof VBox list) {
                for (Node child : list.getChildren()) {
                    if (child instanceof CheckBox box) {
                        boxes.add(box);
                    }
                }
            }
        }
        return boxes;
    }

    /**
     * Unchecks the box with the given text.
     *
     * @param table the table
     * @param text  the checklist text
     */
    private static void uncheck(Table table, String text) {
        for (CheckBox box : checks(table)) {
            if (text.equals(box.getText())) {
                box.setSelected(false);
            }
        }
    }

    /**
     * Returns a popup command.
     *
     * @param table the table
     * @param text  the command text
     * @return the button
     */
    private static Button command(Table table, String text) {
        return popup(table).getChildren().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> text.equals(button.getText()))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Returns the streets of the visible rows.
     *
     * @param table the table
     * @return the streets
     */
    private static List<String> streets(Table table) {
        List<String> streets = new ArrayList<>();
        for (Node node : body(table).getChildren()) {
            streets.add(((TextField) node.lookup("#streetField")).getText());
        }
        return streets;
    }

    /**
     * Returns the streets of the collection.
     *
     * @param visits the visits
     * @return the streets
     */
    private static List<String> streets(List<Visit> visits) {
        return visits.stream().map(Visit::getStreet).toList();
    }

    /**
     * Returns a primary click.
     *
     * @return the event
     */
    private static MouseEvent click() {
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null);
    }
}
