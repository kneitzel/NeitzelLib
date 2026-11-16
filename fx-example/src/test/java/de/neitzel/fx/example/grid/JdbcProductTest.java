package de.neitzel.fx.example.grid;

import static org.junit.jupiter.api.Assertions.*;

import de.neitzel.grid.*;
import de.neitzel.grid.jdbc.*;

import org.junit.jupiter.api.*;

import java.lang.reflect.*;
import java.math.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import javax.sql.DataSource;

class JdbcProductTest {
    static ProductCatalog catalog;

    @BeforeAll
    static void start() throws Exception {
        catalog = new ProductCatalog(10000);
    }

    @AfterAll
    static void stop() {
        catalog.close();
    }

    @BeforeEach
    void reset() throws Exception {
        catalog.reset();
    }

    static PageResult<Long, Product> result(GridSource<Long, Product> source, GridQuery q)
            throws Exception {
        return source.query(q, new QueryOptions(1000, true))
                .result()
                .toCompletableFuture()
                .get(20, TimeUnit.SECONDS);
    }

    static GridQuery query(List<FilterCondition> filters, List<SortKey> sort, PageRequest page) {
        return new GridQuery(filters, sort, page);
    }

    void parity(List<FilterCondition> filters, List<SortKey> sort, PageRequest page)
            throws Exception {
        var q = query(filters, sort, page);
        assertEquals(result(catalog.memory, q), result(catalog.jdbc, q), q.toString());
    }

    @Test
    void everyOperatorAndUnicodeExactChecklistAndOrderingAgree() throws Exception {
        for (var op : TextFilter.Operator.values())
            for (boolean sensitive : List.of(false, true))
                for (String text :
                        List.of(
                                "Alpha",
                                "a",
                                "%",
                                "_",
                                "!",
                                "İ",
                                "ß",
                                "ss",
                                "😀",
                                "é",
                                "e\u0301",
                                "' OR 1=1 --"))
                    parity(
                            List.of(new TextFilter("name", op, text, sensitive)),
                            List.of(new SortKey("name", SortKey.Direction.ASCENDING)),
                            new PageRequest.All());
        parity(
                List.of(new TextFilter("name", TextFilter.Operator.EQUALS, "", true)),
                List.of(),
                new PageRequest.All());
        for (var op : NumericFilter.Operator.values())
            for (String col : List.of("quantity", "price"))
                parity(
                        List.of(
                                new NumericFilter(
                                        col,
                                        op,
                                        new BigDecimal("1.0"),
                                        op == NumericFilter.Operator.RANGE
                                                ? new BigDecimal("20.00")
                                                : null)),
                        List.of(),
                        new PageRequest.All());
        for (String col : List.of("name", "category", "quantity", "price"))
            for (boolean missing : List.of(true, false))
                parity(
                        List.of(new MissingFilter(col, missing)),
                        List.of(),
                        new PageRequest.Paged(0, 50));
        for (boolean missing : List.of(true, false)) {
            parity(
                    List.of(
                            new ValueFilter(
                                    "name",
                                    Set.of(
                                            new FilterValue.Text("Alpha"),
                                            new FilterValue.Text("a%b")),
                                    missing)),
                    List.of(),
                    new PageRequest.All());
            parity(
                    List.of(new ValueFilter("name", Set.of(), missing)),
                    List.of(),
                    new PageRequest.All());
            parity(
                    List.of(
                            new ValueFilter(
                                    "quantity", Set.of(new FilterValue.IntegerValue(1)), missing)),
                    List.of(),
                    new PageRequest.All());
            parity(
                    List.of(
                            new ValueFilter(
                                    "price",
                                    Set.of(new FilterValue.Decimal(new BigDecimal("1.0"))),
                                    missing)),
                    List.of(),
                    new PageRequest.All());
        }
        for (String col : List.of("name", "category", "quantity", "price"))
            for (var dir : SortKey.Direction.values())
                parity(List.of(), List.of(new SortKey(col, dir)), new PageRequest.All());
        parity(
                List.of(
                        new TextFilter("name", TextFilter.Operator.CONTAINS, "product", false),
                        new NumericFilter(
                                "price", NumericFilter.Operator.LESS, new BigDecimal("10"), null)),
                List.of(
                        new SortKey("category", SortKey.Direction.DESCENDING),
                        new SortKey("price", SortKey.Direction.ASCENDING)),
                new PageRequest.Paged(3, 25));
        for (int page : List.of(0, 1, 199, 200, 999))
            parity(List.of(), List.of(), new PageRequest.Paged(page, 50));
    }

    @Test
    void unsupportedAndInvalidSemanticsFailBeforeBorrowing() throws Exception {
        var tracking = new Tracking(catalog.database);
        var source = source(tracking, ProductCatalog::read);
        for (FilterCondition invalid :
                List.of(
                        new LocalNameLength("name"),
                        new TextFilter("price", TextFilter.Operator.EQUALS, "1", true),
                        new NumericFilter(
                                "quantity",
                                NumericFilter.Operator.EQUALS,
                                new BigDecimal("1.1"),
                                null),
                        new NumericFilter(
                                "price",
                                NumericFilter.Operator.EQUALS,
                                new BigDecimal("1.001"),
                                null),
                        new NumericFilter(
                                "price",
                                NumericFilter.Operator.EQUALS,
                                new BigDecimal("10000000000"),
                                null),
                        new ValueFilter("quantity", Set.of(new FilterValue.Text("1")), false),
                        new MissingFilter("not_mapped", true))) {
            assertThrows(RuntimeException.class, () -> source.validateFilter(invalid));
            assertThrows(
                    ExecutionException.class,
                    () ->
                            result(
                                    source,
                                    query(
                                            List.of(invalid),
                                            List.of(),
                                            new PageRequest.Paged(0, 50))));
        }
        assertEquals(0, tracking.opened.get());
        assertThrows(
                IllegalArgumentException.class,
                () -> JdbcColumn.text("bad", "name; DROP TABLE products", "key"));
        assertEquals(
                2,
                result(
                                catalog.memory,
                                query(
                                        List.of(
                                                new ValueFilter(
                                                        "name",
                                                        Set.of(
                                                                new FilterValue.Text("Alpha"),
                                                                new FilterValue.Text("alpha")),
                                                        false)),
                                        List.of(),
                                        new PageRequest.All()))
                        .rows()
                        .size());
    }

    @Test
    void snapshotContainsCountAndRowsDespiteConcurrentWriter() throws Exception {
        var tracking = new Tracking(catalog.database);
        tracking.afterCount =
                () -> {
                    try (var connection = catalog.database.getConnection()) {
                        ProductCatalog.insert(
                                connection,
                                List.of(
                                        new Product(
                                                10001,
                                                "Concurrent",
                                                "Office",
                                                1L,
                                                new BigDecimal("1.00"))));
                    }
                };
        var page =
                result(
                        source(tracking, ProductCatalog::read),
                        query(List.of(), List.of(), new PageRequest.All()));
        assertEquals(10000, page.total().orElseThrow());
        assertEquals(10000, page.rows().size());
        assertEquals(
                10001,
                result(catalog.jdbc, query(List.of(), List.of(), new PageRequest.All()))
                        .rows()
                        .size());
        tracking.assertReleased();
        assertEquals(1, tracking.opened.get());
    }

    @Test
    void allWarningClosesResourcesAndRecountsOnApproval() throws Exception {
        var tracking = new Tracking(catalog.database);
        var source = source(tracking, ProductCatalog::read);
        var q = query(List.of(), List.of(), new PageRequest.All());
        var error =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                source.query(q, new QueryOptions(9999, false))
                                        .result()
                                        .toCompletableFuture()
                                        .get());
        assertInstanceOf(AllRowsConfirmationRequired.class, error.getCause());
        assertEquals(0, tracking.rowQueries.get());
        tracking.assertReleased();
        catalog.insert();
        var page =
                source.query(q, new QueryOptions(9999, true)).result().toCompletableFuture().get();
        assertEquals(10001, page.rows().size());
        tracking.assertReleased();
        assertEquals(
                10001,
                source.query(q, new QueryOptions(10001, false))
                        .result()
                        .toCompletableFuture()
                        .get()
                        .rows()
                        .size());
        var small =
                query(
                        List.of(
                                new NumericFilter(
                                        "id",
                                        NumericFilter.Operator.LESS_OR_EQUAL,
                                        new BigDecimal("1000"),
                                        null)),
                        List.of(),
                        new PageRequest.All());
        assertEquals(
                1000,
                source.query(small, QueryOptions.defaults())
                        .result()
                        .toCompletableFuture()
                        .get()
                        .rows()
                        .size());
        var above =
                query(
                        List.of(
                                new NumericFilter(
                                        "id",
                                        NumericFilter.Operator.LESS_OR_EQUAL,
                                        new BigDecimal("1001"),
                                        null)),
                        List.of(),
                        new PageRequest.All());
        assertInstanceOf(
                AllRowsConfirmationRequired.class,
                assertThrows(
                                ExecutionException.class,
                                () ->
                                        source.query(above, QueryOptions.defaults())
                                                .result()
                                                .toCompletableFuture()
                                                .get())
                        .getCause());
        tracking.assertReleased();
    }

    @Test
    void failuresCancellationAndExecutorRejectionReleaseResources() throws Exception {
        var tracking = new Tracking(catalog.database);
        var source =
                source(
                        tracking,
                        rs -> {
                            throw new SQLException("Injected mapper failure");
                        });
        assertThrows(
                ExecutionException.class,
                () -> result(source, query(List.of(), List.of(), new PageRequest.Paged(0, 50))));
        tracking.assertReleased();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var blocking =
                source(
                        tracking,
                        rs -> {
                            entered.countDown();
                            try {
                                if (!release.await(5, TimeUnit.SECONDS))
                                    throw new SQLException("test timed out");
                            } catch (InterruptedException e) {
                                throw new SQLException(e);
                            }
                            return ProductCatalog.read(rs);
                        });
        var execution = blocking.query(query(List.of(), List.of(), new PageRequest.Paged(0, 50)));
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        execution.cancel();
        release.countDown();
        assertThrows(
                CancellationException.class, () -> execution.result().toCompletableFuture().join());
        long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (tracking.closed.get() < tracking.opened.get() && System.nanoTime() < limit)
            Thread.sleep(10);
        tracking.assertReleased();
        assertTrue(tracking.cancels.get() > 0);
        var rejected =
                new JdbcGridSource<Long, Product>(
                        tracking.proxy(),
                        r -> {
                            throw new RejectedExecutionException("closed executor");
                        },
                        JdbcDialect.h2(),
                        "products",
                        "id",
                        ProductCatalog.jdbcColumns(),
                        ProductCatalog::read,
                        250,
                        3);
        int before = tracking.opened.get();
        assertThrows(
                ExecutionException.class,
                () -> result(rejected, query(List.of(), List.of(), new PageRequest.Paged(0, 50))));
        assertEquals(before, tracking.opened.get());
    }

    @Test
    void queuedCancellationAndUnqualifiedCollationAreRejected() throws Exception {
        var tracking = new Tracking(catalog.database);
        var queued = new java.util.ArrayList<Runnable>();
        var source =
                new JdbcGridSource<Long, Product>(
                        tracking.proxy(),
                        queued::add,
                        JdbcDialect.h2(),
                        "products",
                        "id",
                        ProductCatalog.jdbcColumns(),
                        ProductCatalog::read,
                        250,
                        3);
        var execution = source.query(query(List.of(), List.of(), new PageRequest.Paged(0, 50)));
        execution.cancel();
        queued.removeFirst().run();
        assertEquals(0, tracking.opened.get());
        var other =
                org.h2.jdbcx.JdbcConnectionPool.create(
                        "jdbc:h2:mem:collated_" + UUID.randomUUID(), "sa", "");
        try (var anchor = other.getConnection()) {
            try (var statement = anchor.createStatement()) {
                statement.execute("SET COLLATION ENGLISH");
            }
            var unqualified =
                    new JdbcGridSource<Long, Product>(
                            other,
                            Runnable::run,
                            JdbcDialect.h2(),
                            "products",
                            "id",
                            ProductCatalog.jdbcColumns(),
                            ProductCatalog::read,
                            250,
                            3);
            var error =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    result(
                                            unqualified,
                                            query(
                                                    List.of(),
                                                    List.of(),
                                                    new PageRequest.Paged(0, 50))));
            assertTrue(error.getCause().getMessage().contains("collation"));
        } finally {
            other.dispose();
        }
    }

    @Test
    void mutationKeysAndTimings() throws Exception {
        catalog.insert();
        parity(
                List.of(
                        new TextFilter(
                                "name", TextFilter.Operator.EQUALS, "INSERTED 10001", false)),
                List.of(),
                new PageRequest.All());
        catalog.delete(10001);
        parity(List.of(), List.of(), new PageRequest.Paged(199, 50));
        var lines = new ArrayList<String>();
        lines.add(
                "10,000 immutable Product rows; source materialization only, excludes FX"
                        + " Components; single observed run, no performance guarantee.");
        for (var source : List.of(catalog.memory, catalog.jdbc))
            for (PageRequest page :
                    List.of(
                            new PageRequest.Paged(0, 50),
                            new PageRequest.Paged(199, 50),
                            new PageRequest.All())) {
                long before = System.nanoTime();
                var response =
                        result(
                                source,
                                query(
                                        List.of(),
                                        List.of(new SortKey("name", SortKey.Direction.ASCENDING)),
                                        page));
                lines.add(
                        source.filterExecutionId()
                                + " "
                                + page
                                + " rows="
                                + response.rows().size()
                                + " durationMs="
                                + (System.nanoTime() - before) / 1_000_000.0);
            }
        Path path = Path.of("target/jdbc-evidence");
        Files.createDirectories(path);
        Files.write(path.resolve("timings.txt"), lines);
    }

    JdbcGridSource<Long, Product> source(
            Tracking tracking, JdbcGridSource.RowMapper<Long, Product> mapper) {
        return new JdbcGridSource<>(
                tracking.proxy(),
                catalog.workers,
                JdbcDialect.h2(),
                "products",
                "id",
                ProductCatalog.jdbcColumns(),
                mapper,
                250,
                3);
    }

    @FunctionalInterface
    interface SqlAction {
        void run() throws SQLException;
    }

    static final class Tracking {
        final DataSource target;
        final AtomicInteger opened = new AtomicInteger(),
                closed = new AtomicInteger(),
                statements = new AtomicInteger(),
                results = new AtomicInteger(),
                rowQueries = new AtomicInteger(),
                cancels = new AtomicInteger();
        final List<Throwable> problems = new CopyOnWriteArrayList<>();
        SqlAction afterCount = () -> {};

        Tracking(DataSource target) {
            this.target = target;
        }

        DataSource proxy() {
            return wrap(
                    DataSource.class,
                    target,
                    (object, method, args) -> {
                        Object value = invoke(target, method, args);
                        if (method.getName().equals("getConnection")) {
                            opened.incrementAndGet();
                            return connection((Connection) value);
                        }
                        return value;
                    });
        }

        Connection connection(Connection c) throws SQLException {
            int original = c.getTransactionIsolation();
            boolean auto = c.getAutoCommit();
            return wrap(
                    Connection.class,
                    c,
                    (object, method, args) -> {
                        if (method.getName().equals("close")) {
                            try {
                                assertEquals(auto, c.getAutoCommit());
                                assertEquals(original, c.getTransactionIsolation());
                            } catch (Throwable problem) {
                                problems.add(problem);
                            }
                            closed.incrementAndGet();
                        }
                        Object value = invoke(c, method, args);
                        if (value instanceof PreparedStatement s)
                            return statement(s, (String) args[0]);
                        return value;
                    });
        }

        PreparedStatement statement(PreparedStatement s, String sql) {
            statements.incrementAndGet();
            boolean count = sql.startsWith("SELECT COUNT");
            if (sql.startsWith("SELECT id")) rowQueries.incrementAndGet();
            return wrap(
                    PreparedStatement.class,
                    s,
                    (object, method, args) -> {
                        if (method.getName().equals("close")) statements.decrementAndGet();
                        if (method.getName().equals("cancel")) cancels.incrementAndGet();
                        if (method.getName().equals("executeQuery")
                                && !sql.contains("INFORMATION_SCHEMA"))
                            assertEquals(3, s.getQueryTimeout());
                        Object value = invoke(s, method, args);
                        if (value instanceof ResultSet rs) {
                            results.incrementAndGet();
                            return wrap(
                                    ResultSet.class,
                                    rs,
                                    (o, m, a) -> {
                                        Object answer = invoke(rs, m, a);
                                        if (m.getName().equals("close")) {
                                            results.decrementAndGet();
                                            if (count) afterCount.run();
                                        }
                                        return answer;
                                    });
                        }
                        return value;
                    });
        }

        void assertReleased() {
            assertEquals(opened.get(), closed.get());
            assertEquals(0, statements.get());
            assertEquals(0, results.get());
            assertTrue(problems.isEmpty(), problems.toString());
        }

        static Object invoke(Object object, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(object, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        static <T> T wrap(Class<T> type, T target, InvocationHandler handler) {
            return type.cast(
                    Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
        }
    }
}
