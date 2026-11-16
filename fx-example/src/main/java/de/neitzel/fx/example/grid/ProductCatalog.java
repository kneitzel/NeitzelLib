package de.neitzel.fx.example.grid;

import de.neitzel.grid.*;
import de.neitzel.grid.jdbc.*;

import org.h2.jdbcx.JdbcConnectionPool;

import java.math.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Application-owned database, fixture service and execution pool. */
public final class ProductCatalog implements AutoCloseable {
    public final ExecutorService workers;
    public final JdbcConnectionPool database;
    public final MemoryGridSource<Long, Product> memory;
    public final JdbcGridSource<Long, Product> jdbc;
    private List<Product> products = List.of();
    private final int size;
    // Keeps this in-memory database alive without DB_CLOSE_DELAY=-1 leaking it after close.
    private final Connection anchor;

    public ProductCatalog(int size) throws SQLException {
        this(
                size,
                JdbcConnectionPool.create("jdbc:h2:mem:grid_" + UUID.randomUUID(), "sa", ""),
                Executors.newFixedThreadPool(4));
    }

    ProductCatalog(int size, JdbcConnectionPool database, ExecutorService workers)
            throws SQLException {
        this.size = size;
        this.database = Objects.requireNonNull(database);
        this.workers = Objects.requireNonNull(workers);
        Connection borrowed = null;
        try {
            borrowed = database.getConnection();
            anchor = borrowed;
            try (var s = anchor.createStatement()) {
                s.execute(
                        "CREATE TABLE products (id BIGINT PRIMARY KEY, name VARCHAR(200), name_key"
                                + " VARCHAR(250), category VARCHAR(100), category_key VARCHAR(120),"
                                + " quantity BIGINT, price DECIMAL(12,2))");
            }
            reset();
            memory =
                    new MemoryGridSource<>(
                            this::snapshot,
                            Product::id,
                            Long::compare,
                            memoryColumns(),
                            workers,
                            250);
            jdbc =
                    new JdbcGridSource<>(
                            database,
                            workers,
                            JdbcDialect.h2(),
                            "products",
                            "id",
                            jdbcColumns(),
                            ProductCatalog::read,
                            250,
                            10);
        } catch (SQLException | RuntimeException error) {
            try {
                DemoResources.closeAll(borrowed, database::dispose, workers::shutdown);
            } catch (RuntimeException cleanup) {
                error.addSuppressed(cleanup);
            }
            throw error;
        }
    }

    public static List<Product> fixtures(int size) {
        var rows = new ArrayList<Product>();
        String[] specials = {
            null,
            "",
            "Alpha",
            "alpha",
            "Äpfel",
            "İstanbul",
            "ß",
            "ss",
            "a%b",
            "a_b",
            "!literal",
            "O'Reilly",
            "😀",
            "𐀀",
            "",
            "é",
            "é",
            " trailing "
        };
        for (int i = 1; i <= size; i++)
            rows.add(
                    new Product(
                            i,
                            i <= specials.length ? specials[i - 1] : "Product " + (i % 137),
                            i % 17 == 0
                                    ? null
                                    : List.of("Hardware", "Office", "Food", "").get(i % 4),
                            i % 19 == 0 ? null : (long) (i % 80),
                            i % 23 == 0 ? null : BigDecimal.valueOf(i % 10007, 2)));
        return List.copyOf(rows);
    }

    public static List<JdbcColumn> jdbcColumns() {
        return List.of(
                JdbcColumn.integer("id", "id"),
                JdbcColumn.text("name", "name", "name_key"),
                JdbcColumn.text("category", "category", "category_key"),
                JdbcColumn.integer("quantity", "quantity"),
                JdbcColumn.decimal("price", "price", 12, 2));
    }

    public static List<MemoryColumn<Product>> memoryColumns() {
        return List.of(
                new MemoryColumn<>(
                        "id", MemoryColumn.Kind.INTEGER, p -> new FilterValue.IntegerValue(p.id())),
                new MemoryColumn<>(
                        "name",
                        MemoryColumn.Kind.TEXT,
                        p -> p.name() == null ? null : new FilterValue.Text(p.name()),
                        Map.of(
                                "demo.name-length",
                                f -> {
                                    if (!(f instanceof LocalNameLength))
                                        throw new IllegalArgumentException(
                                                "Wrong custom condition");
                                    return v ->
                                            v instanceof FilterValue.Text t
                                                    && t.value().length() <= 5;
                                })),
                new MemoryColumn<>(
                        "category",
                        MemoryColumn.Kind.TEXT,
                        p -> p.category() == null ? null : new FilterValue.Text(p.category())),
                new MemoryColumn<>(
                        "quantity",
                        MemoryColumn.Kind.INTEGER,
                        p ->
                                p.quantity() == null
                                        ? null
                                        : new FilterValue.IntegerValue(p.quantity())),
                new MemoryColumn<>(
                        "price",
                        MemoryColumn.Kind.DECIMAL,
                        p -> p.price() == null ? null : new FilterValue.Decimal(p.price())));
    }

    public static GridRow<Long, Product> read(ResultSet rs) throws SQLException {
        var p =
                new Product(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("category"),
                        (Long) rs.getObject("quantity"),
                        rs.getBigDecimal("price"));
        return new GridRow<>(p.id(), p);
    }

    public synchronized List<Product> snapshot() {
        return products;
    }

    public synchronized void reset() throws SQLException {
        replace(fixtures(size));
    }

    public synchronized void insert() throws SQLException {
        var copy = new ArrayList<>(products);
        long id = copy.stream().mapToLong(Product::id).max().orElse(0) + 1;
        var p = new Product(id, "Inserted " + id, "Office", 1L, new BigDecimal("1.00"));
        try (var c = database.getConnection()) {
            insert(c, List.of(p));
        }
        copy.add(p);
        products = List.copyOf(copy);
    }

    public synchronized void delete(long id) throws SQLException {
        try (var c = database.getConnection();
                var s = c.prepareStatement("DELETE FROM products WHERE id=?")) {
            s.setLong(1, id);
            s.executeUpdate();
        }
        products = products.stream().filter(p -> p.id() != id).toList();
    }

    private void replace(List<Product> rows) throws SQLException {
        try (var c = database.getConnection()) {
            c.setAutoCommit(false);
            try (var s = c.createStatement()) {
                s.executeUpdate("DELETE FROM products");
            }
            insert(c, rows);
            c.commit();
        }
        products = List.copyOf(rows);
    }

    public static void insert(Connection c, List<Product> rows) throws SQLException {
        try (var s = c.prepareStatement("INSERT INTO products VALUES (?,?,?,?,?,?,?)")) {
            for (var p : rows) {
                s.setLong(1, p.id());
                s.setString(2, p.name());
                s.setString(3, key(p.name()));
                s.setString(4, p.category());
                s.setString(5, key(p.category()));
                s.setObject(6, p.quantity());
                s.setBigDecimal(7, p.price());
                s.addBatch();
            }
            s.executeBatch();
        }
    }

    private static String key(String text) {
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    public void close() {
        workers.shutdown();
        try {
            if (!workers.awaitTermination(15, TimeUnit.SECONDS))
                throw new IllegalStateException("Queries did not finish before database shutdown");
            DemoResources.closeAll(anchor, database::dispose);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }
}
