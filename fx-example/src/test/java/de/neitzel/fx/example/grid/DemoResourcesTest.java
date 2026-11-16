package de.neitzel.fx.example.grid;

import static org.junit.jupiter.api.Assertions.*;

import org.h2.jdbcx.JdbcConnectionPool;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

class DemoResourcesTest {
    @Test
    void independentCleanupContinuesAndPreservesFailures() {
        var attempts = new ArrayList<String>();
        var first = new java.sql.SQLException("anchor close");
        var second = new IllegalStateException("pool close");
        var failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                DemoResources.closeAll(
                                        () -> {
                                            attempts.add("anchor");
                                            throw first;
                                        },
                                        () -> {
                                            attempts.add("pool");
                                            throw second;
                                        },
                                        () -> attempts.add("executor")));
        assertEquals(List.of("anchor", "pool", "executor"), attempts);
        assertSame(first, failure.getCause());
        assertArrayEquals(new Throwable[] {second}, failure.getSuppressed());
    }

    @Test
    void failedInitialBorrowStillReleasesOwnedExecutor() {
        var pool = JdbcConnectionPool.create("jdbc:h2:mem:closed_" + UUID.randomUUID(), "sa", "");
        var workers = Executors.newSingleThreadExecutor();
        pool.dispose();
        try {
            assertThrows(Exception.class, () -> new ProductCatalog(10, pool, workers));
            assertTrue(workers.isShutdown());
        } finally {
            workers.shutdown();
            pool.dispose();
        }
    }
}
