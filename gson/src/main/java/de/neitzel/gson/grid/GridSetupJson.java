package de.neitzel.gson.grid;

import com.google.gson.*;
import com.google.gson.stream.*;

import de.neitzel.grid.*;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;

/** Explicit versioned schema. Never serializes Java classes, row data, approval or credentials. */
public final class GridSetupJson {
    public interface FilterCodec {
        String typeId();

        int version();

        default Set<Integer> readableVersions() {
            return Set.of(version());
        }

        JsonObject encode(FilterCondition condition);

        FilterCondition decode(String columnId, int version, JsonObject data);
    }

    private final Map<String, FilterCodec> codecs = new LinkedHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    public GridSetupJson() {
        for (String id :
                List.of("neitzel.text", "neitzel.number", "neitzel.missing", "neitzel.values"))
            register(new BuiltIn(id));
    }

    public void register(FilterCodec codec) {
        Objects.requireNonNull(codec);
        if (codec.typeId() == null || codec.typeId().isBlank() || codec.version() < 1)
            throw new IllegalArgumentException("Invalid codec identity/version");
        if (codecs.putIfAbsent(codec.typeId(), codec) != null)
            throw new IllegalArgumentException("Duplicate filter codec: " + codec.typeId());
    }

    private FilterCodec codec(String id) {
        var codec = codecs.get(id);
        if (codec == null)
            throw new IllegalArgumentException("Register a persistence codec for filter: " + id);
        return codec;
    }

    public String encode(GridSetup setup) {
        if (setup.formatVersion() != 1 && setup.formatVersion() != 2)
            throw new IllegalArgumentException("Unsupported setup format version");
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", setup.formatVersion());
        root.addProperty("definitionId", setup.definitionId());
        root.addProperty("definitionVersion", setup.definitionVersion());
        JsonArray columns = new JsonArray();
        for (var c : setup.columns()) {
            if (c.type().equals("unspecified"))
                throw new IllegalArgumentException("Declare column type before saving: " + c.id());
            JsonObject column = new JsonObject();
            column.addProperty("id", c.id());
            column.addProperty("type", c.type());
            JsonObject width = new JsonObject();
            width.addProperty(
                    "kind", c.width() instanceof ColumnWidth.Default ? "default" : "explicit");
            if (c.width() instanceof ColumnWidth.Explicit explicit)
                width.addProperty("value", explicit.value());
            column.add("width", width);
            columns.add(column);
        }
        root.add("columns", columns);
        JsonArray sort = new JsonArray();
        for (var s : setup.sort()) {
            JsonObject item = new JsonObject();
            item.addProperty("column", s.columnId());
            item.addProperty("direction", s.direction().name());
            sort.add(item);
        }
        root.add("sort", sort);
        JsonArray filters = new JsonArray();
        for (var filter : setup.filters()) {
            var codec = codec(filter.typeId());
            JsonObject item = new JsonObject();
            item.addProperty("type", filter.typeId());
            item.addProperty("version", codec.version());
            item.addProperty("column", filter.columnId());
            item.add(
                    "data",
                    Objects.requireNonNull(codec.encode(filter), "Codec returned no payload"));
            filters.add(item);
        }
        root.add("filters", filters);
        JsonObject page = new JsonObject();
        page.addProperty("mode", setup.page() instanceof PageRequest.All ? "all" : "paged");
        if (setup.page() instanceof PageRequest.Paged p) page.addProperty("size", p.pageSize());
        root.add("page", page);
        if (setup.formatVersion() == 2) root.add("grouping", grouping(setup.grouping()));
        return gson.toJson(root);
    }

    public GridSetup decode(String json) {
        try (var reader = new JsonReader(new StringReader(Objects.requireNonNull(json)))) {
            reader.setStrictness(Strictness.STRICT);
            reader.setNestingLimit(64);
            JsonObject root = object(read(reader));
            if (reader.peek() != JsonToken.END_DOCUMENT)
                throw new IllegalArgumentException("Trailing JSON content");
            int format = integer(root, "formatVersion");
            if (format == 1)
                keys(
                        root,
                        "formatVersion",
                        "definitionId",
                        "definitionVersion",
                        "columns",
                        "sort",
                        "filters",
                        "page");
            else if (format == 2)
                keys(
                        root,
                        "formatVersion",
                        "definitionId",
                        "definitionVersion",
                        "columns",
                        "sort",
                        "filters",
                        "page",
                        "grouping");
            else throw new IllegalArgumentException("Unsupported setup format version: " + format);
            List<GridSetup.ColumnState> columns = new ArrayList<>();
            for (var entry : array(root, "columns")) {
                var c = object(entry);
                keys(c, "id", "type", "width");
                var w = object(required(c, "width"));
                ColumnWidth width;
                switch (string(w, "kind")) {
                    case "default" -> {
                        keys(w, "kind");
                        width = new ColumnWidth.Default();
                    }
                    case "explicit" -> {
                        keys(w, "kind", "value");
                        width = new ColumnWidth.Explicit(number(w, "value").doubleValue());
                    }
                    default -> throw new IllegalArgumentException("Unknown width kind");
                }
                columns.add(new GridSetup.ColumnState(string(c, "id"), string(c, "type"), width));
            }
            List<SortKey> sort = new ArrayList<>();
            for (var entry : array(root, "sort")) {
                var s = object(entry);
                keys(s, "column", "direction");
                sort.add(
                        new SortKey(
                                string(s, "column"),
                                SortKey.Direction.valueOf(string(s, "direction"))));
            }
            List<FilterCondition> filters = new ArrayList<>();
            for (var entry : array(root, "filters")) {
                var f = object(entry);
                keys(f, "type", "version", "column", "data");
                String type = string(f, "type"), column = string(f, "column");
                int version = integer(f, "version");
                var codec = codec(type);
                if (!codec.readableVersions().contains(version))
                    throw new IllegalArgumentException(
                            "Unsupported codec version " + version + " for " + type);
                var condition =
                        Objects.requireNonNull(
                                codec.decode(column, version, object(required(f, "data"))));
                if (!type.equals(condition.typeId()) || !column.equals(condition.columnId()))
                    throw new IllegalArgumentException("Codec changed filter identity");
                filters.add(condition);
            }
            var p = object(required(root, "page"));
            PageRequest page;
            switch (string(p, "mode")) {
                case "all" -> {
                    keys(p, "mode");
                    page = new PageRequest.All();
                }
                case "paged" -> {
                    keys(p, "mode", "size");
                    page = new PageRequest.Paged(0, integer(p, "size"));
                }
                default -> throw new IllegalArgumentException("Unknown page mode");
            }
            return new GridSetup(
                    format,
                    string(root, "definitionId"),
                    integer(root, "definitionVersion"),
                    columns,
                    sort,
                    filters,
                    page,
                    format == 2 ? grouping(object(required(root, "grouping"))) : null);
        } catch (IOException | IllegalStateException | ArithmeticException error) {
            throw new IllegalArgumentException("Invalid saved setup: " + error.getMessage(), error);
        }
    }

    private static JsonObject grouping(GroupingSetup grouping) {
        var object = new JsonObject();
        var columns = new JsonArray();
        grouping.columns().forEach(columns::add);
        object.add("columns", columns);
        var choices = new JsonArray();
        for (var choice : grouping.choices()) {
            var item = new JsonObject();
            item.addProperty("expanded", choice.expanded());
            var path = new JsonArray();
            for (var segment : choice.path().segments()) {
                var value = new JsonObject();
                value.addProperty("column", segment.columnId());
                value.add(
                        "value",
                        segment.value() == null
                                ? JsonNull.INSTANCE
                                : BuiltIn.value(segment.value()));
                path.add(value);
            }
            item.add("path", path);
            choices.add(item);
        }
        object.add("choices", choices);
        return object;
    }

    private static GroupingSetup grouping(JsonObject object) {
        keys(object, "columns", "choices");
        List<String> columns = new ArrayList<>();
        for (var column : array(object, "columns")) {
            if (!column.isJsonPrimitive() || !column.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("Grouping column must be text");
            columns.add(column.getAsString());
        }
        List<GroupingSetup.Choice> choices = new ArrayList<>();
        for (var entry : array(object, "choices")) {
            var item = object(entry);
            keys(item, "expanded", "path");
            List<GroupPath.Segment> path = new ArrayList<>();
            for (var part : array(item, "path")) {
                var segment = object(part);
                keys(segment, "column", "value");
                path.add(
                        new GroupPath.Segment(
                                string(segment, "column"),
                                segment.get("value").isJsonNull()
                                        ? null
                                        : BuiltIn.value(object(segment.get("value")))));
            }
            choices.add(new GroupingSetup.Choice(new GroupPath(path), bool(item, "expanded")));
        }
        return new GroupingSetup(columns, choices);
    }

    static JsonElement read(JsonReader reader) throws IOException {
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                var object = new JsonObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name))
                        throw new IllegalArgumentException("Duplicate JSON property: " + name);
                    object.add(name, read(reader));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                var array = new JsonArray();
                while (reader.hasNext()) array.add(read(reader));
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default ->
                    throw new IllegalArgumentException("Unexpected JSON token: " + reader.peek());
        };
    }

    static void keys(JsonObject o, String... names) {
        if (!o.keySet().equals(Set.of(names)))
            throw new IllegalArgumentException(
                    "Expected properties " + Arrays.toString(names) + ", found " + o.keySet());
    }

    private static JsonElement required(JsonObject o, String name) {
        var v = o.get(name);
        if (v == null || v.isJsonNull())
            throw new IllegalArgumentException("Missing property: " + name);
        return v;
    }

    static JsonObject object(JsonElement v) {
        if (!v.isJsonObject()) throw new IllegalArgumentException("Expected JSON object");
        return v.getAsJsonObject();
    }

    private static JsonArray array(JsonObject o, String name) {
        var v = required(o, name);
        if (!v.isJsonArray()) throw new IllegalArgumentException("Expected array: " + name);
        return v.getAsJsonArray();
    }

    static String string(JsonObject o, String name) {
        var v = required(o, name);
        if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected string: " + name);
        return v.getAsString();
    }

    private static BigDecimal number(JsonObject o, String name) {
        var v = required(o, name);
        if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Expected number: " + name);
        return v.getAsBigDecimal();
    }

    private static int integer(JsonObject o, String name) {
        return number(o, name).intValueExact();
    }

    private static boolean bool(JsonObject o, String name) {
        var v = required(o, name);
        if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException("Expected boolean: " + name);
        return v.getAsBoolean();
    }

    private record BuiltIn(String typeId) implements FilterCodec {
        public int version() {
            return 1;
        }

        public JsonObject encode(FilterCondition condition) {
            JsonObject data = new JsonObject();
            switch (condition) {
                case TextFilter f -> {
                    data.addProperty("operator", f.operator().name());
                    data.addProperty("operand", f.operand());
                    data.addProperty("caseSensitive", f.caseSensitive());
                }
                case NumericFilter f -> {
                    data.addProperty("operator", f.operator().name());
                    data.addProperty("operand", f.operand().toString());
                    if (f.upper() != null) data.addProperty("upper", f.upper().toString());
                }
                case MissingFilter f -> data.addProperty("missing", f.missing());
                case ValueFilter f -> {
                    data.addProperty("includeMissing", f.includeMissing());
                    var values = new JsonArray();
                    for (var v : f.values()) values.add(value(v));
                    data.add("values", values);
                }
                default ->
                        throw new IllegalArgumentException(
                                "Wrong built-in condition class: " + condition.typeId());
            }
            return data;
        }

        public FilterCondition decode(String column, int version, JsonObject d) {
            return switch (typeId) {
                case "neitzel.text" -> {
                    keys(d, "operator", "operand", "caseSensitive");
                    yield new TextFilter(
                            column,
                            TextFilter.Operator.valueOf(string(d, "operator")),
                            string(d, "operand"),
                            bool(d, "caseSensitive"));
                }
                case "neitzel.number" -> {
                    var op = NumericFilter.Operator.valueOf(string(d, "operator"));
                    if (op == NumericFilter.Operator.RANGE) keys(d, "operator", "operand", "upper");
                    else keys(d, "operator", "operand");
                    yield new NumericFilter(
                            column,
                            op,
                            new BigDecimal(string(d, "operand")),
                            op == NumericFilter.Operator.RANGE
                                    ? new BigDecimal(string(d, "upper"))
                                    : null);
                }
                case "neitzel.missing" -> {
                    keys(d, "missing");
                    yield new MissingFilter(column, bool(d, "missing"));
                }
                case "neitzel.values" -> {
                    keys(d, "includeMissing", "values");
                    Set<FilterValue> values = new LinkedHashSet<>();
                    for (var v : array(d, "values"))
                        if (!values.add(value(object(v))))
                            throw new IllegalArgumentException("Duplicate checklist value");
                    yield new ValueFilter(column, values, bool(d, "includeMissing"));
                }
                default -> throw new IllegalArgumentException("Unknown built-in codec");
            };
        }

        private static JsonObject value(FilterValue value) {
            var o = new JsonObject();
            switch (value) {
                case FilterValue.Text v -> {
                    o.addProperty("type", "text");
                    o.addProperty("value", v.value());
                }
                case FilterValue.IntegerValue v -> {
                    o.addProperty("type", "integer");
                    o.addProperty("value", Long.toString(v.value()));
                }
                case FilterValue.Decimal v -> {
                    o.addProperty("type", "decimal");
                    o.addProperty("value", v.value().toString());
                }
                case FilterValue.BooleanValue v -> {
                    o.addProperty("type", "boolean");
                    o.addProperty("value", v.value());
                }
            }
            return o;
        }

        private static FilterValue value(JsonObject o) {
            keys(o, "type", "value");
            return switch (string(o, "type")) {
                case "text" -> new FilterValue.Text(string(o, "value"));
                case "integer" -> new FilterValue.IntegerValue(Long.parseLong(string(o, "value")));
                case "decimal" -> new FilterValue.Decimal(new BigDecimal(string(o, "value")));
                case "boolean" -> new FilterValue.BooleanValue(bool(o, "value"));
                default -> throw new IllegalArgumentException("Unknown checklist scalar type");
            };
        }
    }
}
