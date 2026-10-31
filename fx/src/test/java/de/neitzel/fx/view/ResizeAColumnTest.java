package de.neitzel.fx.view;

import de.neitzel.fx.view.fixture.Host;
import de.neitzel.fx.view.fixture.Visit;
import de.neitzel.injection.ApplicationContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Dragging a Column boundary changes that Column's width only.
 */
class ResizeAColumnTest {

    /**
     * Starts the JavaFX toolkit once for this class.
     */
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        FxThread.start();
    }

    /**
     * A drag widens one Column and leaves the other, the Sort, and the order alone.
     */
    @Test
    void aDragChangesOnlyThatColumn() throws Exception {
        FxThread.onFx(() -> {
            Host host = new Host();
            host.setVisits(new ArrayList<>(List.of(new Visit("Main", "Berlin"))));
            Table table = table(host);
            HBox header = header(table);
            Region street = (Region) header.getChildren().get(0);
            Region city = (Region) header.getChildren().get(1);
            assertEquals(150, street.getPrefWidth());
            assertEquals(150, city.getPrefWidth());
            drag(street.lookup("." + Table.RESIZE_STYLE), 100, 140);
            assertEquals(190, street.getPrefWidth());
            assertEquals(150, city.getPrefWidth());
            double widened = street.getPrefWidth();
            street.lookup("." + Table.RESIZE_STYLE).fireEvent(click(2));
            assertEquals(widened, street.getPrefWidth());
            table.setPrefWidth(800);
            assertEquals(widened, street.getPrefWidth());
            assertEquals(150, city.getPrefWidth());
            assertEquals("Street", ((Label) street.lookup("." + Table.HEADER_TEXT_STYLE)).getText());
            assertEquals("City", ((Label) city.lookup("." + Table.HEADER_TEXT_STYLE)).getText());
            Label mark = (Label) street.lookup("." + Table.SORT_MARK_STYLE);
            assertFalse(mark.isVisible());
        });
    }

    /**
     * Columns wider than the Table make the header wider than the viewport.
     */
    @Test
    void wideColumnsScrollHorizontally() throws Exception {
        FxThread.onFx(() -> {
            Host host = new Host();
            host.setVisits(new ArrayList<>(List.of(new Visit("Main", "Berlin"))));
            Table table = table(host);
            HBox header = header(table);
            drag(((Region) header.getChildren().get(0)).lookup("." + Table.RESIZE_STYLE), 0, 400);
            assertEquals(700, header.getPrefWidth());
            ScrollPane scroll = (ScrollPane) table.lookup("." + Table.SCROLL_STYLE);
            assertEquals(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED, scroll.getHbarPolicy());
        });
    }

    /**
     * Loads the visits table.
     *
     * @param host the model
     * @return the table
     */
    private static Table table(Host host) {
        URL fxml = ResizeAColumnTest.class.getResource("show-visits.fxml");
        assertNotNull(fxml);
        LoadedView loaded = ViewLoader.load(fxml, host, new ApplicationContext("de.neitzel.fx.view.component"));
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
     * Drags a resize handle from one screen x to another.
     *
     * @param handle the handle
     * @param from   the start screen x
     * @param to     the end screen x
     */
    private static void drag(javafx.scene.Node handle, double from, double to) {
        handle.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, from, 1));
        handle.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, to, 1));
    }

    /**
     * Returns a primary click.
     *
     * @param clicks the click count
     * @return the event
     */
    private static MouseEvent click(int clicks) {
        return mouse(MouseEvent.MOUSE_CLICKED, 0, clicks);
    }

    /**
     * Returns a primary mouse event.
     *
     * @param type   the event type
     * @param screen the screen x
     * @param clicks the click count
     * @return the event
     */
    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type, double screen, int clicks) {
        return new MouseEvent(type, screen, 0, screen, 0, MouseButton.PRIMARY, clicks,
                false, false, false, false, true, false, false, false, false, false, null);
    }
}
