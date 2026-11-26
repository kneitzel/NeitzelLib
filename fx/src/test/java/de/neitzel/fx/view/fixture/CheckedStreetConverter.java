package de.neitzel.fx.view.fixture;

import de.neitzel.fx.view.Converter;

/**
 * Accepts a street written as {@code ok } plus the street, and rejects anything else.
 */

public class CheckedStreetConverter implements Converter {

    /**
     * Shows the street as it is stored.
     *
     * @param attributeValue the street
     * @return the control text
     */
    @Override
    public Object toControlValue(Object attributeValue) {
        return attributeValue == null ? "" : attributeValue.toString();
    }

    /**
     * Reads a street that starts with {@code ok }.
     *
     * @param controlValue the control text
     * @return the street
     */
    @Override
    public Object toAttributeValue(Object controlValue) {
        String text = controlValue == null ? "" : controlValue.toString();
        if (!text.startsWith("ok ")) {
            throw new IllegalArgumentException("not a street: " + text);
        }
        return text.substring("ok ".length());
    }
}
