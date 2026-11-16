package de.neitzel.grid;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/**
 * Local source with caller-thread scalar snapshot capture and executor-based query processing. The
 * supplier must return a consistent view; FX-owned lists may be captured on the FX caller thread.
 * Captured scalar values are immutable; row models remain application-owned. No executor is closed.
 */
public final class MemoryGridSource<K, T> implements GridSource<K, T> {
    private final Supplier<? extends Collection<T>> data;
    private final Function<T, K> key;
    private final Comparator<K> keyOrder;
    private final Map<String, MemoryColumn<T>> columns;
    private final Executor executor;
    private final SourceCapabilities capabilities;

    private record Snapshot<K, T>(K key, T model, Map<String, FilterValue> values) {}

    public MemoryGridSource(
            Supplier<? extends Collection<T>> data,
            Function<T, K> key,
            Comparator<K> keyOrder,
            List<MemoryColumn<T>> columns,
            Executor executor,
            int maximumPageSize) {
        this.data = Objects.requireNonNull(data);
        this.key = Objects.requireNonNull(key);
        this.keyOrder = Objects.requireNonNull(keyOrder);
        this.executor = Objects.requireNonNull(executor);
        Map<String, MemoryColumn<T>> mapped = new LinkedHashMap<>();
        for (var column : columns)
            if (mapped.putIfAbsent(column.id(), column) != null)
                throw new IllegalArgumentException("Duplicate column mapping");
        this.columns = Map.copyOf(mapped);
        capabilities = new SourceCapabilities(true, true, maximumPageSize);
    }

    public SourceCapabilities capabilities() {
        return capabilities;
    }

    public QueryExecution<K, T> query(GridQuery query) {
        return query(query, QueryOptions.defaults());
    }

    public QueryExecution<K, T> query(GridQuery query, QueryOptions options) {
        CompletableFuture<PageResult<K, T>> result = new CompletableFuture<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        try {
            Objects.requireNonNull(query);
            Objects.requireNonNull(options);
            capabilities.validate(query.page());
            List<Predicate<Snapshot<K, T>>> predicates = new ArrayList<>();
            for (var condition : query.filters()) {
                var column = column(condition.columnId());
                var test = compile(column, condition);
                predicates.add(row -> test.test(row.values().get(column.id())));
            }
            for (var sort : query.sort()) column(sort.columnId());
            List<Snapshot<K, T>> snapshot = new ArrayList<>();
            Set<K> keys = new HashSet<>();
            for (T model : List.copyOf(data.get())) {
                K id = Objects.requireNonNull(key.apply(model), "Missing row key");
                if (!keys.add(id)) throw new IllegalArgumentException("Duplicate row key");
                Map<String, FilterValue> values = new HashMap<>();
                for (var column : columns.values()) {
                    FilterValue value = column.read().apply(model);
                    column.validate(value);
                    values.put(column.id(), value);
                }
                snapshot.add(new Snapshot<>(id, model, Collections.unmodifiableMap(values)));
            }
            executor.execute(
                    () -> {
                        try {
                            checkCancelled(cancelled);
                            List<Snapshot<K, T>> matching = new ArrayList<>();
                            for (var row : snapshot) {
                                checkCancelled(cancelled);
                                if (predicates.stream().allMatch(p -> p.test(row)))
                                    matching.add(row);
                            }
                            long count = matching.size();
                            if (query.page() instanceof PageRequest.All
                                    && !options.allApproved()
                                    && count > options.allWarningThreshold())
                                throw new AllRowsConfirmationRequired(count);
                            matching.sort(
                                    (left, right) -> {
                                        checkCancelled(cancelled);
                                        for (var sort : query.sort()) {
                                            FilterValue a = left.values().get(sort.columnId()),
                                                    b = right.values().get(sort.columnId());
                                            if (a == null || b == null) {
                                                if (a != b) return a == null ? 1 : -1;
                                                continue;
                                            }
                                            int c = compare(a, b);
                                            if (c != 0)
                                                return sort.direction()
                                                                == SortKey.Direction.ASCENDING
                                                        ? c
                                                        : -c;
                                        }
                                        int byKey = keyOrder.compare(left.key(), right.key());
                                        if (byKey == 0
                                                && !Objects.equals(left.key(), right.key())) {
                                            throw new IllegalArgumentException(
                                                    "Row key comparator must distinguish keys");
                                        }
                                        return byKey;
                                    });
                            int from = 0, to = matching.size();
                            boolean hasNext = false;
                            if (query.page() instanceof PageRequest.Paged page) {
                                from = (int) Math.min(count, page.offset());
                                to = (int) Math.min(count, (long) from + page.pageSize());
                                hasNext = to < count;
                            }
                            List<GridRow<K, T>> rows = new ArrayList<>();
                            for (int i = from; i < to; i++) {
                                checkCancelled(cancelled);
                                var row = matching.get(i);
                                rows.add(new GridRow<>(row.key(), row.model()));
                            }
                            checkCancelled(cancelled);
                            result.complete(
                                    new PageResult<>(
                                            query.page(), rows, OptionalLong.of(count), hasNext));
                        } catch (RuntimeException failure) {
                            result.completeExceptionally(failure);
                        }
                    });
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
        }
        return new QueryExecution<>() {
            public CompletionStage<PageResult<K, T>> result() {
                return result;
            }

            public void cancel() {
                cancelled.set(true);
                result.completeExceptionally(new CancellationException("Superseded query"));
            }
        };
    }

    @Override
    public String filterExecutionId() {
        return "local";
    }

    @Override
    public void validateFilter(FilterCondition condition) {
        compile(column(condition.columnId()), condition);
    }

    /** Explicitly attached source-wide candidates; all other column conditions are respected. */
    public FilterCandidates.Provider candidateProvider(Function<FilterValue, String> label) {
        Objects.requireNonNull(label);
        return FilterCandidates.snapshot(
                Set.of(FilterCandidates.Scope.SOURCE_WIDE),
                context -> {
                    var selected = column(context.columnId());
                    var tests =
                            context.otherFilters().stream()
                                    .map(
                                            condition ->
                                                    Map.entry(
                                                            condition.columnId(),
                                                            compile(
                                                                    column(condition.columnId()),
                                                                    condition)))
                                    .toList();
                    List<Map<String, FilterValue>> snapshot = new ArrayList<>();
                    for (T model : List.copyOf(data.get())) {
                        Map<String, FilterValue> values = new HashMap<>();
                        for (var column : columns.values()) {
                            var value = column.read().apply(model);
                            column.validate(value);
                            values.put(column.id(), value);
                        }
                        snapshot.add(Collections.unmodifiableMap(values));
                    }
                    return () ->
                            snapshot.stream()
                                    .filter(
                                            values ->
                                                    tests.stream()
                                                            .allMatch(
                                                                    test ->
                                                                            test.getValue()
                                                                                    .test(
                                                                                            values
                                                                                                    .get(
                                                                                                            test
                                                                                                                    .getKey()))))
                                    .map(values -> values.get(selected.id()))
                                    .distinct()
                                    .map(
                                            value ->
                                                    new FilterCandidates.Candidate(
                                                            value, label.apply(value)))
                                    .toList();
                },
                executor);
    }

    private MemoryColumn<T> column(String id) {
        var column = columns.get(id);
        if (column == null) throw new IllegalArgumentException("Unknown column: " + id);
        return column;
    }

    private Predicate<FilterValue> compile(MemoryColumn<T> column, FilterCondition condition) {
        if (condition instanceof MissingFilter filter) return value -> filter.matches(value);
        if (condition instanceof ValueFilter filter) {
            filter.values().forEach(column::validate);
            return filter::matches;
        }
        if (condition instanceof TextFilter filter) {
            if (column.kind() != MemoryColumn.Kind.TEXT)
                throw new IllegalArgumentException("Text filter requires a text column");
            return value ->
                    filter.matches(value == null ? null : ((FilterValue.Text) value).value());
        }
        if (condition instanceof NumericFilter filter) {
            if (column.kind() != MemoryColumn.Kind.INTEGER
                    && column.kind() != MemoryColumn.Kind.DECIMAL)
                throw new IllegalArgumentException("Numeric filter requires a numeric column");
            if (column.kind() == MemoryColumn.Kind.INTEGER) {
                filter.operand().longValueExact();
                if (filter.upper() != null) filter.upper().longValueExact();
            }
            return value -> filter.matches(value == null ? null : number(value));
        }
        var compiler = column.customFilters().get(condition.typeId());
        if (compiler == null)
            throw new UnsupportedOperationException("Unsupported filter: " + condition.typeId());
        return Objects.requireNonNull(compiler.apply(condition));
    }

    private static BigDecimal number(FilterValue value) {
        return value instanceof FilterValue.IntegerValue i
                ? BigDecimal.valueOf(i.value())
                : ((FilterValue.Decimal) value).value();
    }

    private static int compare(FilterValue a, FilterValue b) {
        if (a instanceof FilterValue.Text text)
            return text.value()
                    .toLowerCase(Locale.ROOT)
                    .compareTo(((FilterValue.Text) b).value().toLowerCase(Locale.ROOT));
        if (a instanceof FilterValue.BooleanValue bool)
            return Boolean.compare(bool.value(), ((FilterValue.BooleanValue) b).value());
        return number(a).compareTo(number(b));
    }

    private static void checkCancelled(AtomicBoolean cancelled) {
        if (cancelled.get()) throw new CancellationException("Superseded query");
    }
}
