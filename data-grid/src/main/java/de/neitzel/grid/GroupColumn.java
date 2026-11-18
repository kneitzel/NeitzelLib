package de.neitzel.grid;

import java.util.*;
import java.util.function.Function;

/** Explicit owner-thread mapping. Labels never determine identity or ordering. */
public record GroupColumn<T>(
        String id,
        MemoryColumn.Kind kind,
        Function<T, FilterValue> read,
        Function<FilterValue, String> label) {
    public GroupColumn {
        Checks.id(id);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(read);
        Objects.requireNonNull(label);
    }

    public static <T> GroupColumn<T> text(String id, Function<T, String> reader) {
        return new GroupColumn<>(
                id,
                MemoryColumn.Kind.TEXT,
                row -> {
                    String value = reader.apply(row);
                    return value == null ? null : new FilterValue.Text(value);
                },
                GroupColumn::display);
    }

    public static <T> GroupColumn<T> integer(String id, Function<T, Long> reader) {
        return new GroupColumn<>(
                id,
                MemoryColumn.Kind.INTEGER,
                row -> {
                    Long value = reader.apply(row);
                    return value == null ? null : new FilterValue.IntegerValue(value);
                },
                GroupColumn::display);
    }

    public static <T> GroupColumn<T> decimal(String id, Function<T, java.math.BigDecimal> reader) {
        return new GroupColumn<>(
                id,
                MemoryColumn.Kind.DECIMAL,
                row -> {
                    var value = reader.apply(row);
                    return value == null ? null : new FilterValue.Decimal(value);
                },
                GroupColumn::display);
    }

    public static <T> GroupColumn<T> bool(String id, Function<T, Boolean> reader) {
        return new GroupColumn<>(
                id,
                MemoryColumn.Kind.BOOLEAN,
                row -> {
                    Boolean value = reader.apply(row);
                    return value == null ? null : new FilterValue.BooleanValue(value);
                },
                GroupColumn::display);
    }

    public void validate(FilterValue value) {
        if (value == null) return;
        boolean valid =
                switch (kind) {
                    case TEXT -> value instanceof FilterValue.Text;
                    case INTEGER -> value instanceof FilterValue.IntegerValue;
                    case DECIMAL -> value instanceof FilterValue.Decimal;
                    case BOOLEAN -> value instanceof FilterValue.BooleanValue;
                };
        if (!valid) throw new IllegalArgumentException("Grouping value type changed: " + id);
    }

    public static String display(FilterValue value) {
        return switch (value) {
            case null -> "(Missing)";
            case FilterValue.Text t -> t.value().isEmpty() ? "(Empty)" : t.value();
            case FilterValue.IntegerValue i -> Long.toString(i.value());
            case FilterValue.Decimal d -> d.value().toPlainString();
            case FilterValue.BooleanValue b -> Boolean.toString(b.value());
        };
    }

    public static int compare(FilterValue a, FilterValue b) {
        if (a == null) return b == null ? 0 : 1;
        if (b == null) return -1;
        return switch (a) {
            case FilterValue.Text t -> {
                String right = ((FilterValue.Text) b).value();
                int order =
                        t.value()
                                .toLowerCase(Locale.ROOT)
                                .compareTo(right.toLowerCase(Locale.ROOT));
                yield order == 0 ? t.value().compareTo(right) : order;
            }
            case FilterValue.IntegerValue i ->
                    Long.compare(i.value(), ((FilterValue.IntegerValue) b).value());
            case FilterValue.Decimal d -> d.value().compareTo(((FilterValue.Decimal) b).value());
            case FilterValue.BooleanValue v ->
                    Boolean.compare(v.value(), ((FilterValue.BooleanValue) b).value());
        };
    }
}
