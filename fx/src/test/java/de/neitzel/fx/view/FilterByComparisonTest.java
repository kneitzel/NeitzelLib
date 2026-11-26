package de.neitzel.fx.view;

import de.neitzel.fx.view.fixture.Host;
import de.neitzel.fx.view.fixture.Role;
import de.neitzel.fx.view.fixture.Visit;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A typed comparison replaces the checklist on that Column.
 */
class FilterByComparisonTest {

    /**
     * Starts the JavaFX toolkit once for this class.
     */
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        FxThread.start();
    }

    /**
     * A string comparison ignores case, and confirming the checklist replaces it.
     */
    @Test
    void aStringComparisonReplacesTheChecklist() throws Exception {
        FxThread.onFx(() -> {
            Visit berlin = new Visit("Main", "Berlin");
            Visit bonn = new Visit("Oak", "Bonn");
            Visit lower = new Visit("Elm", "berlin");
            Host host = host(berlin, bonn, lower);
            Table table = first(load(host));
            open(table, 1);
            operator(table).setValue("Contains");
            value(table).setText("ER");
            command(table, "Confirm").fire();
            assertFalse(popup(table).isVisible());
            assertEquals(List.of("Main", "Elm"), streets(table));
            assertEquals(List.of("Main", "Oak", "Elm"), streets(host.getVisits()));
            open(table, 1);
            assertEquals("Contains", operator(table).getValue());
            assertEquals("ER", value(table).getText());
            operator(table).setValue(null);
            value(table).setText("");
            uncheck(table, "Berlin");
            uncheck(table, "berlin");
            command(table, "Confirm").fire();
            assertEquals(List.of("Oak"), streets(table));
        });
    }

    /**
     * Starts with and ends with match the checklist text, ignoring case.
     */
    @Test
    void startsWithAndEndsWithMatchTheChecklistText() throws Exception {
        FxThread.onFx(() -> {
            Host host = host(new Visit("Main", "Berlin"), new Visit("Oak", "Bonn"));
            Table table = first(load(host));
            open(table, 1);
            operator(table).setValue("Starts with");
            value(table).setText("bo");
            command(table, "Confirm").fire();
            assertEquals(List.of("Oak"), streets(table));
            open(table, 1);
            operator(table).setValue("Ends with");
            value(table).setText("LIN");
            command(table, "Confirm").fire();
            assertEquals(List.of("Main"), streets(table));
        });
    }

    /**
     * A number comparison excludes the boundary, and a missing value does not pass.
     */
    @Test
    void aNumberComparisonExcludesTheBoundary() throws Exception {
        FxThread.onFx(() -> {
            Visit five = visit("Five", 5, false, Role.MEMBER, LocalDate.of(2020, 1, 2));
            Visit six = visit("Six", 6, true, Role.ADMIN, LocalDate.of(2020, 1, 1));
            Visit missing = visit("Missing", null, false, null, null);
            Host host = host(five, six, missing);
            Table table = first(load(host));
            open(table, 2);
            operator(table).setValue("Greater than");
            value(table).setText("5");
            command(table, "Confirm").fire();
            assertEquals(List.of("Six"), streets(table));
            open(table, 2);
            value(table).setText("abc");
            command(table, "Confirm").fire();
            assertTrue(popup(table).isVisible());
            assertEquals(List.of("Six"), streets(table));
            value(table).setText("");
            command(table, "Confirm").fire();
            assertTrue(popup(table).isVisible());
            operator(table).setValue(null);
            value(table).setText("5");
            command(table, "Confirm").fire();
            assertTrue(popup(table).isVisible());
            assertEquals(List.of("Six"), streets(table));
        });
    }

    /**
     * Before and after exclude the named day.
     */
    @Test
    void beforeAndAfterExcludeTheNamedDay() throws Exception {
        FxThread.onFx(() -> {
            Visit earlier = visit("Early", 1, false, Role.MEMBER, LocalDate.of(2020, 1, 1));
            Visit day = visit("Day", 1, false, Role.MEMBER, LocalDate.of(2020, 1, 2));
            Host host = host(earlier, day);
            Table table = first(load(host));
            open(table, 5);
            operator(table).setValue("Before");
            value(table).setText("2020-01-02");
            command(table, "Confirm").fire();
            assertEquals(List.of("Early"), streets(table));
            open(table, 5);
            operator(table).setValue("After");
            value(table).setText("2020-01-01");
            command(table, "Confirm").fire();
            assertEquals(List.of("Day"), streets(table));
            open(table, 5);
            value(table).setText("02.01.2020");
            command(table, "Confirm").fire();
            assertTrue(popup(table).isVisible());
            assertEquals(List.of("Day"), streets(table));
        });
    }

    /**
     * A boolean and an enum have no comparison.
     */
    @Test
    void aBooleanAndAnEnumHaveNoComparison() throws Exception {
        FxThread.onFx(() -> {
            Host host = host(visit("Main", 1, true, Role.MEMBER, LocalDate.of(2020, 1, 1)));
            Table table = first(load(host));
            open(table, 3);
            assertFalse(operator(table).isVisible());
            assertFalse(value(table).isVisible());
            open(table, 4);
            assertFalse(operator(table).isVisible());
            assertFalse(value(table).isVisible());
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
     * Loads the two-table view.
     *
     * @param host the model
     * @return the loaded view
     */
    private static LoadedView load(Host host) {
        URL fxml = FilterByComparisonTest.class.getResource("show-two-tables.fxml");
        assertNotNull(fxml);
        return TestViews.load(fxml, host, new TestViews());
    }

    /**
     * Returns the first table.
     *
     * @param loaded the loaded view
     * @return the table
     */
    private static Table first(LoadedView loaded) {
        return (Table) loaded.getRoot().lookup("#firstTable");
    }

    /**
     * Opens one Column's popup.
     *
     * @param table the table
     * @param index the Column index
     */
    private static void open(Table table, int index) {
        ScrollPane headerScroll = (ScrollPane) table.lookup(".table-header-scroll");
        HBox header = (HBox) headerScroll.getContent();
        ((Button) header.getChildren().get(index).lookup("." + Table.OPENER_STYLE)).fire();
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
     * Returns the comparison operator.
     *
     * @param table the table
     * @return the operator
     */
    @SuppressWarnings("unchecked")
    private static ComboBox<String> operator(Table table) {
        return (ComboBox<String>) popup(table).lookup(".column-comparison-operator");
    }

    /**
     * Returns the comparison value.
     *
     * @param table the table
     * @return the value
     */
    private static TextField value(Table table) {
        return (TextField) popup(table).lookup(".column-comparison-value");
    }

    /**
     * Unchecks the checklist entry with the given text.
     *
     * @param table the table
     * @param text  the checklist text
     */
    private static void uncheck(Table table, String text) {
        for (Node node : popup(table).getChildren()) {
            if (!(node instanceof VBox list)) {
                continue;
            }
            for (Node child : list.getChildren()) {
                if (child instanceof CheckBox box && text.equals(box.getText())) {
                    box.setSelected(false);
                }
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
        ScrollPane scroll = (ScrollPane) table.lookup("." + Table.SCROLL_STYLE);
        VBox body = (VBox) scroll.getContent();
        List<String> streets = new ArrayList<>();
        for (Node node : body.getChildren()) {
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
}
