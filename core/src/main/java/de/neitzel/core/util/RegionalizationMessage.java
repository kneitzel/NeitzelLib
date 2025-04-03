package de.neitzel.core.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

public class RegionalizationMessage {

    /**
     * Resource Bundle to use for operations.
     */
    private ResourceBundle res;

    /**
     * Creates a new instance of RegionalizationMessage.
     * @param source Source of messages.
     */
    public RegionalizationMessage(final String source) {
        res = ResourceBundle.getBundle(source);
    }

    /**
     * Creates a new instance of RegionalizationMessage.
     * @param source Source of messages.
     * @param locale Locale to use.
     */
    public RegionalizationMessage(final String source, final Locale locale) {
        res = ResourceBundle.getBundle(source, locale);
    }

    /**
     * Gets the Message behind a key.
     * @param key Key to look up.
     * @return Message or null.
     */
    public String getMessage(final String key) {
        if (!res.containsKey(key)) return null;
        return res.getString(key);
    }

    /**
     * Gets the Message behind a key.
     * @param key Key to look up.
     * @param defaultMessage Default message to use if message is not available.
     * @return Message from resource or default Message.
     */
    public String getMessage(final String key, final String defaultMessage) {
        if (res.containsKey(key))
            return res.getString(key);

        return defaultMessage;
    }

    /**
     * Gets a formatted message.
     * @param key key to look up message.
     * @param defaultMessage Default message to use if message couldn't be loaded.
     * @param params parameter to format the message.
     * @return Formatted message.
     */
    public String getFormattedMessage(final String key, final String defaultMessage, final Object... params) {
        MessageFormat format = new MessageFormat(getMessage(key, defaultMessage));
        return format.format(params);
    }
}
