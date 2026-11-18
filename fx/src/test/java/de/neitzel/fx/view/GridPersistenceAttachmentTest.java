package de.neitzel.fx.view;

import static org.junit.jupiter.api.Assertions.*;

import de.neitzel.grid.*;

import javafx.application.Platform;

import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class GridPersistenceAttachmentTest {
    static class Store implements GridSetupStore {
        GridSetup value;
        int revision, writes;
        boolean unreadable;

        public synchronized Stored load(Key key) {
            if (unreadable) throw new IllegalArgumentException("Malformed document");
            return new Stored(value, "" + revision);
        }

        public synchronized String revision(Key key) {
            return "" + revision;
        }

        public synchronized String save(Key key, String expected, GridSetup setup)
                throws java.io.IOException {
            if (!expected.equals("" + revision)) throw new Conflict();
            value = setup;
            unreadable = false;
            writes++;
            return "" + (++revision);
        }
    }

    @BeforeAll
    static void toolkit() throws Exception {
        DataGridTest.start();
        Platform.setImplicitExit(false);
    }

    record View(
            DataGrid<Long, DataGridGroupingTest.Item> grid,
            GridPersistenceAttachment<Long, DataGridGroupingTest.Item> attachment,
            AtomicInteger queries) {}

    static View view(GridApplicationConfiguration config) throws Exception {
        return DataGridGroupingTest.fx(() -> {
            AtomicInteger queries = new AtomicInteger();
            var grid = DataGridGroupingTest.grid(queries, new AtomicInteger(), new HashMap<>());
            var attachment = grid.attachPersistence(config, "records");
            return new View(grid, attachment, queries);
        });
    }

    @Test
    void loadPrecedesQueryAndDetachedViewLeavesItsAcceptedSaveWithTheService() throws Exception {
        var store = new Store();
        var config =
                new GridApplicationConfiguration(
                        "Example",
                        store,
                        Duration.ofHours(1),
                        Executors.newSingleThreadScheduledExecutor(),
                        true);
        var first = view(config);
        assertEquals(0, first.queries().get());
        DataGridGroupingTest.fx(first.attachment()::loadAsync)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        DataGridGroupingTest.fx(() -> {
            first.grid().setColumnWidth("type", new ColumnWidth.Explicit(270));
            first.grid().setExpanded(DataGridGroupingTest.path("A"), true);
            first.grid().close();
            return null;
        });
        config.flushAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(1, store.writes);
        var second = view(config);
        DataGridGroupingTest.fx(second.attachment()::loadAsync)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        DataGridGroupingTest.fx(() -> {
            assertEquals(
                    new ColumnWidth.Explicit(270),
                    second.grid().snapshotSetup().columns().getFirst().width());
            assertTrue(
                    second.grid().snapshotSetup().grouping().choices().stream()
                            .anyMatch(c ->
                                    c.path().equals(DataGridGroupingTest.path("A"))
                                            && c.expanded()));
            assertEquals(1, second.queries().get());
            second.grid().close();
            return null;
        });
        config.closeAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(1, store.writes);
    }

    @Test
    void unreadableStartupDoesNotQueryOrOverwriteUntilExplicitResetSucceeds() throws Exception {
        var store = new Store();
        store.unreadable = true;
        var config =
                new GridApplicationConfiguration(
                        "Example",
                        store,
                        Duration.ofHours(1),
                        Executors.newSingleThreadScheduledExecutor(),
                        true);
        var view = view(config);
        assertThrows(
                ExecutionException.class,
                () ->
                        DataGridGroupingTest.fx(view.attachment()::loadAsync)
                                .toCompletableFuture()
                                .get(5, TimeUnit.SECONDS));
        assertEquals(0, view.queries().get());
        assertEquals(0, store.writes);
        DataGridGroupingTest.fx(view.attachment()::resetAsync)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        config.flushAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(1, view.queries().get());
        assertEquals(1, store.writes);
        DataGridGroupingTest.fx(() -> {
            view.grid().close();
            return null;
        });
        config.closeAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test
    void applicationCloseDuringRestoreCompletesTheOperationWithAVisibleFailure() throws Exception {
        var config = new GridApplicationConfiguration("Example", new Store());
        var view = view(config);
        var guard = new CompletableFuture<Boolean>();
        var entered = new CountDownLatch(1);
        DataGridGroupingTest.fx(() -> {
            view.grid()
                    .setEditGuard(query -> {
                        entered.countDown();
                        return guard;
                    });
            return null;
        });
        var loading = DataGridGroupingTest.fx(view.attachment()::loadAsync);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            config.closeAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
            guard.complete(true);
            assertThrows(
                    ExecutionException.class,
                    () -> loading.toCompletableFuture().get(5, TimeUnit.SECONDS));
            DataGridGroupingTest.fx(() -> {
                assertTrue(
                        view.grid().lookupAll(".grid-persistence-status").stream()
                                .map(javafx.scene.control.Label.class::cast)
                                .anyMatch(label -> label.getText().contains("unavailable")));
                return null;
            });
        } finally {
            guard.complete(false);
            DataGridGroupingTest.fx(() -> {
                view.grid().close();
                return null;
            });
            config.discardAndCloseAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }
}
