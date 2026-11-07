package de.neitzel.fx.view;

import de.neitzel.fx.view.fixture.Host;
import de.neitzel.fx.view.fixture.Visit;
import de.neitzel.injection.ApplicationContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A Column opens at its starting width. A Column without one takes an equal share.
 */
class OpenAtAGivenWidthTest {

    /**
     * Starts the JavaFX toolkit once for this class.
     */
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        FxThread.start();
    }

    /**
     * Named widths are kept, including after a drag and a change of the Table's width.
     */
    @Test
    void aColumnOpensAtItsWidth() throws Exception {
        FxThread.onFx(() -> {
            Table table = table("show-visits-widths.fxml");
            HBox header = header(table);
            Region street = (Region) header.getChildren().get(0);
            Region city = (Region) header.getChildren().get(1);
            assertEquals(220, street.getPrefWidth());
            assertEquals(80, city.getPrefWidth());
            assertEquals(220, field(table, 0).getPrefWidth());
            assertEquals(80, field(table, 1).getPrefWidth());
            drag(street.lookup("." + Table.RESIZE_STYLE), 0, 40);
            assertEquals(260, street.getPrefWidth());
            assertEquals(80, city.getPrefWidth());
            table.setPrefWidth(900);
            assertEquals(260, street.getPrefWidth());
            assertEquals(80, city.getPrefWidth());
        });
    }

    /**
     * A Column with no width takes what the named widths leave of the Table.
     */
    @Test
    void aColumnWithoutAWidthTakesTheRemainingShare() throws Exception {
        FxThread.onFx(() -> {
            HBox header = header(table("show-visits-one-width.fxml"));
            assertEquals(100, ((Region) header.getChildren().get(0)).getPrefWidth());
            assertEquals(200, ((Region) header.getChildren().get(1)).getPrefWidth());
        });
    }

    /**
     * A starting width narrower than the header text opens wide enough to show that text.
     */
    @Test
    void aNarrowWidthOpensAtTheHeaderText() throws Exception {
        FxThread.onFx(() -> {
            HBox header = header(table("show-visits-narrow.fxml"));
            assertEquals(280, ((Region) header.getChildren().get(0)).getPrefWidth());
            Text text = new Text("City");
            double headerText = Math.ceil(text.getLayoutBounds().getWidth()) + 8;
            assertEquals(headerText, ((Region) header.getChildren().get(1)).getPrefWidth());
        });
    }

    /**
     * A width that is not positive fails the load.
     */
    @Test
    void aWidthThatIsNotPositiveFailsToLoad() throws Exception {
        FxThread.onFx(() -> assertThrows(IllegalArgumentException.class, () -> table("show-visits-bad-width.fxml")));
    }

    /**
     * Loads a visits table.
     *
     * @param name the document name
     * @return the table
     */
    private static Table table(String name) {
        URL fxml = OpenAtAGivenWidthTest.class.getResource(name);
        assertNotNull(fxml);
        Host host = new Host();
        host.setVisits(new ArrayList<>(List.of(new Visit("Main", "Berlin"))));
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
     * Returns one field of the first row.
     *
     * @param table the table
     * @param index the field index
     * @return the field
     */
    private static Region field(Table table, int index) {
        ScrollPane scroll = (ScrollPane) table.lookup("." + Table.SCROLL_STYLE);
        javafx.scene.layout.VBox body = (javafx.scene.layout.VBox) scroll.getContent();
        return (Region) ((javafx.scene.Parent) body.getChildren().get(0)).getChildrenUnmodifiable().get(index);
    }

    /**
     * Drags a resize handle from one screen x to another.
     *
     * @param handle the handle
     * @param from   the start screen x
     * @param to     the end screen x
     */
    private static void drag(javafx.scene.Node handle, double from, double to) {
        handle.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, from));
        handle.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, to));
    }

    /**
     * Returns a primary mouse event.
     *
     * @param type   the event type
     * @param screen the screen x
     * @return the event
     */
    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type, double screen) {
        return new MouseEvent(type, screen, 0, screen, 0, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null);
    }
}
