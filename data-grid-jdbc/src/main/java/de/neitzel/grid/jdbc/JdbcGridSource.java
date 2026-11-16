package de.neitzel.grid.jdbc;

import de.neitzel.grid.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import javax.sql.DataSource;

/** Mapped, parameterized queries. The application owns the DataSource and executor. */
public final class JdbcGridSource<K, T> implements GridSource<K, T> {
    @FunctionalInterface
    public interface RowMapper<K, T> {
        GridRow<K, T> read(ResultSet result) throws SQLException;
    }

    private final DataSource dataSource;
    private final Executor executor;
    private final JdbcDialect dialect;
    private final String table, key, projection;
    private final Map<String, JdbcColumn> columns;
    private final RowMapper<K, T> mapper;
    private final int timeoutSeconds;
    private final SourceCapabilities capabilities;

    public JdbcGridSource(
            DataSource dataSource,
            Executor executor,
            JdbcDialect dialect,
            String table,
            String key,
            List<JdbcColumn> columns,
            RowMapper<K, T> mapper,
            int maximumPageSize,
            int timeoutSeconds) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.executor = Objects.requireNonNull(executor);
        this.dialect = Objects.requireNonNull(dialect);
        this.table = JdbcColumn.identifier(table);
        this.key = JdbcColumn.identifier(key);
        this.mapper = Objects.requireNonNull(mapper);
        if (timeoutSeconds < 1)
            throw new IllegalArgumentException("A positive query timeout is required");
        this.timeoutSeconds = timeoutSeconds;
        capabilities = new SourceCapabilities(true, true, maximumPageSize);
        var mapped = new LinkedHashMap<String, JdbcColumn>();
        var selected = new LinkedHashSet<String>();
        selected.add(key);
        for (var c : columns) {
            if (mapped.put(c.id(), c) != null)
                throw new IllegalArgumentException("Duplicate column");
            selected.add(c.sqlName());
        }
        this.columns = Map.copyOf(mapped);
        projection = String.join(",", selected);
    }

    public SourceCapabilities capabilities() {
        return capabilities;
    }

    public String filterExecutionId() {
        return dialect.executionId();
    }

    private JdbcColumn column(String id) {
        var c = columns.get(id);
        if (c == null) throw new IllegalArgumentException("Unknown column: " + id);
        return c;
    }

    public void validateFilter(FilterCondition filter) {
        compile(filter, new ArrayList<>());
    }

    public QueryExecution<K, T> query(GridQuery query) {
        return query(query, QueryOptions.defaults());
    }

    public QueryExecution<K, T> query(GridQuery query, QueryOptions options) {
        Objects.requireNonNull(query);
        Objects.requireNonNull(options);
        var execution = new Execution();
        try {
            capabilities.validate(query.page());
            var parameters = new ArrayList<Object>();
            String where = where(query.filters(), parameters);
            var order = new ArrayList<String>();
            for (var sort : query.sort()) {
                var c = column(sort.columnId());
                order.add(
                        (c.kind() == JdbcColumn.Kind.TEXT ? c.comparisonKey() : c.sqlName())
                                + (sort.direction() == SortKey.Direction.ASCENDING
                                        ? " ASC"
                                        : " DESC")
                                + " NULLS LAST");
            }
            order.add(key + " ASC");
            String rows =
                    "SELECT "
                            + projection
                            + " FROM "
                            + table
                            + where
                            + " ORDER BY "
                            + String.join(",", order)
                            + (query.page() instanceof PageRequest.Paged ? dialect.paging() : "");
            executor.execute(
                    () -> {
                        try {
                            execution.check();
                            execution.result.complete(
                                    execute(execution, query, options, where, rows, parameters));
                        } catch (Exception failure) {
                            execution.result.completeExceptionally(failure);
                        }
                    });
        } catch (RuntimeException failure) {
            execution.result.completeExceptionally(failure);
        }
        return execution;
    }

    private PageResult<K, T> execute(
            Execution execution,
            GridQuery query,
            QueryOptions options,
            String where,
            String sql,
            List<Object> parameters)
            throws SQLException {
        try (var connection = dataSource.getConnection()) {
            boolean auto = connection.getAutoCommit();
            int isolation = connection.getTransactionIsolation();
            if (!auto)
                throw new SQLException("Borrowed connection must be idle with autoCommit=true");
            Throwable primary = null;
            try {
                dialect.beginSnapshot(connection, timeoutSeconds);
                execution.check();
                long count;
                try (var statement =
                                execution.prepare(
                                        connection,
                                        "SELECT COUNT(*) FROM " + table + where,
                                        parameters);
                        var rs = statement.executeQuery()) {
                    rs.next();
                    count = rs.getLong(1);
                }
                execution.active.set(null);
                execution.check();
                if (query.page() instanceof PageRequest.All
                        && count > options.allWarningThreshold()
                        && !options.allApproved()) throw new AllRowsConfirmationRequired(count);
                var values = new ArrayList<>(parameters);
                if (query.page() instanceof PageRequest.Paged p) {
                    values.add(p.offset());
                    values.add(p.pageSize());
                }
                var result = new ArrayList<GridRow<K, T>>();
                try (var statement = execution.prepare(connection, sql, values);
                        var rs = statement.executeQuery()) {
                    while (rs.next()) {
                        execution.check();
                        result.add(mapper.read(rs));
                    }
                }
                execution.active.set(null);
                execution.check();
                var page =
                        new PageResult<>(
                                query.page(),
                                result,
                                OptionalLong.of(count),
                                query.page() instanceof PageRequest.Paged p
                                        && count - p.offset() > p.pageSize());
                connection.commit();
                return page;
            } catch (SQLException | RuntimeException | Error failure) {
                primary = failure;
                throw failure;
            } finally {
                execution.active.set(null);
                SQLException cleanup = null;
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    cleanup = ex;
                }
                try {
                    connection.setTransactionIsolation(isolation);
                } catch (SQLException ex) {
                    if (cleanup == null) cleanup = ex;
                    else cleanup.addSuppressed(ex);
                }
                try {
                    connection.setAutoCommit(auto);
                } catch (SQLException ex) {
                    if (cleanup == null) cleanup = ex;
                    else cleanup.addSuppressed(ex);
                }
                if (cleanup != null) {
                    if (primary != null) primary.addSuppressed(cleanup);
                    else throw cleanup;
                }
            }
        }
    }

    private String where(List<FilterCondition> filters, List<Object> args) {
        if (filters.isEmpty()) return "";
        return " WHERE "
                + String.join(
                        " AND ", filters.stream().map(f -> "(" + compile(f, args) + ")").toList());
    }

    private String compile(FilterCondition f, List<Object> args) {
        var c = column(f.columnId());
        String field = c.sqlName();
        if (f instanceof MissingFilter m)
            return field + (m.missing() ? " IS NULL" : " IS NOT NULL");
        if (f instanceof TextFilter t) {
            if (c.kind() != JdbcColumn.Kind.TEXT)
                throw new IllegalArgumentException("Text filter requires text column");
            field = t.caseSensitive() ? field : c.comparisonKey();
            String operand = t.caseSensitive() ? t.operand() : t.operand().toLowerCase(Locale.ROOT);
            if (t.operator() == TextFilter.Operator.EQUALS) {
                args.add(operand);
                return field + " = ?";
            }
            operand = operand.replace("!", "!!").replace("%", "!%").replace("_", "!_");
            args.add(
                    switch (t.operator()) {
                        case CONTAINS -> "%" + operand + "%";
                        case STARTS_WITH -> operand + "%";
                        case ENDS_WITH -> "%" + operand;
                        default -> throw new IllegalStateException();
                    });
            return field + " LIKE ? ESCAPE '!'";
        }
        if (f instanceof NumericFilter n) {
            c.number(n.operand());
            args.add(n.operand());
            if (n.operator() == NumericFilter.Operator.RANGE) {
                c.number(n.upper());
                args.add(n.upper());
                return field + " BETWEEN ? AND ?";
            }
            return field
                    + switch (n.operator()) {
                        case EQUALS -> " = ?";
                        case LESS -> " < ?";
                        case LESS_OR_EQUAL -> " <= ?";
                        case GREATER -> " > ?";
                        case GREATER_OR_EQUAL -> " >= ?";
                        default -> throw new IllegalStateException();
                    };
        }
        if (f instanceof ValueFilter v) {
            var marks = new ArrayList<String>();
            for (var value : v.values()) {
                args.add(c.value(value));
                marks.add("?");
            }
            String membership =
                    marks.isEmpty() ? "1=0" : field + " IN (" + String.join(",", marks) + ")";
            return membership + (v.includeMissing() ? " OR " + field + " IS NULL" : "");
        }
        throw new UnsupportedOperationException("JDBC does not support filter: " + f.typeId());
    }

    private final class Execution implements QueryExecution<K, T> {
        final CompletableFuture<PageResult<K, T>> result = new CompletableFuture<>();
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicReference<Statement> active = new AtomicReference<>();

        public CompletionStage<PageResult<K, T>> result() {
            return result;
        }

        public void cancel() {
            cancelled.set(true);
            var statement = active.get();
            if (statement != null)
                try {
                    statement.cancel();
                } catch (SQLException ignored) {
                    /* Best effort; obsolete publication is independently rejected. */
                }
            result.cancel(false);
        }

        void check() {
            if (cancelled.get()) throw new CancellationException("JDBC request cancelled");
        }

        PreparedStatement prepare(Connection connection, String sql, List<Object> parameters)
                throws SQLException {
            check();
            var statement = connection.prepareStatement(sql);
            try {
                statement.setQueryTimeout(timeoutSeconds);
                for (int i = 0; i < parameters.size(); i++)
                    statement.setObject(i + 1, parameters.get(i));
                active.set(statement);
                check();
                return statement;
            } catch (SQLException | RuntimeException failure) {
                try {
                    statement.close();
                } catch (SQLException close) {
                    failure.addSuppressed(close);
                }
                throw failure;
            }
        }
    }
}
