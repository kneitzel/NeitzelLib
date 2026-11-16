package de.neitzel.grid;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class GridDefinitionTest {
    static GridDefinition.Column column(String id, String type, int fixed) {
        return new GridDefinition.Column(id, type, 100, 50, 200, fixed);
    }

    static GridSetup saved(String type) {
        return new GridSetup(
                1,
                "products",
                1,
                List.of(new GridSetup.ColumnState("a", type, new ColumnWidth.Explicit(300))),
                List.of(),
                List.of(),
                new PageRequest.Paged(0, 25));
    }

    @Test
    void addsNewColumnsClampsWidthsAndPreservesDefaultIntent() {
        var d =
                new GridDefinition(
                        "products",
                        1,
                        List.of(column("a", "text", -1), column("b", "integer", -1)));
        var prepared =
                d.prepare(saved("text"), Map.of(), new SourceCapabilities(true, true, 50), f -> {});
        assertEquals(
                List.of(
                        new GridSetup.ColumnState("a", "text", new ColumnWidth.Explicit(200)),
                        new GridSetup.ColumnState("b", "integer", new ColumnWidth.Default())),
                prepared.setup().columns());
        assertEquals(1, prepared.adjustments().size());
    }

    @Test
    void identityTypesUnknownColumnsAndFixedPositionsRequireExplicitMigration() {
        var d =
                new GridDefinition(
                        "products", 1, List.of(column("b", "text", 0), column("a", "text", -1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        d.prepare(
                                saved("text"),
                                Map.of(),
                                new SourceCapabilities(true, true, 50),
                                f -> {}));
        var changed = new GridDefinition("products", 1, List.of(column("a", "integer", -1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        changed.prepare(
                                saved("text"),
                                Map.of(),
                                new SourceCapabilities(true, true, 50),
                                f -> {}));
        var removed = new GridDefinition("products", 1, List.of(column("b", "text", -1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        removed.prepare(
                                saved("text"),
                                Map.of(),
                                new SourceCapabilities(true, true, 50),
                                f -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GridDefinition("other", 1, List.of(column("a", "text", -1)))
                                .prepare(
                                        saved("text"),
                                        Map.of(),
                                        new SourceCapabilities(true, true, 50),
                                        f -> {}));
    }

    @Test
    void migrationsMustAdvanceAndRemainSubjectToFullValidation() {
        var d = new GridDefinition("products", 2, List.of(column("a", "integer", -1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        d.prepare(
                                saved("text"),
                                Map.of(),
                                new SourceCapabilities(true, true, 50),
                                f -> {}));
        var result =
                d.prepare(
                        saved("text"),
                        Map.of(
                                1,
                                s ->
                                        new GridSetup(
                                                1,
                                                s.definitionId(),
                                                2,
                                                List.of(
                                                        new GridSetup.ColumnState(
                                                                "a",
                                                                "integer",
                                                                new ColumnWidth.Default())),
                                                s.sort(),
                                                s.filters(),
                                                s.page())),
                        new SourceCapabilities(true, true, 50),
                        f -> {});
        assertEquals(2, result.setup().definitionVersion());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        d.prepare(
                                saved("text"),
                                Map.of(1, s -> s),
                                new SourceCapabilities(true, true, 50),
                                f -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        d.prepare(
                                saved("text"),
                                Map.of(
                                        1,
                                        s ->
                                                new GridSetup(
                                                        1,
                                                        s.definitionId(),
                                                        2,
                                                        s.columns(),
                                                        s.sort(),
                                                        s.filters(),
                                                        s.page())),
                                new SourceCapabilities(true, true, 50),
                                f -> {}));
    }

    @Test
    void actualFilterAndPageCapabilitiesAreCheckedBeforeExecution() {
        var d = new GridDefinition("products", 1, List.of(column("a", "text", -1)));
        var s = saved("text");
        var filtered =
                new GridSetup(
                        1,
                        "products",
                        1,
                        s.columns(),
                        List.of(),
                        List.of(new MissingFilter("a", true)),
                        new PageRequest.All());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        d.prepare(
                                filtered,
                                Map.of(),
                                new SourceCapabilities(true, false, 50),
                                f -> {}));
        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        d.prepare(
                                filtered,
                                Map.of(),
                                new SourceCapabilities(true, true, 50),
                                f -> {
                                    throw new UnsupportedOperationException("Missing unsupported");
                                }));
    }
}
