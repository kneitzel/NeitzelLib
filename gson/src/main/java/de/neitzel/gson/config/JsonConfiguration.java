package de.neitzel.gson.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.Builder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a configuration utility class that manages application settings and allows
 * loading and saving the configuration in JSON format. This class is built using Gson for
 * JSON serialization and deserialization.
 * <p>
 * The class maintains application settings in memory and provides methods to store
 * the configuration on the filesystem and retrieve it as needed.
 */
@Builder(builderClassName = "ConfigurationBuilder")
public class JsonConfiguration {
    /**
     * The name of the application.
     * This variable is used to identify the application and is incorporated into various configurations,
     * such as the naming of directories or files associated with the application's settings.
     * It is finalized and set during the construction of the {@code JsonConfiguration} instance and cannot be changed afterward.
     */
    private final String appName;

    /**
     * The filesystem path representing the home directory utilized by the application.
     * This directory serves as the location where application-specific configuration files
     * and data are stored. The path is initialized when the {@code JsonConfiguration} instance
     * is constructed, based on the settings provided through the {@code JsonConfigurationBuilder}.
     * It can be customized to a user-specified value or defaults to the user's home directory.
     * <p>
     * The {@code homeDir} variable is integral in determining the location of the main configuration
     * file and other related resources used by the application.
     */
    private final String homeDir;

    /**
     * A map that stores configuration settings as key-value pairs,
     * where both the keys and values are represented as strings.
     * <p>
     * This map is used to manage dynamic or runtime settings for the configuration,
     * allowing for flexible assignment and retrieval of values associated with specific
     * configuration keys. It is initialized as a final instance, ensuring it cannot be
     * reassigned after creation.
     */
    private final Map<String, String> settings;

    /**
     * A Gson instance used for handling JSON serialization and deserialization within
     * the JsonConfiguration class. This instance is immutable and customized with
     * registered adapters during the initialization of the JsonConfiguration.
     * <p>
     * The Gson object provides functionality for converting Java objects to JSON
     * and vice versa. It supports complex serialization and deserialization
     * workflows by leveraging adapters specified during the configuration phase.
     * <p>
     * Adapters can be registered to customize the behavior of (de)serialization
     * for specific types.
     */
    private final Gson gson;

    /**
     * Constructs a new {@code JsonConfiguration} instance with the specified parameters.
     *
     * @param appName  the name of the application
     * @param homeDir  the home directory path for the application
     * @param settings a map containing configuration settings as key-value pairs
     * @param gson     the Gson instance used for JSON serialization and deserialization
     */
    public JsonConfiguration(String appName, String homeDir, Map<String, String> settings, Gson gson) {
        this.appName = appName;
        this.homeDir = homeDir;
        this.settings = settings;
        this.gson = gson;
    }

    /**
     * Stores a key-value pair into the configuration settings.
     * The value is serialized into a JSON string before being stored.
     *
     * @param key   the key under which the value will be stored
     * @param value the object to be associated with the specified key
     */
    public void set(String key, Object value) {
        settings.put(key, gson.toJson(value));
    }

    /**
     * Retrieves a value associated with the specified key from the configuration,
     * deserializing it into the specified class type using Gson.
     * If the key does not exist in the settings, the method returns {@code null}.
     *
     * @param <T>   the type of the object to be deserialized
     * @param key   the key corresponding to the value in the configuration
     * @param clazz the {@code Class} object representing the expected type of the value
     * @return the deserialized object of type {@code T}, or {@code null}
     * if the key does not exist
     */
    public <T> T get(String key, Class<T> clazz) {
        String json = settings.get(key);
        return json != null ? gson.fromJson(json, clazz) : null;
    }

    /**
     * Retrieves a value associated with the given key from the configuration as an object of the specified type.
     * If the key does not exist in the configuration, the provided default value is returned.
     *
     * @param <T>          the type of the object to be returned
     * @param key          the key whose associated value is to be retrieved
     * @param clazz        the class of the object to deserialize the value into
     * @param defaultValue the default value to return if the key does not exist or the value is null
     * @return the value associated with the specified key, deserialized into the specified type,
     * or the default value if the key does not exist or the value is null
     */
    public <T> T get(String key, Class<T> clazz, T defaultValue) {
        String json = settings.get(key);
        return json != null ? gson.fromJson(json, clazz) : defaultValue;
    }

    /**
     * Loads the configuration data from the JSON configuration file located at the path
     * determined by the {@code getConfigFilePath()} method.
     * If the configuration file exists, its content is read and deserialized into a map
     * of key-value pairs using Gson. The method clears the current settings and populates
     * them with the loaded values.
     *
     * @throws IOException if an I/O error occurs while reading the configuration file
     */
    public void load() throws IOException {
        Path configPath = getConfigFilePath();
        if (Files.exists(configPath)) {
            try (var reader = Files.newBufferedReader(configPath)) {
                Map<String, String> loaded = gson.fromJson(reader, new TypeToken<Map<String, String>>() {
                }.getType());
                settings.clear();
                settings.putAll(loaded);
            }
        }
    }

    /**
     * Constructs the path to the configuration file for the application.
     * The configuration file is located in the user's home directory, within a hidden
     * folder named after the application, and is named "config.json".
     *
     * @return the {@code Path} to the application's configuration file
     */
    private Path getConfigFilePath() {
        return Path.of(homeDir, "." + appName, "config.json");
    }

    /**
     * Saves the current configuration to a JSON file.
     * <p>
     * This method serializes the current settings into a JSON format and writes
     * them to a file located at the configuration path. If the parent directory
     * of the configuration file does not exist, it is created automatically.
     * <p>
     * The configuration file path is determined based on the application name
     * and home directory. Any existing content in the file will be overwritten
     * during this operation.
     *
     * @throws IOException if an I/O error occurs while creating the directory,
     *                     opening the file, or writing to the file
     */
    public void save() throws IOException {
        Path configPath = getConfigFilePath();
        Files.createDirectories(configPath.getParent());
        try (var writer = Files.newBufferedWriter(configPath)) {
            gson.toJson(settings, writer);
        }
    }

    /**
     * Builder class for creating instances of {@code JsonConfiguration}.
     * The {@code JsonConfigurationBuilder} allows the configuration of application
     * name, home directory, and custom Gson adapters before building a {@code JsonConfiguration} object.
     */
    public static class JsonConfigurationBuilder {
        /**
         * A map that holds custom Gson adapters to register with a GsonBuilder.
         * The keys represent the classes for which the adapters are applicable,
         * and the values are the adapter instances associated with those classes.
         * <p>
         * This variable is used to store user-defined type adapters, allowing
         * for customized serialization and deserialization behavior for specific
         * classes when constructing a {@code JsonConfiguration}.
         * <p>
         * It is populated using the {@code addGsonAdapter} method in the
         * {@code JsonConfigurationBuilder} class and is later passed to a
         * {@code GsonBuilder} for registration.
         */
        private final Map<Class<?>, Object> gsonAdapters = new HashMap<>();

        /**
         * The name of the application being configured.
         * This variable holds a string representation of the application's name and is used
         * to identify the application within the context of the {@code JsonConfiguration}.
         * It is set during the construction of a {@code JsonConfigurationBuilder} instance.
         */
        private String appName;

        /**
         * Represents the home directory path of the current user.
         * By default, this variable is initialized to the value of the "user.home" system property,
         * which typically points to the user's home directory on the filesystem.
         * <p>
         * This field can be customized to point to a different directory via the builder class
         * methods when building an instance of {@code JsonConfiguration}.
         * <p>
         * Example system values for "user.home" may include paths such as "~/Users/username" for macOS,
         * "C:/Users/username" for Windows, or "/home/username" for Unix/Linux systems.
         */
        private String homeDir = System.getProperty("user.home");

        /**
         * Default constructor for the {@code JsonConfigurationBuilder} class.
         * Initializes a new instance of the {@code JsonConfigurationBuilder}.
         * This constructor allows creation of a builder used for configuring
         * {@code JsonConfiguration} instances, enabling the specification of
         * application-specific settings such as the application name, home directory,
         * and custom Gson adapters.
         */
        public JsonConfigurationBuilder() {
            // Default constructor
        }

        /**
         * Sets the application name to be used in the configuration.
         *
         * @param appName the name of the application to be set
         * @return the current instance of {@code JsonConfigurationBuilder} for chaining
         */
        public JsonConfigurationBuilder appName(String appName) {
            this.appName = appName;
            return this;
        }

        /**
         * Sets the home directory for the configuration being built.
         * This method specifies the directory path where the application's configuration
         * files or settings should be stored.
         *
         * @param homeDir the path to the desired home directory
         * @return the current instance of {@code JsonConfigurationBuilder} to enable method chaining
         */
        public JsonConfigurationBuilder homeDir(String homeDir) {
            this.homeDir = homeDir;
            return this;
        }

        /**
         * Adds a custom Gson adapter to the builder for the specified type.
         * This method allows registration of a type-to-adapter mapping that will be applied
         * when building the Gson instance within the {@code JsonConfiguration}.
         *
         * @param type    the {@code Class} of the type for which the adapter is being registered
         * @param adapter the adapter object to be used for the specified type
         * @return the current instance of {@code JsonConfigurationBuilder}, allowing method chaining
         */
        public JsonConfigurationBuilder addGsonAdapter(Class<?> type, Object adapter) {
            gsonAdapters.put(type, adapter);
            return this;
        }

        /**
         * Builds and returns an instance of {@code JsonConfiguration} with the specified
         * settings, including the application name, home directory, and any registered
         * custom Gson adapters.
         *
         * @return a {@code JsonConfiguration} instance configured with the builder's parameters.
         */
        public JsonConfiguration build() {
            GsonBuilder gsonBuilder = new GsonBuilder();
            for (var entry : gsonAdapters.entrySet()) {
                gsonBuilder.registerTypeAdapter(entry.getKey(), entry.getValue());
            }

            Gson gson = gsonBuilder.create();

            return new JsonConfiguration(
                    appName,
                    homeDir,
                    new HashMap<>(),
                    gson
            );
        }
    }
}
