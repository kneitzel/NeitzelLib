package de.neitzel.fx.view.fixture;

import de.neitzel.fx.view.Converter;
import jakarta.inject.Singleton;

/**
 * Shows an age and has no reverse conversion.
 */
@Singleton
public class ShownAgeConverter implements Converter {

    /**
     * Turns the age into label text.
     *
     * @param attributeValue the age
     * @return the label text
     */
    @Override
    public Object toControlValue(Object attributeValue) {
        return "shown " + attributeValue;
    }
}
