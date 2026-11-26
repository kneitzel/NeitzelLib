package de.neitzel.fx.view;

import de.neitzel.fx.view.component.CityFirstRow;
import de.neitzel.fx.view.component.VisitRow;
import de.neitzel.fx.view.fixture.Host;
import de.neitzel.fx.view.fixture.Visit;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A Table shows one Row for each element, in collection order.
 */
class ShowATableOfRowsTest {

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
     * Each visit appears in collection order, with its fields under the headers.
     */
    @Test
    void eachVisitAppearsInCollectionOrderUnderTheHeaders() throws Exception {
        onFx(() -> {
            Visit main = new Visit("Main", "Berlin");
            Visit oak = new Visit("Oak", "Bonn");
            Host host = host(main, oak);
            LoadedView loaded = load("show-visits.fxml", host);
            Table table = table(loaded);
            HBox header = header(table);
            assertEquals("Street", headerText(header, 0));
            assertEquals("City", headerText(header, 1));
            VBox body = body(table);
            assertEquals(2, body.getChildren().size());
            VisitRow first = (VisitRow) body.getChildren().get(0);
            VisitRow second = (VisitRow) body.getChildren().get(1);
            assertSame(main, first.getModel());
            assertSame(oak, second.getModel());
            assertEquals("Main", field(first, "streetField").getText());
            assertEquals("Berlin", field(first, "cityField").getText());
            assertEquals("Oak", field(second, "streetField").getText());
            assertEquals("Bonn", field(second, "cityField").getText());
            assertEquals(150, field(first, "streetField").getPrefWidth());
            assertEquals(150, field(first, "cityField").getPrefWidth());
            assertEquals(150, ((javafx.scene.layout.Region) header.getChildren().get(0)).getPrefWidth());
            assertEquals(150, ((javafx.scene.layout.Region) header.getChildren().get(1)).getPrefWidth());
            first.setModel(new Visit("Other", "Hamburg"));
            assertEquals("Main", field(first, "streetField").getText());
        });
    }

    /**
     * A row keeps the height of its own fields.
     */
    @Test
    void aRowKeepsTheHeightOfItsOwnFields() throws Exception {
        onFx(() -> {
            Host host = host(new Visit("Tall", "Berlin"), new Visit("Main", "Bonn"));
            LoadedView loaded = load("show-visits.fxml", host);
            VBox body = body(table(loaded));
            assertEquals(80, ((Region) body.getChildren().get(0)).getPrefHeight());
            assertEquals(24, ((Region) body.getChildren().get(1)).getPrefHeight());
        });
    }

    /**
     * A row whose fields are swapped still loads.
     */
    @Test
    void aRowWhoseFieldsAreSwappedStillLoads() throws Exception {
        onFx(() -> {
            Visit main = new Visit("Main", "Berlin");
            Host host = host(main);
            LoadedView loaded = load("show-visits-swapped.fxml", host);
            VBox body = body(table(loaded));
            assertEquals(1, body.getChildren().size());
            CityFirstRow row = (CityFirstRow) body.getChildren().get(0);
            assertSame(main, row.getModel());
            assertEquals("Berlin", field(row, "cityField").getText());
            assertEquals("Main", field(row, "streetField").getText());
        });
    }

    /**
     * Adding and removing a visit updates the table and the entity.
     */
    @Test
    void addingAndRemovingAVisitUpdatesTheTableAndTheEntity() throws Exception {
        onFx(() -> {
            Visit main = new Visit("Main", "Berlin");
            Visit oak = new Visit("Oak", "Bonn");
            Host host = host(main, oak);
            LoadedView loaded = load("show-visits.fxml", host);
            @SuppressWarnings("unchecked")
            ObservableList<Visit> visits = (ObservableList<Visit>) loaded.property("visits").getValue();
            Visit elm = new Visit("Elm", "Hamburg");
            visits.add(elm);
            VBox body = body(table(loaded));
            assertEquals(3, body.getChildren().size());
            assertSame(elm, ((VisitRow) body.getChildren().get(2)).getModel());
            assertEquals(List.of("Main", "Oak", "Elm"), streets(host.getVisits()));
            visits.remove(oak);
            assertEquals(2, body.getChildren().size());
            assertSame(main, ((VisitRow) body.getChildren().get(0)).getModel());
            assertSame(elm, ((VisitRow) body.getChildren().get(1)).getModel());
            assertEquals(List.of("Main", "Elm"), streets(host.getVisits()));
        });
    }

    /**
     * A row scrolled out of sight still exists, and the header stays put.
     */
    @Test
    void aRowScrolledOutOfSightStillExists() throws Exception {
        onFx(() -> {
            List<Visit> visits = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                visits.add(new Visit("Street " + index, "Berlin"));
            }
            Host host = new Host();
            host.setVisits(visits);
            LoadedView loaded = load("show-visits.fxml", host);
            Table table = table(loaded);
            VBox body = body(table);
            assertEquals(12, body.getChildren().size());
            ScrollPane scroll = (ScrollPane) table.lookup("." + Table.SCROLL_STYLE);
            assertSame(body, scroll.getContent());
            HBox header = header(table);
            assertFalse(scroll.getContent() instanceof Parent parent && parent.getChildrenUnmodifiable().contains(header));
            assertSame(body, scroll.getContent());
        });
    }

    /**
     * An empty collection shows the headers and a blank body.
     */
    @Test
    void anEmptyCollectionShowsTheHeadersAndABlankBody() throws Exception {
        onFx(() -> {
            Host host = new Host();
            LoadedView loaded = load("show-visits.fxml", host);
            Table table = table(loaded);
            assertEquals(2, header(table).getChildren().size());
            assertEquals(0, body(table).getChildren().size());
            assertNull(body(table).lookup(".label"));
        });
    }

    /**
     * A table with no column fails to load.
     */
    @Test
    void aTableWithNoColumnFailsToLoad() throws Exception {
        onFx(() -> {
            Host host = host(new Visit("Main", "Berlin"));
            assertThrows(IllegalStateException.class, () -> load("show-visits-no-column.fxml", host));
        });
    }

    /**
     * A table that names one attribute twice fails to load.
     */
    @Test
    void aRepeatedAttributeFailsToLoad() throws Exception {
        onFx(() -> {
            Host host = host(new Visit("Main", "Berlin"));
            assertThrows(IllegalStateException.class, () -> load("show-visits-duplicate.fxml", host));
        });
    }

    /**
     * A column that is not a simple value fails to load.
     */
    @Test
    void aComplexAttributeFailsToLoad() throws Exception {
        onFx(() -> {
            Host host = host(new Visit("Main", "Berlin"));
            assertThrows(IllegalStateException.class, () -> load("show-visits-complex.fxml", host));
        });
    }

    /**
     * Loads a view of visits.
     *
     * @param name the document name
     * @param host the model
     * @return the loaded view
     */
    private static LoadedView load(String name, Host host) {
        URL fxml = ShowATableOfRowsTest.class.getResource(name);
        assertNotNull(fxml);
        TestViews context = new TestViews();
        return TestViews.load(fxml, host, context);
    }

    /**
     * Returns a host whose visits are {@code visits}.
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
     * Returns the table in a loaded view.
     *
     * @param loaded the loaded view
     * @return the table
     */
    private static Table table(LoadedView loaded) {
        return (Table) loaded.getRoot().lookup("#visitsTable");
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
     * Returns the header text of one Column.
     *
     * @param header the header row
     * @param index  the Column index
     * @return the header text
     */
    private static String headerText(HBox header, int index) {
        Label label = (Label) header.getChildren().get(index).lookup("." + Table.HEADER_TEXT_STYLE);
        return label.getText();
    }

    /**
     * Returns the body that holds the rows.
     *
     * @param table the table
     * @return the body
     */
    private static VBox body(Table table) {
        ScrollPane scroll = (ScrollPane) table.lookup("." + Table.SCROLL_STYLE);
        assertInstanceOf(VBox.class, scroll.getContent());
        return (VBox) scroll.getContent();
    }

    /**
     * Returns a named field in a row.
     *
     * @param row the row
     * @param id  the field id
     * @return the field
     */
    private static TextField field(Parent row, String id) {
        return (TextField) row.lookup("#" + id);
    }

    /**
     * Returns the streets of the visits, in list order.
     *
     * @param visits the visits
     * @return the streets
     */
    private static List<String> streets(List<Visit> visits) {
        return visits.stream().map(Visit::getStreet).toList();
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
