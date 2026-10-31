package de.neitzel.fx.view;

import de.neitzel.fx.view.component.VisitRow;
import de.neitzel.fx.view.fixture.Host;
import de.neitzel.fx.view.fixture.Role;
import de.neitzel.fx.view.fixture.Visit;
import de.neitzel.injection.ApplicationContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A header click sorts one Column without rearranging the collection.
 */
class SortOneColumnTest {

    /**
     * Starts the JavaFX toolkit once for this class.
     */
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        FxThread.start();
    }

    /**
     * A header click switches direction and drops the other Column's Sort.
     */
    @Test
    void aHeaderClickSortsOneColumn() throws Exception {
        FxThread.onFx(() -> {
            Visit main = new Visit("Main", "Bonn");
            Visit oak = new Visit("Oak", "Berlin");
            Visit elm = new Visit("Elm", null);
            Host host = host(main, oak, elm);
            LoadedView loaded = load("show-two-tables.fxml", host);
            Table first = (Table) loaded.getRoot().lookup("#firstTable");
            clickHeader(first, 1);
            assertTrue(mark(first, 1).isVisible());
            assertEquals(List.of("Oak", "Main", "Elm"), streets(first));
            assertEquals("ascending", mark(first, 1).getText());
            assertFalse(mark(first, 0).isVisible());
            clickHeader(first, 1);
            assertEquals(List.of("Main", "Oak", "Elm"), streets(first));
            assertEquals("descending", mark(first, 1).getText());
            clickHeader(first, 0);
            assertEquals(List.of("Elm", "Main", "Oak"), streets(first));
            assertTrue(mark(first, 0).isVisible());
            assertFalse(mark(first, 1).isVisible());
            assertEquals(List.of("Main", "Oak", "Elm"), streets(host.getVisits()));
            Table second = (Table) loaded.getRoot().lookup("#secondTable");
            assertEquals(List.of("Main", "Oak", "Elm"), streets(second));
        });
    }

    /**
     * The popup sorts and clears, and a click outside leaves the Sort.
     */
    @Test
    void thePopupSortsAndClears() throws Exception {
        FxThread.onFx(() -> {
            Visit berlin = new Visit("Main", "berlin");
            Visit capital = new Visit("Oak", "Berlin");
            Visit aachen = new Visit("Elm", "Aachen");
            Host host = host(berlin, capital, aachen);
            Table table = (Table) load("show-two-tables.fxml", host).getRoot().lookup("#firstTable");
            opener(table, 1).fire();
            VBox popup = popup(table);
            assertTrue(popup.isVisible());
            command(popup, "Ascending").fire();
            assertFalse(popup.isVisible());
            assertEquals(List.of("Elm", "Main", "Oak"), streets(table));
            opener(table, 1).fire();
            table.fireEvent(click());
            assertFalse(popup(table).isVisible());
            assertEquals(List.of("Elm", "Main", "Oak"), streets(table));
            opener(table, 1).fire();
            command(popup(table), "Clear sort").fire();
            assertEquals(List.of("Main", "Oak", "Elm"), streets(table));
            assertFalse(mark(table, 1).isVisible());
        });
    }

    /**
     * Numbers, booleans, enums, and dates sort in their own order. Missing values stay last.
     */
    @Test
    void eachTypeHasItsOwnOrder() throws Exception {
        FxThread.onFx(() -> {
            Visit low = visit("Low", 1, false, Role.ADMIN, LocalDate.of(2020, 1, 2));
            Visit high = visit("High", 10, true, Role.MEMBER, LocalDate.of(2019, 5, 1));
            Visit missing = visit("Missing", null, false, null, null);
            Host host = host(high, missing, low);
            Table table = (Table) load("show-two-tables.fxml", host).getRoot().lookup("#firstTable");
            clickHeader(table, 2);
            assertEquals(List.of("Low", "High", "Missing"), streets(table));
            clickHeader(table, 3);
            assertEquals(List.of("Missing", "Low", "High"), streets(table));
            clickHeader(table, 4);
            assertEquals(List.of("High", "Low", "Missing"), streets(table));
            clickHeader(table, 5);
            assertEquals(List.of("High", "Low", "Missing"), streets(table));
        });
    }

    /**
     * Returns a visit with the extra Attributes filled in.
     *
     * @param street the street
     * @param rank   the rank
     * @param open   whether the visit is open
     * @param role   the role
     * @param day    the day
     * @return the visit
     */
    private static Visit visit(String street, Integer rank, boolean open, Role role, LocalDate day) {
        Visit visit = new Visit(street, "Berlin");
        visit.setRank(rank);
        visit.setOpen(open);
        visit.setRole(role);
        visit.setDay(day);
        return visit;
    }

    /**
     * Loads a document.
     *
     * @param name the document name
     * @param host the model
     * @return the loaded view
     */
    private static LoadedView load(String name, Host host) {
        URL fxml = SortOneColumnTest.class.getResource(name);
        assertNotNull(fxml);
        return ViewLoader.load(fxml, host, new ApplicationContext("de.neitzel.fx.view.component"));
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
     * Clicks a Column header.
     *
     * @param table the table
     * @param index the Column index
     */
    private static void clickHeader(Table table, int index) {
        HBox header = header(table);
        header.getChildren().get(index).fireEvent(click());
    }

    /**
     * Returns the header row.
     *
     * @param table the table
     * @return the header
     */
    private static HBox header(Table table) {
        ScrollPane headerScroll = (ScrollPane) table.lookup(".table-header-scroll");
        return (HBox) headerScroll.getContent();
    }

    /**
     * Returns the sort mark of one Column.
     *
     * @param table the table
     * @param index the Column index
     * @return the mark
     */
    private static Label mark(Table table, int index) {
        return (Label) header(table).getChildren().get(index).lookup("." + Table.SORT_MARK_STYLE);
    }

    /**
     * Returns the opener of one Column.
     *
     * @param table the table
     * @param index the Column index
     * @return the opener
     */
    private static Button opener(Table table, int index) {
        return (Button) header(table).getChildren().get(index).lookup("." + Table.OPENER_STYLE);
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
     * Returns a popup command.
     *
     * @param popup the popup
     * @param text  the command text
     * @return the button
     */
    private static Button command(VBox popup, String text) {
        return popup.getChildren().stream()
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
        return body(table).getChildren().stream()
                .map(node -> ((javafx.scene.control.TextField) ((javafx.scene.Parent) node).lookup("#streetField"))
                        .getText())
                .toList();
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
     * Returns a primary click.
     *
     * @return the event
     */
    private static MouseEvent click() {
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null);
    }
}
