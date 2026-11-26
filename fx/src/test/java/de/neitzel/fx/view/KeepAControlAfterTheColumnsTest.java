package de.neitzel.fx.view;

import de.neitzel.fx.view.component.ActionRow;
import de.neitzel.fx.view.fixture.Host;
import de.neitzel.fx.view.fixture.Visit;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A control after the Column fields keeps its own width, and the header uses the Row's spacing.
 */
class KeepAControlAfterTheColumnsTest {

    /**
     * Starts the JavaFX toolkit once for this class.
     */
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        FxThread.start();
    }

    /**
     * The remove button stays its own size and the row is wide enough to show it.
     */
    @Test
    void aControlAfterTheFieldsKeepsItsOwnWidth() throws Exception {
        FxThread.onFx(() -> {
            Table table = table();
            HBox header = header(table);
            assertEquals(2, header.getChildren().size());
            assertEquals(6, header.getSpacing());
            assertEquals(306, header.getPrefWidth());
            ActionRow row = row(table);
            Region street = (Region) row.getChildren().get(0);
            Region city = (Region) row.getChildren().get(1);
            Button remove = (Button) row.lookup("#removeButton");
            assertEquals(150, street.getPrefWidth());
            assertEquals(150, city.getPrefWidth());
            assertEquals(Region.USE_COMPUTED_SIZE, remove.getMinWidth());
            assertEquals(Region.USE_COMPUTED_SIZE, remove.getPrefWidth());
            assertEquals(Region.USE_COMPUTED_SIZE, remove.getMaxWidth());
            assertEquals("Remove", remove.getText());
            assertEquals(300 + 12 + remove.prefWidth(-1), row.getPrefWidth());
        });
    }

    /**
     * Loads the visits table whose row has a button after the fields.
     *
     * @return the table
     */
    private static Table table() {
        URL fxml = KeepAControlAfterTheColumnsTest.class.getResource("show-visits-action.fxml");
        assertNotNull(fxml);
        Host host = new Host();
        host.setVisits(new ArrayList<>(List.of(new Visit("Main", "Berlin"))));
        LoadedView loaded = TestViews.load(fxml, host, new TestViews());
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
     * Returns the first row.
     *
     * @param table the table
     * @return the row
     */
    private static ActionRow row(Table table) {
        ScrollPane scroll = (ScrollPane) table.lookup("." + Table.SCROLL_STYLE);
        return (ActionRow) ((VBox) scroll.getContent()).getChildren().get(0);
    }
}
