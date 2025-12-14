package de.neitzel.encryption;

/**
 * Main characters of the Alphabet.
 */
public class MainAlphabet implements BaseAlphabet {

    /**
     * All characters that are part of the alphabet used by the MainDomain.
     * This array includes lowercase letters, uppercase letters, digits, a set of
     * special characters, and German-specific characters.
     */
    private static final char[] ALL_CHARS = new char[]{
            // lowercase characters
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r',
            's', 't', 'u', 'v', 'w', 'x', 'y', 'z',

            // uppercase characters
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R',
            'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',

            // numbers
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',

            // special characters
            '-', '_', '?', ' ', '#', '+', '/', '*', '!', '"', '§', '$', '%', '&', '(', ')', '=', '@',
            '€', ',', ';', '.', ':', '<', '>', '|', '\'', '\\', '{', '}', '[', ']',

            // german special characters
            'ä', 'Ä', 'ö', 'Ö', 'ü', 'Ü', 'ß'
    };

    /**
     * Creates a new MainAlphabet instance.
     *
     * <p>This constructor performs no runtime initialization because the
     * alphabet is represented by the static {@code ALL_CHARS} array. Use
     * {@link #availableCharacters()} to obtain the supported characters and
     * {@link #radix()} to obtain the alphabet size.</p>
     *
     * <p> Important usage notes: </p>
     * <ul>
     *   <li>The array returned by {@link #availableCharacters()} is the internal
     *       static array; callers must not modify it.</li>
     *   <li>The alphabet intentionally contains letters (lower and upper case),
     *       digits, common punctuation and a set of German-specific characters
     *       to support locale-sensitive operations.</li>
     * </ul>
     */
    public MainAlphabet() {
        // default constructor only ...
    }

    /**
     * Gets the available characters.
     *
     * @return an array containing all supported characters in the alphabet
     */
    @Override
    public char[] availableCharacters() {
        return ALL_CHARS;
    }

    /**
     * Gets the radix of the alphabet.
     *
     * @return the number of characters in the alphabet (radix)
     */
    @Override
    public Integer radix() {
        return ALL_CHARS.length;
    }
}
