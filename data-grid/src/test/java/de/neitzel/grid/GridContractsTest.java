package de.neitzel.grid;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

class GridContractsTest {
    @Test
    void textIsLiteralAndMissingIsNotEmpty() {
        assertTrue(
                new TextFilter("name", TextFilter.Operator.CONTAINS, "%_", false).matches("a%_B"));
        assertFalse(new TextFilter("name", TextFilter.Operator.EQUALS, "", false).matches(null));
        assertTrue(new TextFilter("name", TextFilter.Operator.EQUALS, "", false).matches(""));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TextFilter("name", TextFilter.Operator.CONTAINS, "", false));
        assertFalse(new TextFilter("name", TextFilter.Operator.EQUALS, "ss", false).matches("ß"));
    }

    @Test
    void decimalsAreExactAndChecklistsKeepExactText() {
        assertTrue(
                new NumericFilter(
                                "price",
                                NumericFilter.Operator.EQUALS,
                                new BigDecimal("9007199254740993.00"),
                                null)
                        .matches(new BigDecimal("9007199254740993")));
        assertFalse(
                new NumericFilter(
                                "price",
                                NumericFilter.Operator.EQUALS,
                                new BigDecimal("9007199254740993"),
                                null)
                        .matches(new BigDecimal("9007199254740992")));
        var values =
                new ValueFilter(
                        "price", Set.of(new FilterValue.Decimal(new BigDecimal("1.0"))), false);
        assertTrue(values.matches(new FilterValue.Decimal(new BigDecimal("1.00"))));
        assertFalse(
                new ValueFilter("name", Set.of(new FilterValue.Text("Alpha")), false)
                        .matches(new FilterValue.Text("alpha")));
        assertFalse(new ValueFilter("name", Set.of(), false).matches(null));
        assertTrue(new MissingFilter("name", false).matches(""));
    }

    @Test
    void queryAndSetupAreDefensivelyCopiedAndRejectAmbiguity() {
        var filters = new ArrayList<FilterCondition>();
        filters.add(new MissingFilter("name", true));
        var q = new GridQuery(filters, List.of(), new PageRequest.All());
        filters.clear();
        assertEquals(1, q.filters().size());
        assertThrows(UnsupportedOperationException.class, () -> q.filters().clear());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GridQuery(
                                List.of(
                                        new MissingFilter("a", true),
                                        new MissingFilter("a", false)),
                                List.of(),
                                new PageRequest.All()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GridSetup(
                                1,
                                "products",
                                1,
                                List.of(new GridSetup.ColumnState("a", new ColumnWidth.Default())),
                                List.of(),
                                List.of(),
                                new PageRequest.Paged(1, 50)));
        assertThrows(IllegalArgumentException.class, () -> new ColumnWidth.Explicit(Double.NaN));
    }

    @Test
    void resultsValidateCountsButAllowDisappearedPages() {
        var row = new GridRow<>(1, "one");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PageResult<>(
                                new PageRequest.Paged(0, 2),
                                List.of(row, row),
                                OptionalLong.of(2),
                                false));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PageResult<>(
                                new PageRequest.Paged(0, 2),
                                List.of(row),
                                OptionalLong.of(3),
                                true));
        assertDoesNotThrow(
                () ->
                        new PageResult<>(
                                new PageRequest.Paged(4, 2), List.of(), OptionalLong.of(2), false));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PageResult<>(
                                new PageRequest.All(), List.of(row), OptionalLong.of(2), false));
        assertThrows(ArithmeticException.class, () -> new PageRequest.Paged(Long.MAX_VALUE, 250));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceCapabilities(true, false, 100).validate(new PageRequest.All()));
    }
}
