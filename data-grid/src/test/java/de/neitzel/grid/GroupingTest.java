package de.neitzel.grid;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class GroupingTest {
    record Item(String type, String status) {}

    static GroupPath path(String value) {
        return new GroupPath(List.of(new GroupPath.Segment("type", new FilterValue.Text(value))));
    }

    @Test
    void snapshotSeparatesExactIdentityAndLabelsFromOrderingAndRetainsLeafSourceOrder() {
        AtomicInteger calls = new AtomicInteger();
        var mapping =
                new GroupColumn<Item>(
                        "type",
                        MemoryColumn.Kind.TEXT,
                        i -> {
                            calls.incrementAndGet();
                            return i.type() == null ? null : new FilterValue.Text(i.type());
                        },
                        GroupColumn::display);
        var rows =
                List.of(
                        new GridRow<>(1, new Item("a", "one")),
                        new GridRow<>(2, new Item(null, "two")),
                        new GridRow<>(3, new Item("A", "three")),
                        new GridRow<>(4, new Item("a", "four")),
                        new GridRow<>(5, new Item("", "five")));
        var snapshot = GroupProjection.capture(rows, List.of(mapping));
        var groups = GroupProjection.project(snapshot, List.of("type"));
        assertEquals(
                List.of("(Empty)", "A", "a", "(Missing)"),
                groups.stream().map(GroupProjection.Node::label).toList());
        assertEquals(List.of(1, 4), groups.get(2).rows().stream().map(GridRow::key).toList());
        assertEquals(5, calls.get());
        GroupProjection.project(snapshot, List.of("type"));
        assertEquals(5, calls.get(), "Regroup must use captured scalars, not reread models");
        assertEquals(List.of(1, 2, 3, 4, 5), rows.stream().map(GridRow::key).toList());
    }

    @Test
    void decimalScaleDoesNotCreateDifferentGroupsAndWrongTypesFailBeforePublication() {
        var mapping =
                new GroupColumn<BigDecimal>(
                        "amount",
                        MemoryColumn.Kind.DECIMAL,
                        FilterValue.Decimal::new,
                        GroupColumn::display);
        var groups =
                GroupProjection.project(
                        GroupProjection.capture(
                                List.of(
                                        new GridRow<>(1, new BigDecimal("1.0")),
                                        new GridRow<>(2, new BigDecimal("1.00"))),
                                List.of(mapping)),
                        List.of("amount"));
        assertEquals(1, groups.size());
        assertEquals(2, groups.getFirst().count());
        var invalid =
                new GroupColumn<String>(
                        "type",
                        MemoryColumn.Kind.TEXT,
                        s -> new FilterValue.BooleanValue(true),
                        GroupColumn::display);
        assertThrows(
                IllegalArgumentException.class,
                () -> GroupProjection.capture(List.of(new GridRow<>(1, "x")), List.of(invalid)));
    }

    @Test
    void expansionPreservesCurrentChoicesAndBoundsOnlyInactiveHistory() {
        var expansion = new GroupExpansion(true, 1);
        expansion.current(List.of(path("A"), path("B")));
        expansion.set(path("A"), true);
        expansion.set(path("B"), false);
        expansion.set(path("C"), true);
        expansion.set(path("D"), true);
        assertEquals(3, expansion.snapshot().size());
        assertTrue(expansion.expanded(path("A")));
        assertFalse(expansion.expanded(path("C")));
        expansion.configure(false, 1);
        assertFalse(
                expansion.expanded(path("B")),
                "Explicit choice equal to old default must survive default change");
        expansion.reset();
        assertTrue(expansion.expanded(path("B")));
    }

    @Test
    void nestedCountsAndPathsDoNotConflateEqualLabelsOrParentCollapse() {
        var cols =
                List.of(
                        new GroupColumn<Item>(
                                "type",
                                MemoryColumn.Kind.TEXT,
                                i -> new FilterValue.Text(i.type()),
                                v -> "same"),
                        new GroupColumn<Item>(
                                "status",
                                MemoryColumn.Kind.TEXT,
                                i -> new FilterValue.Text(i.status()),
                                GroupColumn::display));
        var tree =
                GroupProjection.project(
                        GroupProjection.capture(
                                List.of(
                                        new GridRow<>(1, new Item("A", "live")),
                                        new GridRow<>(2, new Item("B", "live"))),
                                cols),
                        List.of("type", "status"));
        assertEquals(2, tree.size());
        assertNotEquals(tree.get(0).path(), tree.get(1).path());
        var expansion = new GroupExpansion(true, 1000);
        expansion.current(GroupProjection.paths(tree));
        var parent = tree.get(0).path();
        var child = tree.get(0).children().getFirst().path();
        expansion.set(child, true);
        expansion.set(parent, false);
        expansion.set(parent, true);
        assertTrue(expansion.expanded(child));
    }
}
