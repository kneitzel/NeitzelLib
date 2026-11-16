# Optional JDBC DataGrid source

`data-grid-jdbc` depends only on the neutral `data-grid` contracts and the JDK JDBC API. It adds no driver or JavaFX dependency. The application supplies a DataSource, executor, approved simple table/key/column identifiers and a row mapper. The stable key must be unique and non-null. The initial dialect is H2, qualified with **2.5.252**; other databases are not claimed compatible.

```java
var source = new JdbcGridSource<Long, Product>(
    dataSource, applicationExecutor, JdbcDialect.h2(), "products", "id",
    List.of(JdbcColumn.text("name", "name", "name_key"),
            JdbcColumn.decimal("price", "price", 12, 2)),
    rs -> new GridRow<>(rs.getLong("id"), mapProduct(rs)), 250, 10);
```

The projection includes the stable key and all mapped physical columns. Identifiers accept only simple ASCII names, supplied by developers; SQL expressions, arbitrary joins and user-provided identifiers are intentionally unavailable. Filter operands and paging values use bound parameters. Custom conditions without a translator are explicitly rejected, never dropped. Date/time and boolean mappings are outside this initial adapter.

## Matching and consistency

Text mappings require a companion key column maintained with Java `Locale.ROOT` lowercasing whenever original values change. Store original text unchanged; checklist equality uses it. The adapter does not repair inconsistent application data or use SQL LOWER. Text sorting uses comparison keys; nulls sort last in either direction, followed by the stable key ascending. The H2 dialect rejects configured linguistic collation; default OFF collation is qualified against Java String comparison, including supplementary Unicode and combining marks. LIKE escapes `%`, `_` and the escape character `!`; all operands remain literal.

Integer operands must fit a signed long exactly. Decimal mappings enforce precision and scale with no rounding; numerically equal values with extra trailing zeros remain valid. The application must use matching SQL types (the example uses BIGINT and DECIMAL(12,2)). Missing/present, exact checklist membership and AND-combined filters are supported. The translator validates both condition type and column type before borrowing a connection.

Each request borrows one idle auto-commit connection, checks H2/collation, starts H2's vendor SNAPSHOT isolation (6), counts and fetches in the same transaction, commits before returning, and restores isolation/auto-commit before close. A non-auto-commit borrowed connection is rejected rather than committing an application transaction. The mapper must not mutate connection settings or execute writes. The application owns transaction cleanliness and pool reset policy. Close failures fail the request; cleanup errors are retained as suppressed exceptions.

All-mode checks its warning threshold after counting and before selecting rows. If approval is required, it rolls back and releases all borrowed resources before completing with `AllRowsConfirmationRequired`. Approval starts a fresh snapshot and recounts: it acknowledges an unpaged load, not a fixed maximum number of rows. The existing coordinator handles query-scoped approval and stale completions. Never save approval in a setup.

Every prepared query has the configured JDBC timeout. Cancellation attempts `Statement.cancel`, marks the request cancelled and stops mapping; cancellation is best effort and does not interrupt embedded driver threads. A cancelled future can complete before worker cleanup finishes, so application shutdown must drain the executor before disposing the DataSource. Closing a DataGrid does not own either resource.

The example explicitly supplies current-page checklist candidates; the adapter does not pretend a page is a complete source-wide candidate set. Applications needing source-wide candidates supply their own `FilterCandidates.Provider`, independently of SQL condition execution.

See [the complete runnable example](../fx-example/docs/product-grid.md) for fixture parity, fault controls, saved setups and limitations.
