package de.neitzel.fx.example.view;

import de.neitzel.fx.view.Converter;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Translates an age between the Model and the text field.
 */
@Singleton
public class AgeConverter implements Converter {

    /**
     * The text written in front of the age.
     */
    private final AgeSuffix suffix;

    /**
     * Creates a converter that reads its suffix from the injected service.
     *
     * @param suffix the suffix written in front of the age
     */
    @Inject
    public AgeConverter(AgeSuffix suffix) {
        this.suffix = suffix;
    }

    /**
     * Writes the suffix in front of the age.
     *
     * @param attributeValue the age
     * @return the text shown in the field
     */
    @Override
    public Object toControlValue(Object attributeValue) {
        if (attributeValue == null) {
            return "";
        }
        return suffix.prefix() + attributeValue;
    }

    /**
     * Reads an age that starts with the suffix.
     *
     * @param controlValue the field text
     * @return the age
     */
    @Override
    public Object toAttributeValue(Object controlValue) {
        String text = controlValue == null ? "" : controlValue.toString();
        String prefix = suffix.prefix();
        if (!text.startsWith(prefix)) {
            throw new IllegalArgumentException("Age must start with \"" + prefix.trim() + "\": " + text);
        }
        try {
            return Integer.valueOf(text.substring(prefix.length()).trim());
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Age must be a whole number: " + text);
        }
    }
}
