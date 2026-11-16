package de.neitzel.gson.grid;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import de.neitzel.grid.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

class GridSetupJsonTest {
    static GridSetup setup(FilterCondition condition, PageRequest page) {
        return new GridSetup(
                1,
                "catalog",
                2,
                List.of(new GridSetup.ColumnState("x", "decimal", new ColumnWidth.Default())),
                List.of(new SortKey("x", SortKey.Direction.DESCENDING)),
                List.of(condition),
                page);
    }

    @Test
    void roundTripsEveryBuiltInAndExactTypedChecklistWithoutRuntimeState() {
        var json = new GridSetupJson();
        for (var condition :
                List.of(
                        new TextFilter("x", TextFilter.Operator.EQUALS, " %_ ", false),
                        new NumericFilter(
                                "x",
                                NumericFilter.Operator.RANGE,
                                new BigDecimal("1.23000000000000000000000001"),
                                new BigDecimal("999999999999999999.99")),
                        new MissingFilter("x", true),
                        new ValueFilter(
                                "x",
                                Set.of(
                                        new FilterValue.IntegerValue(Long.MAX_VALUE),
                                        new FilterValue.Text(""),
                                        new FilterValue.Decimal(new BigDecimal("1.20")),
                                        new FilterValue.BooleanValue(false)),
                                true))) {
            for (var page : List.of(new PageRequest.All(), new PageRequest.Paged(0, 50))) {
                var setup = setup(condition, page);
                String encoded = json.encode(setup);
                assertEquals(setup, json.decode(encoded));
                assertFalse(encoded.contains("pageIndex"));
                assertFalse(encoded.contains("approval"));
                assertFalse(encoded.contains("selectedKey"));
            }
        }
    }

    @Test
    void rejectsMalformedFutureDuplicateAndUnregisteredData() {
        var json = new GridSetupJson();
        String valid =
                json.encode(setup(new MissingFilter("x", false), new PageRequest.Paged(0, 50)));
        for (String invalid :
                List.of(
                        valid.replace("\"formatVersion\": 1", "\"formatVersion\": 2"),
                        valid.replace("\"formatVersion\": 1", "\"formatVersion\": 1.5"),
                        valid.replace("\"missing\": false", "\"missing\": \"false\""),
                        valid.replace("\"size\": 50", "\"size\": 50, \"pageIndex\": 5"),
                        valid.replace("\"version\": 1", "\"version\": 9"),
                        valid.replace("neitzel.missing", "missing.registration"),
                        "{\"formatVersion\":1,\"formatVersion\":1}",
                        valid + "{}"))
            assertThrows(IllegalArgumentException.class, () -> json.decode(invalid), invalid);
        assertThrows(
                IllegalArgumentException.class,
                () -> json.encode(setup(new Custom("x", "secret"), new PageRequest.All())));
    }

    record Custom(String columnId, String value) implements FilterCondition {
        public String typeId() {
            return "app.custom";
        }
    }

    @Test
    void customCodecsAreExplicitVersionedAndIdentityChecked() {
        var json = new GridSetupJson();
        json.register(
                new GridSetupJson.FilterCodec() {
                    public String typeId() {
                        return "app.custom";
                    }

                    public int version() {
                        return 3;
                    }

                    public JsonObject encode(FilterCondition c) {
                        var d = new JsonObject();
                        d.addProperty("value", ((Custom) c).value());
                        return d;
                    }

                    public FilterCondition decode(String column, int version, JsonObject d) {
                        return new Custom(column, d.get("value").getAsString());
                    }
                });
        var saved = setup(new Custom("x", "hello"), new PageRequest.All());
        assertEquals(saved, json.decode(json.encode(saved)));
        assertThrows(
                IllegalArgumentException.class,
                () -> json.decode(json.encode(saved).replace("\"version\": 3", "\"version\": 4")));
    }

    @Test
    void widthIntentSurvivesAndNonFiniteNegativeOrUnknownWidthFails() {
        var json = new GridSetupJson();
        var s = setup(new MissingFilter("x", false), new PageRequest.Paged(0, 50));
        var explicit =
                new GridSetup(
                        1,
                        s.definitionId(),
                        s.definitionVersion(),
                        List.of(
                                new GridSetup.ColumnState(
                                        "x", "decimal", new ColumnWidth.Explicit(175.5))),
                        s.sort(),
                        s.filters(),
                        s.page());
        String encoded = json.encode(explicit);
        assertEquals(explicit, json.decode(encoded));
        for (String invalid :
                List.of(
                        encoded.replace("175.5", "-1"),
                        encoded.replace("175.5", "1e999"),
                        encoded.replace("explicit", "automatic")))
            assertThrows(IllegalArgumentException.class, () -> json.decode(invalid));
    }
}
