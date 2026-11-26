package de.neitzel.fx.view;

import de.neitzel.fx.view.component.VisitRow;
import de.neitzel.fx.view.fixture.Host;
import de.neitzel.fx.view.fixture.Visit;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Clicking a Row sets the parent Model's Selection.
 */
class SelectARowTest {

    /**
     * Starts the JavaFX toolkit once for this class.
     */
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        FxThread.start();
    }

    /**
     * A click selects the visit, including a click in a field, and a second click leaves it.
     */
    @Test
    void aClickSelectsTheVisit() throws Exception {
        FxThread.onFx(() -> {
            Visit main = new Visit("Main", "Berlin");
            Visit oak = new Visit("Oak", "Bonn");
            Host host = host(oak, main, oak);
            LoadedView loaded = load(host);
            VBox body = body(loaded);
            VisitRow mainRow = (VisitRow) body.getChildren().get(0);
            VisitRow oakRow = (VisitRow) body.getChildren().get(1);
            assertTrue(oakRow.getStyleClass().contains(Table.SELECTED_STYLE));
            assertFalse(mainRow.getStyleClass().contains(Table.SELECTED_STYLE));
            click(mainRow.lookup("#streetField"));
            assertSame(main, host.getSelectedVisit());
            assertTrue(mainRow.getStyleClass().contains(Table.SELECTED_STYLE));
            assertFalse(oakRow.getStyleClass().contains(Table.SELECTED_STYLE));
            click(mainRow);
            assertSame(main, host.getSelectedVisit());
        });
    }

    /**
     * A click on the blank body clears Selection. Removing the element clears it too.
     */
    @Test
    void theBlankBodyAndRemovalClearSelection() throws Exception {
        FxThread.onFx(() -> {
            Visit main = new Visit("Main", "Berlin");
            Visit oak = new Visit("Oak", "Bonn");
            Host host = host(main, main, oak);
            LoadedView loaded = load(host);
            VBox rows = body(loaded);
            click(rows);
            assertNull(host.getSelectedVisit());
            assertFalse(((VisitRow) rows.getChildren().get(0)).getStyleClass().contains(Table.SELECTED_STYLE));
            click(rows.getChildren().get(1));
            assertSame(oak, host.getSelectedVisit());
            @SuppressWarnings("unchecked")
            javafx.collections.ObservableList<Visit> visits =
                    (javafx.collections.ObservableList<Visit>) loaded.property("visits").getValue();
            visits.remove(oak);
            assertNull(host.getSelectedVisit());
            assertEquals(1, rows.getChildren().size());
        });
    }

    /**
     * Returns a host with the selection already set.
     *
     * @param selected the selected visit
     * @param visits   the visits
     * @return the host
     */
    private static Host host(Visit selected, Visit... visits) {
        Host host = new Host();
        host.setVisits(new ArrayList<>(List.of(visits)));
        host.setSelectedVisit(selected);
        return host;
    }

    /**
     * Loads the selection view.
     *
     * @param host the model
     * @return the loaded view
     */
    private static LoadedView load(Host host) {
        URL fxml = SelectARowTest.class.getResource("show-visits-selection.fxml");
        assertNotNull(fxml);
        return TestViews.load(fxml, host, new TestViews());
    }

    /**
     * Returns the row body.
     *
     * @param loaded the loaded view
     * @return the body
     */
    private static VBox body(LoadedView loaded) {
        Table table = (Table) loaded.getRoot().lookup("#visitsTable");
        ScrollPane scroll = (ScrollPane) table.lookup("." + Table.SCROLL_STYLE);
        return (VBox) scroll.getContent();
    }

    /**
     * Clicks a node with the primary button.
     *
     * @param node the node
     */
    private static void click(javafx.scene.Node node) {
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null));
    }
}
