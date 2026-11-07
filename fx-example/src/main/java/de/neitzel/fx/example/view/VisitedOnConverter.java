package de.neitzel.fx.example.view;

import de.neitzel.fx.view.Converter;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Translates a visit date between a {@link LocalDate} and the ISO text in the table row.
 */
@Singleton
public class VisitedOnConverter implements Converter {

    /**
     * Creates the converter. The container calls this constructor.
     */
    public VisitedOnConverter() {
    }

    /**
     * Writes the date as an ISO local date, or an empty string when the date is missing.
     *
     * @param attributeValue the visit date
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
     * Reads an ISO local date. An empty field clears the date.
     *
     * @param controlValue the field text
     * @return the date, or {@code null} when the field is empty
     * @throws IllegalArgumentException when the text is not an ISO local date
     */
    @Override
    public Object toAttributeValue(Object controlValue) {
        String text = controlValue == null ? "" : controlValue.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException failure) {
            throw new IllegalArgumentException("Visited on must be a date: " + text);
        }
    }
}
