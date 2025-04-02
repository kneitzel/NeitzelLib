package de.neitzel.encryption;

/**
 * Main characters of the Alphabet.
 */
public class MainAlphabet extends BaseAlphabet {

    /**
     * All characters we want to use.
     */
    private static final char[] ALL_CHARS = new char[] {
            // lowercase characters
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r',
            's', 't', 'u', 'v', 'w', 'x', 'y', 'z',

            // uppercase characters
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R',
            'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',

            // numbers
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',

            // special characters
            '-', '_', '?', ' ', '#', '+', '/', '*', '!', '\"', '§', '$', '%', '&', '(', ')', '=', '@',
            '€', ',', ';', '.', ':', '<', '>', '|', '\'', '\\', '{', '}', '[', ']',

            // german special characters
            'ä', 'Ä', 'ö', 'Ö', 'ü', 'Ü', 'ß'
    };

    /**
     * Gets the available characters.
     * @return
     */
    @Override
    public char[] availableCharacters() {
        return ALL_CHARS;
    }

    /**
     * Gets the radix of the alphabet.
     * @return
     */
    @Override
    public Integer radix() {
        return ALL_CHARS.length;
    }
}
