# Product DataGrid: memory and H2

This separate launcher uses the accepted DataGrid APIs without changing the person-desk application or legacy Table behavior. The application owns an embedded H2 **2.5.252** pool, its anchor connection, a four-worker executor, completion timer, immutable product fixtures and saved setup file. No server, credentials or stored procedures are needed. JDBC is in the optional `data-grid-jdbc` module; H2 remains example-owned.

From the repository root with JDK 25, build without installing runtime artifacts:

```sh
./mvnw -pl fx-example -am package -DskipTests
java -cp 'fx-example/target/fx-example-1.0.8-SNAPSHOT.jar:fx-example/target/libs/*' de.neitzel.fx.example.grid.ProductGridMain
```

The default catalog has 10,000 products. Both backends use identical stable IDs, names, categories, quantities and DECIMAL(12,2) prices. Fixtures include duplicate sort values, nulls, empty text, mixed case, Unicode, apostrophes and literal SQL wildcard characters. One `ProductCatalog` service maintains original text and Locale.ROOT comparison keys together on insertion/reset. Mutations run on application workers and explicitly refresh the grid; browsing is read-only.

## Interactive walkthrough

1. Browse 50 rows initially. Use header sorting and first/previous/next/last, then choose 25/100/250. Name filtering has a developer-configured **current-page** checklist; its scope is shown by the editor, and candidate values are exact originals. Other columns use comparison/missing modes. Source-wide checklist configuration remains available through the provider API but is not silently inferred here.
2. Open text/quantity/price filters. `%`, `_`, `!` and apostrophes are literal. Try a decimal range, empty text equality, missing prices and an empty checklist. Toggle **Custom budget editor** to use a separate FXML/controller that emits the same portable numeric condition. It runs on both sources and persists with the built-in numeric codec.
3. Select **Memory**, then **Memory-only filter** (names of at most five UTF-16 units). Switch to H2: the condition is rejected visibly, the previous result remains, and no SQL silently substitutes different behavior. Clear filters, then switch again. Saving a memory-only condition is also rejected because no codec is registered.
4. Toggle outside-click acceptance and OK/Cancel independently of the editor. Enter/Escape use the existing popup lifecycle. Native input qualification remains under the previously recorded waiver; these options do not claim a new OS-input qualification.
5. Choose a theme, select a row, widen Name or move Price first. **Save setup** writes atomically to `~/.neitzellib-example/products-setup.json`; **Load setup** decodes/validates and stages its layout/query atomically. Invalid JSON, unsupported versions/conditions and execution failures are visible and retain committed layout/data. Setup data never contains credentials, row data, selection or warning approval. **Load v1 setup** demonstrates an explicit migration from the former `group` column to `category`, updating column order, sort and text/checklist/missing conditions before querying the current definition version 2.
6. Choose **All**. Above the configurable default threshold of 1,000 matches, Continue/Cancel explains that all matching rows become real Components, consuming memory and potentially blocking responsiveness. Exactly the threshold does not warn. Cancel retains the previous setup/page. Loading a saved All setup asks again. Filtering, source switching or a newer request invalidates old approval. No connection stays borrowed while waiting for an answer.
7. Use **Slow next refresh** to inspect loading while old rows remain unavailable. **Fail next refresh** exposes the error; Retry succeeds normally. **Late success then failure** leaves the later failure visible. **Late failure then success** leaves the later success visible. These are deterministic completion delays/failures around real requests, not simulated SQL slowness; cancel-resistant completions deliberately exercise stale-result rejection.
8. Open **Request diagnostics** for local request IDs, requested/displayed pages, duration, row count, cancellation and obsolete completions. Source IDs restart for each wrapper; the selected source and coordinator generation disambiguate them.
9. Insert/delete selected/reset fixtures and refresh. Navigate to the last page and **Delete displayed page** to exercise bounded recovery when that page disappears. Deletion updates both datasets; this is demonstration tooling, not a CRUD framework.

## Qualification and limits

`JdbcProductTest` compares memory/SQL results for every supported condition/operator, exact membership, Unicode ordering, ties, boundaries, nulls and decimals. It injects a writer between count and row queries, observes borrowed resources/settings, checks cancellation and mapper failure, rejects invalid mappings before connection acquisition, and verifies the All warning race with a changed dataset.

`ProductGridDemoTest` loads real row/editor FXML, switches sources/themes, tests saving/restoring, visible capability errors, late responses, Retry, disappearing pages, cancelled/superseded All decisions and restored-All warnings. It measures an explicitly approved 10,000-row FXML materialization. New FX tests capture uncaught application-thread errors.

Outputs are `target/jdbc-evidence/{timings.txt,fx-materialization.txt,demo.png}`. Source timings include the full source query but exclude UI Components; the FX timing includes All confirmation scheduling, query and UI publication. These are single-run observations, not benchmarks or scale guarantees. Offset paging can require scanning/sorting earlier rows. All mode is unvirtualized and its render cost is substantially greater than a page. The optional 100,000-row run is not part of this qualification.

H2's default collation and snapshot behavior are the qualified database semantics. No claim covers other dialects, linguistic collation, JPA/Hibernate, arbitrary joins, date/time filters or arbitrary application overrides.
