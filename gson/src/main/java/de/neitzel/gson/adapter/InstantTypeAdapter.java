package de.neitzel.gson.adapter;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * A custom Gson type adapter for serializing and deserializing {@link Instant} objects. This adapter handles conversion between
 * {@code Instant} and its ISO-8601 formatted string representation.
 * <p>
 * Implementation details: - Serialization: Converts {@link Instant} to its ISO-8601 string representation. - Deserialization: Parses a
 * valid ISO-8601 string to an {@link Instant} instance.
 * <p>
 * The adapter ensures compliance with the ISO-8601 standard for interoperability. A {@link JsonParseException} is thrown if the provided
 * JSON element during deserialization does not conform to the supported {@code Instant} format.
 */
public class InstantTypeAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {

  /**
   * Serializes an {@link Instant} object into its ISO-8601 string representation as a {@link JsonElement}.
   *
   * @param src       the {@link Instant} object to be serialized
   * @param typeOfSrc the specific genericized type of {@code src} (typically ignored in this implementation)
   * @param context   the context of the serialization process to provide custom serialization logic
   * @return a {@link JsonPrimitive} containing the ISO-8601 formatted string representation of the {@link Instant}
   */
  @Override
  public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
    return new JsonPrimitive(src.toString()); // ISO-8601: "2025-04-02T10:15:30Z"
  }

  /**
   * Deserializes a JSON element into an {@link Instant} object.
   *
   * @param json    the JSON data being deserialized
   * @param typeOfT the specific genericized type of the object to deserialize
   * @param context the deserialization context
   * @return the deserialized {@link Instant} object
   * @throws JsonParseException if the JSON element does not represent a valid ISO-8601 instant format
   */
  @Override
  public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    try {
      return Instant.parse(json.getAsString());
    } catch (DateTimeParseException e) {
      throw new JsonParseException("Invalid Instant format", e);
    }
  }
}
