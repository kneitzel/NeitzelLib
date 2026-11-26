package de.neitzel.fx.view;

import de.neitzel.fx.view.fixture.Host;
import de.neitzel.fx.view.fixture.Visit;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The checklist search narrows entries and Select All still reaches the hidden ones.
 */
class SearchTheChecklistTest {

    /**
     * Starts the JavaFX toolkit once for this class.
     */
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        FxThread.start();
    }

    /**
     * Typing narrows the list and leaves the checks and the Rows alone.
     */
    @Test
    void typingNarrowsTheChecklistWithoutChangingTheRows() throws Exception {
        FxThread.onFx(() -> {
            Host host = host(new Visit("Main", "Berlin"), new Visit("Oak", "Bonn"), new Visit("Elm", "Bern"));
            Table table = table(load(host));
            openCity(table);
            search(table).setText("ber");
            assertTrue(box(table, "Berlin").isVisible());
            assertTrue(box(table, "Bern").isVisible());
            assertFalse(box(table, "Bonn").isVisible());
            assertTrue(box(table, "Bonn").isSelected());
            assertEquals(List.of("Main", "Oak", "Elm"), streets(table));
            search(table).setText("*");
            assertFalse(box(table, "Berlin").isVisible());
            assertEquals(List.of("Main", "Oak", "Elm"), streets(table));
        });
    }

    /**
     * Each opening starts with an empty search, so every entry is visible.
     */
    @Test
    void eachOpeningStartsWithAnEmptySearch() throws Exception {
        FxThread.onFx(() -> {
            Host host = host(new Visit("Main", "Berlin"), new Visit("Oak", "Bonn"));
            Table table = table(load(host));
            openCity(table);
            search(table).setText("ber");
            command(table, "Confirm").fire();
            openCity(table);
            assertEquals("", search(table).getText());
            assertTrue(box(table, "Berlin").isVisible());
            assertTrue(box(table, "Bonn").isVisible());
        });
    }

    /**
     * Select All reaches values the search box is hiding, and shows a mixed mark from the whole Column.
     */
    @Test
    void selectAllReachesValuesTheSearchIsHiding() throws Exception {
        FxThread.onFx(() -> {
            Host host = host(new Visit("Main", "Berlin"), new Visit("Oak", "Bonn"), new Visit("Elm", "Bern"));
            Table table = table(load(host));
            openCity(table);
            box(table, "Bonn").setSelected(false);
            search(table).setText("ber");
            assertFalse(box(table, "Bonn").isVisible());
            assertTrue(selectAll(table).isIndeterminate());
            assertFalse(selectAll(table).isSelected());
            click(selectAll(table));
            assertTrue(box(table, "Bonn").isSelected());
            search(table).setText("");
            assertTrue(box(table, "Bonn").isSelected());
            search(table).setText("ber");
            click(selectAll(table));
            assertFalse(box(table, "Bonn").isSelected());
            assertFalse(box(table, "Berlin").isSelected());
        });
    }

    /**
     * Loads the visits table.
     *
     * @param host the model
     * @return the loaded view
     */
    private static LoadedView load(Host host) {
        URL fxml = SearchTheChecklistTest.class.getResource("show-visits.fxml");
        assertNotNull(fxml);
        return TestViews.load(fxml, host, new TestViews());
    }

    /**
     * Returns a host with those visits.
     *
     * @param visits the visits
     * @return the host
     */
    private static Host host(Visit... visits) {
        Host host = new Host();
        host.setVisits(new ArrayList<>(List.of(visits)));
        return host;
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
     * Opens the City popup.
     *
     * @param table the table
     */
    private static void openCity(Table table) {
        ScrollPane headerScroll = (ScrollPane) table.lookup(".table-header-scroll");
        HBox header = (HBox) headerScroll.getContent();
        ((Button) header.getChildren().get(1).lookup("." + Table.OPENER_STYLE)).fire();
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
     * Returns the search box.
     *
     * @param table the table
     * @return the search box
     */
    private static TextField search(Table table) {
        return (TextField) popup(table).lookup(".column-search");
    }

    /**
     * Returns Select All.
     *
     * @param table the table
     * @return Select All
     */
    private static CheckBox selectAll(Table table) {
        return (CheckBox) popup(table).lookup(".column-select-all");
    }

    /**
     * Returns the checklist box with the given text.
     *
     * @param table the table
     * @param text  the checklist text
     * @return the box
     */
    private static CheckBox box(Table table, String text) {
        for (Node node : popup(table).getChildren()) {
            if (!(node instanceof VBox list)) {
                continue;
            }
            for (Node child : list.getChildren()) {
                if (child instanceof CheckBox check && text.equals(check.getText())) {
                    return check;
                }
            }
        }
        throw new AssertionError("No checklist entry " + text);
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
        ScrollPane scroll = (ScrollPane) table.lookup("." + Table.SCROLL_STYLE);
        VBox body = (VBox) scroll.getContent();
        List<String> streets = new ArrayList<>();
        for (Node node : body.getChildren()) {
            streets.add(((TextField) node.lookup("#streetField")).getText());
        }
        return streets;
    }

    /**
     * Clicks a node with the primary button.
     *
     * @param node the node
     */
    private static void click(Node node) {
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null));
    }
}
