package de.neitzel.fx.example.docking.workspace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import de.neitzel.fx.docking.DockAreaId;
import de.neitzel.fx.docking.DockGroupId;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.DockWindowId;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.LayoutSnapshot;
import de.neitzel.fx.docking.TabPosition;
import javafx.geometry.Rectangle2D;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Function;

/** The example's explicit JSON shape; no adapter is registered in the fx library. */
final class WorkspaceJson {
    private WorkspaceJson() { }

    static Gson create() {
        return new GsonBuilder().setPrettyPrinting().enableComplexMapKeySerialization()
                .registerTypeAdapter(DockableId.class, new Identifier<>(DockableId::value, DockableId::new))
                .registerTypeAdapter(DockAreaId.class, new Identifier<>(DockAreaId::value, DockAreaId::new))
                .registerTypeAdapter(DockGroupId.class, new Identifier<>(DockGroupId::value, DockGroupId::new))
                .registerTypeAdapter(DockWindowId.class, new Identifier<>(DockWindowId::value, DockWindowId::new))
                .registerTypeAdapter(Rectangle2D.class, new Bounds())
                .registerTypeAdapter(LayoutSnapshot.Branch.class, new Tagged<LayoutSnapshot.Branch>(Map.of(
                        "group", LayoutSnapshot.Group.class, "split", LayoutSnapshot.Split.class)))
                .registerTypeAdapter(DockTarget.class, new Tagged<DockTarget>(Map.of(
                        "area", DockTarget.Area.class, "group", DockTarget.Group.class)))
                .registerTypeAdapter(TabPosition.class, new Tagged<TabPosition>(Map.of(
                        "append", TabPosition.Append.class, "before", TabPosition.Before.class, "after", TabPosition.After.class)))
                .create();
    }

    private record Identifier<T>(Function<T, String> print, Function<String, T> parse)
            implements JsonSerializer<T>, JsonDeserializer<T> {
        @Override public JsonElement serialize(T value, Type type, JsonSerializationContext context) {
            return new JsonPrimitive(print.apply(value));
        }
        @Override public T deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
                throw new JsonParseException("An identity must be a string");
            }
            return parse.apply(json.getAsString());
        }
    }

    private record Tagged<T>(Map<String, Class<? extends T>> variants)
            implements JsonSerializer<T>, JsonDeserializer<T> {
        @Override public JsonElement serialize(T value, Type type, JsonSerializationContext context) {
            var variant = variants.entrySet().stream().filter(entry -> entry.getValue() == value.getClass())
                    .findFirst().orElseThrow(() -> new JsonParseException("Unsupported stored layout value"));
            JsonObject object = context.serialize(value, variant.getValue()).getAsJsonObject();
            object.addProperty("kind", variant.getKey());
            return object;
        }
        @Override public T deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            JsonObject object = json.getAsJsonObject();
            JsonElement kind = object.get("kind");
            Class<? extends T> variant = kind == null ? null : variants.get(kind.getAsString());
            if (variant == null) throw new JsonParseException("Unknown stored layout kind: " + kind);
            return context.deserialize(json, variant);
        }
    }

    private static final class Bounds implements JsonSerializer<Rectangle2D>, JsonDeserializer<Rectangle2D> {
        @Override public JsonElement serialize(Rectangle2D value, Type type, JsonSerializationContext context) {
            JsonObject object = new JsonObject();
            object.addProperty("x", value.getMinX());
            object.addProperty("y", value.getMinY());
            object.addProperty("width", value.getWidth());
            object.addProperty("height", value.getHeight());
            return object;
        }
        @Override public Rectangle2D deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            JsonObject object = json.getAsJsonObject();
            return new Rectangle2D(object.get("x").getAsDouble(), object.get("y").getAsDouble(),
                    object.get("width").getAsDouble(), object.get("height").getAsDouble());
        }
    }
}
