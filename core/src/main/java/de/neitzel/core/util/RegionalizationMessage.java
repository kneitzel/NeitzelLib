package de.neitzel.core.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * The RegionalizationMessage class provides functionality to retrieve
 * localized messages from a resource bundle and format messages with parameters.
 * It supports multiple constructors for initializing with specific locales if needed.
 */
public class RegionalizationMessage {

    /**
     * The `res` variable holds a `ResourceBundle` instance,
     * which is used to retrieve locale-specific objects and messages.
     * It facilitates the process of internationalization by loading resources
     * such as text and messages from specified bundles based on the provided locale.
     * <p>
     * This variable is initialized in constructors of the `RegionalizationMessage` class
     * and is used internally by various methods to fetch localized messages.
     */
    private ResourceBundle res;

    /**
     * Constructs a new RegionalizationMessage instance using the specified resource bundle source.
     * This constructor initializes the resource bundle with the given source name.
     *
     * @param source The base name of the resource bundle to load.
     *               This is typically a fully qualified class name or package name for the resource.
     */
    public RegionalizationMessage(final String source) {
        res = ResourceBundle.getBundle(source);
    }

    /**
     * Constructs a RegionalizationMessage object to retrieve localized messages
     * from a specified resource bundle using the given source and locale.
     *
     * @param source The base name of the resource bundle, a fully qualified class name.
     * @param locale The Locale object specifying the desired language and region for localization.
     */
    public RegionalizationMessage(final String source, final Locale locale) {
        res = ResourceBundle.getBundle(source, locale);
    }

    /**
     * Retrieves the localized message corresponding to the specified key from the resource bundle.
     * If the key does not exist in the resource bundle, this method returns null.
     *
     * @param key The key for which the localized message needs to be retrieved.
     * @return The localized message as a String if the key exists in the resource bundle;
     * otherwise, null.
     */
    public String getMessage(final String key) {
        if (!res.containsKey(key)) return null;
        return res.getString(key);
    }

    /**
     * Retrieves a localized and formatted message from a resource bundle based on a key.
     * If the key is not found in the resource bundle, a default message is used.
     * The message supports parameter substitution using the supplied arguments.
     *
     * @param key            The key used to retrieve the localized message from the resource bundle.
     * @param defaultMessage The default message to be used if the key is not found in the resource bundle.
     * @param params         The parameters to substitute into the message placeholders.
     * @return A formatted string containing the localized message with substituted parameters.
     */
    public String getFormattedMessage(final String key, final String defaultMessage, final Object... params) {
        MessageFormat format = new MessageFormat(getMessage(key, defaultMessage));
        return format.format(params);
    }

    /**
     * Retrieves a localized message for the given key from the resource bundle.
     * If the key is not found, the specified default message is returned.
     *
     * @param key            The key to look up in the resource bundle.
     * @param defaultMessage The default message to return if the key is not found.
     * @return The localized message corresponding to the key, or the default message
     * if the key does not exist in the resource bundle.
     */
    public String getMessage(final String key, final String defaultMessage) {
        if (res.containsKey(key))
            return res.getString(key);

        return defaultMessage;
    }
}
