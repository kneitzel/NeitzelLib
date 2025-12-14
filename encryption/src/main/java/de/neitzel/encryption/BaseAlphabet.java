package de.neitzel.encryption;

import com.idealista.fpe.config.Alphabet;

/**
 * Base implementation of the {@link Alphabet} interface providing common helper methods
 * for working with character sets and validating characters against the alphabet.
 */
public interface BaseAlphabet extends Alphabet {
    /**
     * Replaces illegal characters of a string with a given replacement character.
     *
     * @param text        Text to check; may not be null
     * @param replacement Replacement character to use for characters not present in the alphabet
     * @return a new string in which every invalid character was replaced with the provided replacement
     * @throws IllegalArgumentException if {@code replacement} is not a valid character in the alphabet
     */
    default String replaceIllegalCharacters(String text, char replacement) {
        // Validate
        if (!isValidCharacter(replacement)) throw new IllegalArgumentException("replacement");

        StringBuilder result = new StringBuilder();
        for (char ch : text.toCharArray()) {
            if (isValidCharacter(ch)) {
                result.append(ch);
            } else {
                result.append(replacement);
            }
        }
        return result.toString();
    }

    /**
     * Checks if a given Character is part of the alphabet.
     *
     * @param character Character to check.
     * @return true if character is valid, else false.
     */
    default boolean isValidCharacter(char character) {
        // Compare with all characters
        for (char ch : availableCharacters()) {
            if (ch == character) return true;
        }

        // Character not found.
        return false;
    }
}
