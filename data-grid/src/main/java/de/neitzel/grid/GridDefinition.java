package de.neitzel.grid;

import java.util.*;
import java.util.function.*;

/** Definition identity and layout constraints, independent of JSON and JavaFX. */
public record GridDefinition(String id, int version, List<Column> columns) {
    public record Column(
            String id,
            String type,
            double defaultWidth,
            double minimumWidth,
            double maximumWidth,
            int fixedPosition) {
        public Column {
            Checks.id(id);
            Checks.id(type);
            if (!Double.isFinite(defaultWidth)
                    || !Double.isFinite(minimumWidth)
                    || !Double.isFinite(maximumWidth)
                    || minimumWidth <= 0
                    || defaultWidth < minimumWidth
                    || maximumWidth < defaultWidth
                    || fixedPosition < -1)
                throw new IllegalArgumentException("Invalid column constraints: " + id);
        }
    }

    public GridDefinition {
        Checks.id(id);
        if (version < 1) throw new IllegalArgumentException("Invalid definition version");
        columns = List.copyOf(columns);
        if (columns.isEmpty()) throw new IllegalArgumentException("Missing columns");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < columns.size(); i++) {
            var c = columns.get(i);
            if (!ids.add(c.id())) throw new IllegalArgumentException("Duplicate definition column");
            if (c.fixedPosition() != -1 && c.fixedPosition() != i)
                throw new IllegalArgumentException("Fixed column must match declaration position");
        }
    }

    public record Prepared(GridSetup setup, List<String> adjustments) {
        public Prepared {
            Objects.requireNonNull(setup);
            adjustments = List.copyOf(adjustments);
        }
    }

    /**
     * Each migration explicitly advances exactly one definition version; output is fully validated.
     */
    public Prepared prepare(
            GridSetup input,
            Map<Integer, UnaryOperator<GridSetup>> migrations,
            SourceCapabilities source,
            Consumer<FilterCondition> validateFilter) {
        Objects.requireNonNull(input);
        Objects.requireNonNull(migrations);
        if (input.formatVersion() != 1 && input.formatVersion() != 2)
            throw new IllegalArgumentException(
                    "Unsupported setup format version: " + input.formatVersion());
        if (!id.equals(input.definitionId()))
            throw new IllegalArgumentException(
                    "Setup belongs to another grid definition: " + input.definitionId());
        if (input.definitionVersion() > version)
            throw new IllegalArgumentException("Setup uses a future definition version");
        GridSetup setup = input;
        while (setup.definitionVersion() < version) {
            int old = setup.definitionVersion();
            var migrate = migrations.get(old);
            if (migrate == null)
                throw new IllegalArgumentException(
                        "Missing definition migration from version " + old);
            setup = Objects.requireNonNull(migrate.apply(setup), "Migration returned no setup");
            if (!id.equals(setup.definitionId())
                    || setup.definitionVersion() != old + 1
                    || setup.formatVersion() != input.formatVersion())
                throw new IllegalArgumentException(
                        "Migration must preserve identity/format and advance one definition"
                                + " version");
        }
        Map<String, Column> definitions = new LinkedHashMap<>();
        columns.forEach(c -> definitions.put(c.id(), c));
        List<GridSetup.ColumnState> next = new ArrayList<>();
        List<String> adjustments = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (var saved : setup.columns()) {
            var c = definitions.get(saved.id());
            if (c == null)
                throw new IllegalArgumentException(
                        "Unknown saved column: " + saved.id() + "; provide a migration");
            if (!c.type().equals(saved.type()))
                throw new IllegalArgumentException(
                        "Column type changed: " + saved.id() + "; provide a migration");
            ColumnWidth width = saved.width();
            if (width instanceof ColumnWidth.Explicit explicit) {
                double clamped = Math.clamp(explicit.value(), c.minimumWidth(), c.maximumWidth());
                if (clamped != explicit.value())
                    adjustments.add(
                            "Column "
                                    + c.id()
                                    + " width clamped from "
                                    + explicit.value()
                                    + " to "
                                    + clamped);
                width = new ColumnWidth.Explicit(clamped);
            }
            next.add(new GridSetup.ColumnState(c.id(), c.type(), width));
            seen.add(c.id());
        }
        for (var c : columns)
            if (!seen.contains(c.id()))
                next.add(new GridSetup.ColumnState(c.id(), c.type(), new ColumnWidth.Default()));
        for (int i = 0; i < next.size(); i++) {
            int fixed = definitions.get(next.get(i).id()).fixedPosition();
            if (fixed >= 0 && fixed != i)
                throw new IllegalArgumentException(
                        "Saved order violates fixed position for "
                                + next.get(i).id()
                                + "; provide a migration");
        }
        source.validate(setup.page());
        setup.filters().forEach(validateFilter);
        return new Prepared(
                new GridSetup(
                        setup.formatVersion(),
                        id,
                        version,
                        next,
                        setup.sort(),
                        setup.filters(),
                        setup.page(),
                        setup.grouping()),
                adjustments);
    }
}
