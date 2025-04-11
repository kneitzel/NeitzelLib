package de.neitzel.gson.adapter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * A custom {@link TypeAdapter} implementation for selectively excluding specified fields during JSON serialization.
 *
 * This adapter facilitates both serialization and deserialization of objects while providing functionality to remove specified
 * fields from the serialized output. The set of excluded field names is provided during the adapter's initialization.
 *
 * @param <T> the type of objects that this adapter can serialize and deserialize
 */
public class ExcludeFieldsAdapter<T> extends TypeAdapter<T> {

  /**
   * The type of objects that this adapter handles for serialization and deserialization.
   *
   * This is a generic class type parameter representing the class of objects that the
   * {@link ExcludeFieldsAdapter} instance is designed to process. It ensures type
   * safety and guarantees the compatibility of the adapter with a specific object type.
   */
  private final Class<T> type;
  /**
   * A set containing the names of fields to be excluded during JSON serialization.
   *
   * This collection is used in the {@link ExcludeFieldsAdapter} to determine which fields should be removed from the
   * serialized JSON representation of an object. The fields to be excluded are specified during the initialization
   * of the adapter.
   */
  private final Set<String> excludedFields;

  /**
   * An instance of the Gson library used for JSON serialization and deserialization.
   *
   * This variable holds the Gson object that is utilized to handle the conversion processes within the
   * custom serialization and deserialization logic. If not explicitly provided during initialization,
   * it can be lazily initialized using a GsonBuilder.
   */
  private Gson gson;

  /**
   * A {@link GsonBuilder} instance used for configuring and creating {@link Gson} objects within the adapter.
   *
   * This variable serves as a reference to a GsonBuilder that allows customization of the Gson instance,
   * including registering custom type adapters, serializers, and deserializers, as well as adjusting serialization policies.
   *
   * The {@link #gsonBuilder} is primarily utilized when an existing {@link Gson} instance is not directly provided.
   * It ensures that the adapter can defer the creation of a Gson object until it is explicitly required.
   */
  private GsonBuilder gsonBuilder;

  /**
   * Creates a new instance of {@code ExcludeFieldsAdapter} for managing the serialization and deserialization
   * of objects of the specified {@code type}, while excluding certain fields during serialization.
   *
   * @param type           the class type of the objects to be serialized and deserialized
   * @param excludedFields the set of field names to be excluded during serialization
   * @param gson           the Gson instance to be used for serialization and deserialization
   */
  public ExcludeFieldsAdapter(Class<T> type, Set<String> excludedFields, Gson gson) {
    this.type = type;
    this.excludedFields = new HashSet<>(excludedFields);
    this.gsonBuilder = null;
    this.gson = gson;
  }

  /**
   * Constructs an instance of {@code ExcludeFieldsAdapter} that selectively excludes specified fields from
   * the JSON output based on the configuration provided during initialization.
   *
   * @param type the class of the type to be serialized and deserialized
   * @param excludedFields a set of field names to be excluded from the serialized JSON output
   * @param gsonBuilder an instance of {@code GsonBuilder} used to create a custom {@code Gson} instance if needed
   */
  public ExcludeFieldsAdapter(Class<T> type, Set<String> excludedFields, GsonBuilder gsonBuilder) {
    this.type = type;
    this.excludedFields = new HashSet<>(excludedFields);
    this.gsonBuilder = gsonBuilder;
    this.gson = null;
  }

  /**
   * Lazily initializes and retrieves the {@link Gson} instance. If the instance is null, it creates a new {@link Gson}
   * object using the {@link GsonBuilder} provided during the adapter's initialization.
   *
   * @return the {@link Gson} instance used for serialization and deserialization
   */
  private Gson getGson() {
    if (gson == null) {
      gson = gsonBuilder.create();
    }
    return gson;
  }

  /**
   * Serializes an object of type {@code T} into JSON format, excluding certain fields specified during the initialization of this adapter.
   * The object is first converted into a {@link JsonObject}, then the fields listed in the excluded fields set are removed from the JSON
   * representation before writing it to the provided {@link JsonWriter}.
   *
   * @param out   the {@link JsonWriter} to write the serialized JSON data to
   * @param value the object of type {@code T} to serialize into JSON
   * @throws IOException if an I/O error occurs during writing
   */
  @Override
  public void write(JsonWriter out, T value) throws IOException {
    JsonObject obj = getGson().toJsonTree(value).getAsJsonObject();

    for (String field : excludedFields) {
      obj.remove(field);
    }

    Streams.write(obj, out);
  }

  /**
   * Reads a JSON input stream and deserializes it into an object of the specified type.
   *
   * @param in the {@link JsonReader} to read the JSON input from
   * @return an instance of type {@code T} deserialized from the JSON input
   * @throws IOException if an error occurs during reading or deserialization
   */
  @Override
  public T read(JsonReader in) throws IOException {
    return getGson().fromJson(in, type);
  }
}
