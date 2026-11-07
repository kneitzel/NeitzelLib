package de.neitzel.fx.example.view;

import de.neitzel.fx.view.Converter;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Keeps a street that is not blank, and reports an empty street through the injected policy.
 * <p>
 * The container fills {@link #policy} by field injection after the no-arg constructor runs.
 */
@Singleton
public class StreetConverter implements Converter {

    /**
     * The notice text used when the street is empty. The container sets this field.
     */
    @Inject
    private StreetPolicy policy;

    /**
     * Creates a converter. The container calls this constructor, then injects {@link #policy}.
     */
    public StreetConverter() {
    }

    /**
     * Returns the street unchanged.
     *
     * @param attributeValue the street
     * @return the text shown in the field
     */
    @Override
    public Object toControlValue(Object attributeValue) {
        if (attributeValue == null) {
            return "";
        }
        return attributeValue.toString();
    }

    /**
     * Returns the street with surrounding whitespace removed.
     *
     * @param controlValue the field text
     * @return the street written to the address
     * @throws IllegalArgumentException when the text is blank
     */
    @Override
    public Object toAttributeValue(Object controlValue) {
        String text = controlValue == null ? "" : controlValue.toString().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(policy.message());
        }
        return text;
    }
}
