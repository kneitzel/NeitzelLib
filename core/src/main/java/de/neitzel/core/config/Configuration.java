package de.neitzel.core.config;

import de.neitzel.core.util.FileUtils;
import de.neitzel.core.util.Strings;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Properties;

/**
 * Provides a utility class for managing configuration properties in an
 * application. This class allows loading properties from files, accessing
 * values with various types including environment variable substitution,
 * and updating properties.
 */
@Slf4j
public class Configuration {

    /**
     * A {@link Properties} object that stores a set of key-value pairs.
     * This variable can be used to manage configuration settings or other
     * collections of properties within the application.
     *
     * It provides methods to load, retrieve, and modify properties
     * as necessary for the application's requirements.
     */
    private final Properties properties;

    /**
     * Constructs a new Configuration instance with default properties.
     * This constructor initializes the Configuration object using an
     * empty set of {@code Properties}.
     */
    public Configuration() {
        this(new Properties());
    }

    /**
     * Constructs a new Configuration instance using the provided properties.
     *
     * @param properties the Properties object containing configuration key-value pairs
     */
    public Configuration(final Properties properties) {
        this.properties = properties;
    }

    /**
     * Retrieves a boolean property value associated with the specified key. If the key does not exist in the properties,
     * the provided default value is returned. The method also supports interpreting specific string values as true.
     *
     * @param key the key used to retrieve the boolean property
     * @param defaultValue the default value returned if the key is not found in the properties
     * @return the boolean value associated with the key, or the defaultValue if the key does not exist
     */
    protected boolean getBooleanProperty(final String key, final boolean defaultValue) {
        if (!properties.containsKey(key)) return defaultValue;
        return getStringProperty(key, defaultValue ? "ja": "nein").equalsIgnoreCase("ja") || properties.getProperty(key).equalsIgnoreCase("true");
    }

    /**
     * Retrieves the value of the specified property as a trimmed string.
     * If the property is not found, the default value is returned.
     *
     * @param key the key of the property to retrieve
     * @param defaultValue the default value to return if the property is not found
     * @return the trimmed string value of the property, or the default value if the property is not found
     */
    protected String getStringProperty(final String key, final String defaultValue) {
        if (!properties.containsKey(key)) return defaultValue;
        return properties.getProperty(key).trim();
    }

    /**
     * Retrieves a string property value associated with the specified key, applies
     * environment variable expansion on the value, and returns the processed result.
     *
     * @param key the key identifying the property to retrieve.
     * @param defaultValue the default value to use if the property is not found.
     * @return the processed property value with expanded environment variables, or
     *         the defaultValue if the property is not found.
     */
    protected String getStringPropertyWithEnv(final String key, final String defaultValue) {
        String result = getStringProperty(key, defaultValue);
        return Strings.expandEnvironmentVariables(result);
    }

    /**
     * Retrieves the value of a string property associated with the specified key,
     * removes any surrounding quotes from the value, and returns the resultant string.
     *
     * @param key the key associated with the desired property
     * @param defaultValue the default value to return if the property is not found or is null
     * @return the string property without surrounding quotes, or the defaultValue if the property is not found
     */
    protected String getStringPropertyWithoutQuotes(final String key, final String defaultValue) {
        return Strings.removeQuotes(getStringProperty(key, defaultValue));
    }

    /**
     * Retrieves the string value of a configuration property identified by the given key,
     * removes surrounding quotes if present, and expands any environment variables found
     * within the string. If the property is not found, a default value is used.
     *
     * @param key the key identifying the configuration property
     * @param defaultValue the default value to use if the property is not found
     * @return the processed string property with quotes removed and environment variables expanded
     */
    protected String getStringPropertyWithoutQuotesWithEnv(final String key, final String defaultValue) {
        String result = getStringPropertyWithoutQuotes(key, defaultValue);
        return Strings.expandEnvironmentVariables(result);
    }

    /**
     * Retrieves the integer value for the specified property key. If the key does
     * not exist in the properties or the value is null/empty, the provided default
     * value is returned.
     *
     * @param key the property key to retrieve the value for
     * @param defaultValue the default value to return if the key is not present
     *                     or the value is null/empty
     * @return the integer value associated with the key, or the defaultValue if
     *         the key does not exist or its value is null/empty
     */
    protected Integer getIntegerProperty(final String key, final Integer defaultValue) {
        if (!properties.containsKey(key)) return defaultValue;
        String value = properties.getProperty(key);
        return Strings.isNullOrEmpty(value) ? null : Integer.parseInt(value);
    }

    /**
     * Sets an integer property in the properties object. If the provided value is null,
     * an empty string will be stored as the property's value.
     *
     * @param key the key under which the property will be stored
     * @param value the integer value to be stored; if null, an empty string will be used
     */
    protected void setIntegerProperty(final String key, final Integer value) {
        if (value == null) {
            properties.setProperty(key, "");
        } else {
            properties.setProperty(key, ""+value);
        }
    }

    /**
     * Retrieves a LocalDate value from the properties based on the provided key.
     * If the key does not exist or the value is invalid, a default value is returned.
     *
     * @param key the key to look up the property in the properties map
     * @param defaultValue the default LocalDate value to return if the key is not found
     * @param formatString the format string to parse the LocalDate value
     * @return the LocalDate value from the properties if available and valid,
     *         otherwise the defaultValue
     */
    protected LocalDate getLocalDateProperty(final String key, final LocalDate defaultValue, final String formatString) {
        if (!properties.containsKey(key)) return defaultValue;

        String value = properties.getProperty(key);
        if (value == null || value.isEmpty()) return null;
        return LocalDate.parse(value, DateTimeFormatter.ofPattern(formatString));
    }

    /**
     * Sets a property with the given key and a formatted LocalDate value.
     * If the provided value is null, the property will be set to an empty string.
     *
     * @param key the key of the property to set
     * @param value the LocalDate value to format and set as the property value
     * @param formatString the pattern string used to format the LocalDate value
     */
    protected void setLocalDateProperty(final String key, final LocalDate value, final String formatString) {
        if (value == null) {
            setProperty(key, "");
        } else {
            setProperty(key, value.format(DateTimeFormatter.ofPattern(formatString)));
        }
    }

    /**
     * Sets a property with the specified key to the given value. If the value is null,
     * it defaults to an empty string. Logs the operation and updates the property.
     *
     * @param key the key of the property to be set
     * @param value the value to be associated with the specified key; defaults to an empty string if null
     */
    public void setProperty(final String key, final String value) {
        String newValue = value == null ? "" : value;
        log.info("Setting a new value for '" + key + "': '" + newValue + "'");
        properties.setProperty(key, newValue);
    }

    /**
     * Loads the content of the specified file using the provided file name.
     *
     * @param fileName the name of the file to be loaded
     */
    public void load(final String fileName) {
        load(fileName, Charset.defaultCharset().name(), true);
    }

    /**
     * Loads the configuration from a specified file. If the file does not exist in the
     * specified location, it attempts to find the file alongside the JAR file of the application.
     * Reads the configuration with the provided encoding and an option to accept UTF-8 encoding.
     *
     * @param fileName    the name of the configuration file to be loaded
     * @param encoding    the encoding format to be used while reading the configuration file
     * @param acceptUTF8  a boolean flag indicating whether to accept UTF-8 encoding
     */
    public void load(final String fileName, final String encoding, final boolean acceptUTF8) {
        log.info("Reading Config: " + fileName + " with encoding: " + encoding + "accepting UTF-8: " + acceptUTF8);
        File configFile = new File(fileName);

        // Try to get the file next to the jar file if the configFile does not exist.
        if (!configFile.exists()) {
            try {
                String fileAtJar = new File(Configuration.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent() + "/" + fileName;
                configFile = new File(fileAtJar);
            } catch (URISyntaxException ex) {
                log.error("Unable to get path of jar file / class.", ex);
            }
        }

        // Read the configuration file if it exists.
        if (configFile.exists()) {
            log.info("Reading Configuration file. " + configFile.getAbsolutePath());
            try (InputStreamReader reader = FileUtils.createUniversalFileReader(configFile, encoding, acceptUTF8)) {
                log.info("Reading the configuration with encoding: " + reader.getEncoding());
                properties.load(reader);
            } catch (FileNotFoundException fnfe) {
                log.error("Configuration file: " + fileName + " not found!", fnfe);
            } catch (IOException ioe) {
                log.error("Cannot read config file.", ioe);
            }
        } else {
            log.error("Unable to load config file! Last try: " + configFile.getAbsolutePath());
        }
    }

    /**
     * Merges the properties from the provided configuration into the current configuration.
     *
     * @param config the Configuration object whose properties will be merged into this instance
     */
    public void merge(final Configuration config) {
        for(Map.Entry<Object, Object> entry: config.properties.entrySet()) {
            properties.put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Removes the specified key-value pair from the properties map if the key exists.
     *
     * @param key the key to be removed from the properties map
     */
    public void remove(final String key){
        if (properties.containsKey(key)) properties.remove(key);
    }
}

