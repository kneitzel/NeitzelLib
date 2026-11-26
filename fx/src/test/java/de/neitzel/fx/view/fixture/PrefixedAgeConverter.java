package de.neitzel.fx.view.fixture;

import de.neitzel.fx.view.Converter;

/**
 * Converts an age using the prefix from {@link AgeUnit}.
 */

public class PrefixedAgeConverter implements Converter {

    /**
     * The unit written in front of an age.
     */
    private final AgeUnit ageUnit;

    /**
     * Creates a converter that reads its prefix from the injected service.
     *
     * @param ageUnit the service that supplies the prefix
     */

    public PrefixedAgeConverter(AgeUnit ageUnit) {
        this.ageUnit = ageUnit;
    }

    /**
     * Writes the injected prefix in front of the age.
     *
     * @param attributeValue the age
     * @return the control text
     */
    @Override
    public Object toControlValue(Object attributeValue) {
        return ageUnit.prefix() + attributeValue;
    }

    /**
     * Reads an age that starts with the injected prefix.
     *
     * @param controlValue the control text
     * @return the age
     */
    @Override
    public Object toAttributeValue(Object controlValue) {
        String text = controlValue == null ? "" : controlValue.toString();
        String prefix = ageUnit.prefix();
        if (!text.startsWith(prefix)) {
            throw new IllegalArgumentException("not a whole number: " + text);
        }
        try {
            return Integer.valueOf(text.substring(prefix.length()));
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("not a whole number: " + text);
        }
    }
}
