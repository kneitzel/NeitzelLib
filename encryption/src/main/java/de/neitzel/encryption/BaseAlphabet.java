package de.neitzel.encryption;

import com.idealista.fpe.config.Alphabet;

public abstract class BaseAlphabet implements Alphabet {
    /**
     * Replaces illegal characters of a string with a given replacement character
     * @param text Text to check.
     * @param replacement Replacement character.
     * @return String in which all characters was replaced.
     */
    public String replaceIllegalCharacters(String text, char replacement) {
        // Validate
        if (!isValidCharacter(replacement)) throw new IllegalArgumentException("replacement");

        StringBuilder result = new StringBuilder();
        for (char ch: text.toCharArray()) {
            if (isValidCharacter(ch)){
                result.append(ch);
            } else {
                result.append(replacement);
            }
        }
        return result.toString();
    }

    /**
     * Checks if a given Character is part of the alphabet.
     * @param character Character to check.
     * @return true if character is valid, else false.
     */
    public boolean isValidCharacter(char character) {
        // Compare with all characters
        for (char ch : availableCharacters()) {
            if (ch == character) return true;
        }

        // Character not found.
        return false;
    }
}
