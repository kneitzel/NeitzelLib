package de.neitzel.grid;

import java.util.*;

/** Plain versioned setup. No selection, credentials, page index, drafts, or approval tokens. */
public record GridSetup(
        int formatVersion,
        String definitionId,
        int definitionVersion,
        List<ColumnState> columns,
        List<SortKey> sort,
        List<FilterCondition> filters,
        PageRequest page,
        GroupingSetup grouping) {
    /** Source/binary-compatible constructor for version-one clients. */
    public GridSetup(
            int formatVersion,
            String definitionId,
            int definitionVersion,
            List<ColumnState> columns,
            List<SortKey> sort,
            List<FilterCondition> filters,
            PageRequest page) {
        this(formatVersion, definitionId, definitionVersion, columns, sort, filters, page, null);
    }

    public record ColumnState(String id, String type, ColumnWidth width) {
        /** Older callers must supply a definition type before persistence/restoration. */
        public ColumnState(String id, ColumnWidth width) {
            this(id, "unspecified", width);
        }

        public ColumnState {
            Checks.id(id);
            Checks.id(type);
            Objects.requireNonNull(width);
        }
    }

    public GridSetup {
        if (formatVersion <= 0 || definitionVersion <= 0)
            throw new IllegalArgumentException("Invalid version");
        Checks.id(definitionId);
        columns = List.copyOf(columns);
        sort = List.copyOf(sort);
        filters = List.copyOf(filters);
        Objects.requireNonNull(page);
        Set<String> ids = new HashSet<>();
        for (var c : columns)
            if (!ids.add(c.id())) throw new IllegalArgumentException("Duplicate column ID");
        if (sort.size() > 1)
            throw new IllegalArgumentException("Only one user-selected sort is supported");
        for (var key : sort)
            if (!ids.contains(key.columnId()))
                throw new IllegalArgumentException("Unknown sort column");
        for (var f : filters)
            if (!ids.contains(f.columnId()))
                throw new IllegalArgumentException("Unknown filter column");
        if (formatVersion == 2 && grouping == null)
            throw new IllegalArgumentException("Version two requires grouping preferences");
        if (grouping != null) {
            for (String id : grouping.columns())
                if (!ids.contains(id))
                    throw new IllegalArgumentException("Unknown grouping column");
            for (var choice : grouping.choices())
                for (var segment : choice.path().segments())
                    if (!ids.contains(segment.columnId()))
                        throw new IllegalArgumentException("Unknown group path column");
        }
        new GridQuery(filters, sort, page);
        if (page instanceof PageRequest.Paged p && p.pageIndex() != 0)
            throw new IllegalArgumentException("Setups must start on the first page");
    }
}
