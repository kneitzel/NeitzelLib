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
 * Base class for a simple configuration.
 */
@Slf4j
public class Configuration {

    /**
     * Properties with configuration data.
     */
    private Properties properties = new Properties();

    /**
     * Gets a boolean property
     * @param defaultValue Default value to return if key is not present.
     * @param key Key of the property to get.
     * @return The value of the property if it exists or the default value.
     */
    protected boolean getBooleanProperty(final String key, final boolean defaultValue) {
        if (!properties.containsKey(key)) return defaultValue;
        return getStringProperty(key, defaultValue ? "ja": "nein").equalsIgnoreCase("ja") || properties.getProperty(key).equalsIgnoreCase("true");
    }

    /**
     * Gets a String property
     * @param key Key of the property to query.
     * @param defaultValue Default value to return if property not available.
     * @return The property if it exists, else the default value.
     */
    protected String getStringProperty(final String key, final String defaultValue) {
        if (!properties.containsKey(key)) return defaultValue;
        return properties.getProperty(key).trim();
    }

    /**
     * Gets a String property with all environment variables replaced
     * <p>
     *     Environment variable can be included with ${variable name}.
     * </p>
     * @param key Key of the property to query.
     * @param defaultValue Default value to return if property not available.
     * @return The property if it exists, else the default value.
     */
    protected String getStringPropertyWithEnv(final String key, final String defaultValue) {
        String result = getStringProperty(key, defaultValue);
        return Strings.expandEnvironmentVariables(result);
    }

    /**
     * Gets a String property without quotes.
     * <p>
     *     If the value is put in quotes, then the quote signs are replaced.
     * </p>
     * @param key Key of the property to query.
     * @param defaultValue Default value to return if property not available.
     * @return The property if it exists, else the default value (without leading/ending quote signs).
     */
    protected String getStringPropertyWithoutQuotes(final String key, final String defaultValue) {
        return Strings.removeQuotes(getStringProperty(key, defaultValue));
    }

    /**
     * Gets a String property without quotes.
     * <p>
     *     If the value is put in quotes, then the quote signs are replaced.
     * </p>
     * @param key Key of the property to query.
     * @param defaultValue Default value to return if property not available.
     * @return The property if it exists, else the default value (without leading/ending quote signs).
     */
    protected String getStringPropertyWithoutQuotesWithEnv(final String key, final String defaultValue) {
        String result = getStringPropertyWithoutQuotes(key, defaultValue);
        return Strings.expandEnvironmentVariables(result);
    }

    /**
     * Gets an Integer property.
     * <br>
     *     Supports null as value.
     * @param defaultValue Default value to return if key is not present.
     * @param key Key of the property to get.
     * @return The value of the property if it exists or the default value.
     */
    protected Integer getIntegerProperty(final String key, final Integer defaultValue) {
        if (!properties.containsKey(key)) return defaultValue;
        String value = properties.getProperty(key);
        return Strings.isNullOrEmpty(value) ? null : Integer.parseInt(value);
    }

    /**
     * Sets an Integer property.
     * @param key Key of the property to set.
     * @param value Value to set.
     */
    protected void setIntegerProperty(final String key, final Integer value) {
        if (value == null) {
            properties.setProperty(key, "");
        } else {
            properties.setProperty(key, ""+value);
        }
    }

    /**
     * Gets the property as LocalDate.
     * <br>
     *     An null value or an empty String is given as null.
     * @param key Key of the property.
     * @param defaultValue default Value.
     * @param formatString Format String to use.
     * @return The LocalDate stored the property or the default value if property unknown or couldn't be parsed.
     */
    protected LocalDate getLocalDateProperty(final String key, final LocalDate defaultValue, final String formatString) {
        if (!properties.containsKey(key)) return defaultValue;

        String value = properties.getProperty(key);
        if (value == null || value.isEmpty()) return null;
        return LocalDate.parse(value, DateTimeFormatter.ofPattern(formatString));
    }

    /**
     * Sets the LocalDate using the provided format String.
     * <br>
     *     Null is allowed and is stored as empty string.
     * @param key Key of the property to set.
     * @param value Value to set.
     * @param formatString Format string to use.
     */
    protected void setLocalDateProperty(final String key, final LocalDate value, final String formatString) {
        if (value == null) {
            setProperty(key, "");
        } else {
            setProperty(key, value.format(DateTimeFormatter.ofPattern(formatString)));
        }
    }

    /**
     * Sets a property to a new value.
     * @param key Key of property to set.
     * @param value New value of property.
     */
    public void setProperty(final String key, final String value) {
        String newValue = value == null ? "" : value;
        log.info("Setting a new value for '" + key + "': '" + newValue + "'");
        properties.setProperty(key, newValue);
    }

    /**
     * Loads the configuration of the file.
     */
    public void load(final String fileName) {
        load(fileName, Charset.defaultCharset().name(), true);
    }

    /**
     * Loads the configuration of the file.
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
     * Insert the configuration settings of the given config.
     * @param config Configuration to merge into this instance.
     */
    public void merge(final Configuration config) {
        for(Map.Entry<Object, Object> entry: config.properties.entrySet()) {
            properties.put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Removes a key from the configuration.
     * @param key
     */
    public void remove(final String key){
        if (properties.containsKey(key)) properties.remove(key);
    }
}

