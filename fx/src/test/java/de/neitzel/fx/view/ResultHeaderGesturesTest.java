package de.neitzel.fx.view;

import static org.junit.jupiter.api.Assertions.*;

import de.neitzel.grid.ColumnWidth;

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Handler-level rollback/commit checks; native drag routing is qualified separately. */
class ResultHeaderGesturesTest {
    @BeforeAll
    static void toolkit() throws Exception {
        DataGridGroupingTest.toolkit();
    }

    @Test
    void resizePreviewEscapeBoundsAndReleaseNeverSortOrReplaceRows() throws Exception {
        var reads = new AtomicInteger();
        var disposed = new AtomicInteger();
        var fields = new HashMap<Long, javafx.scene.control.TextField>();
        var grid =
                DataGridGroupingTest.fx(() -> DataGridGroupingTest.grid(reads, disposed, fields));
        DataGridGroupingTest.fx(grid::loadAsync).toCompletableFuture().get(5, TimeUnit.SECONDS);
        try {
            DataGridGroupingTest.fx(() -> {
                Node handle =
                        grid.lookupAll(".column-resize").stream()
                                .filter(n -> "type".equals(n.getParent().getUserData()))
                                .findFirst()
                                .orElseThrow();
                HBox cell = (HBox) handle.getParent();
                var before = grid.snapshotSetup();
                long revision = grid.committedRevisionProperty().get();
                Event.fireEvent(handle, mouse(MouseEvent.MOUSE_PRESSED, 100));
                Event.fireEvent(handle, mouse(MouseEvent.MOUSE_DRAGGED, 5000));
                assertEquals(400, cell.getPrefWidth());
                assertEquals(before, grid.snapshotSetup(), "Preview must not be persisted");
                Event.fireEvent(
                        grid.getScene(),
                        new KeyEvent(
                                KeyEvent.KEY_PRESSED,
                                "",
                                "",
                                KeyCode.ESCAPE,
                                false,
                                false,
                                false,
                                false));
                assertEquals(150, cell.getPrefWidth());
                assertEquals(revision, grid.committedRevisionProperty().get());
                Event.fireEvent(handle, mouse(MouseEvent.MOUSE_RELEASED, 5000));
                assertEquals(before, grid.snapshotSetup());
                Event.fireEvent(handle, mouse(MouseEvent.MOUSE_PRESSED, 100));
                Event.fireEvent(handle, mouse(MouseEvent.MOUSE_DRAGGED, -500));
                Event.fireEvent(handle, mouse(MouseEvent.MOUSE_RELEASED, -500));
                Event.fireEvent(handle, mouse(MouseEvent.MOUSE_CLICKED, -500));
                assertEquals(
                        new ColumnWidth.Explicit(80),
                        grid.snapshotSetup().columns().getFirst().width());
                assertEquals(revision + 1, grid.committedRevisionProperty().get());
                assertTrue(grid.snapshotSetup().sort().isEmpty());
                assertEquals(1, reads.get());
                assertEquals(0, disposed.get());
                return null;
            });
        } finally {
            DataGridGroupingTest.fx(() -> {
                grid.close();
                return null;
            });
        }
    }

    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type, double screenX) {
        return new MouseEvent(
                type,
                0,
                0,
                screenX,
                100,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                type != MouseEvent.MOUSE_RELEASED,
                false,
                false,
                false,
                false,
                false,
                null);
    }
}
