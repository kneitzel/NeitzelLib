package de.neitzel.core.io;

/**
 * Utility class for encoding and decoding strings.
 * This class provides methods for transforming strings into encoded representations
 * and decoding them back to the original representation.
 */
public class StringEncoder {

    /**
     * Private constructor to prevent instantiation of the utility class.
     * This utility class is not meant to be instantiated, as it only provides
     * static utility methods for array-related operations.
     *
     * @throws UnsupportedOperationException always, to indicate that this class
     *                                       should not be instantiated.
     */
    private StringEncoder() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Decodes a string containing encoded characters back to its original representation.
     * Encoded characters are expected to be in the format {@code "&amp;#<code>;<code>"}, where
     * {@code <code>} is a numeric representation of the character to decode.
     *
     * @param data the string to decode; may contain encoded characters or regular text. If null, an empty string is returned.
     * @return the decoded string with all encoded characters replaced by their original representations.
     * @throws IllegalArgumentException if the input string has encoding markup
     *                                  that is improperly formatted or incomplete.
     */
    public static String decodeData(final String data) {
        if (data == null) return "";

        String remaining = data;
        StringBuilder result = new StringBuilder();
        while (!remaining.isEmpty()) {
            int indexAmp = remaining.indexOf("&");
            if (indexAmp == -1) {
                result.append(remaining);
                remaining = "";
            } else {
                // First get the elements till the &
                if (indexAmp > 0) {
                    result.append(remaining.substring(0, indexAmp));
                    remaining = remaining.substring(indexAmp);
                }
                int endSpecial = remaining.indexOf(";");
                if (endSpecial == -1) throw new IllegalArgumentException("String couldn't be decoded! (" + data + ")");
                String special = remaining.substring(0, endSpecial + 1);
                remaining = remaining.substring(endSpecial + 1);
                result.append(decodeCharacter(special));
            }
        }
        return result.toString();
    }

    /**
     * Decodes a character from its numeric character reference representation.
     * The input string must represent an encoded character in the format {@code "&#<code_point>;"}.
     *
     * @param data the string containing the numeric character reference to decode.
     *             It must start with {@code "&#"} and end with {@code ";"}.
     * @return the decoded character represented by the numeric character reference.
     * @throws IllegalArgumentException if the input string does not follow the expected format.
     */
    public static char decodeCharacter(final String data) {
        if (!data.startsWith("&#")) throw new IllegalArgumentException("Data does not start with &# (" + data + ")");
        if (!data.endsWith(";")) throw new IllegalArgumentException("Data does not end with ; (" + data + ")");
        return (char) Integer.parseInt(data.substring(2, data.length() - 1));
    }

    /**
     * Encodes the provided string by converting characters outside the ASCII range (32-127)
     * and the ampersand {@code "&"} character into their corresponding numeric character reference
     * representation (e.g., {@code "&#38;"}).
     *
     * @param data the input string to encode; if null, an empty string is returned
     * @return an encoded string where non-ASCII and ampersand characters
     * are replaced with numeric character references
     */
    public static String encodeData(final String data) {
        if (data == null) return "";

        StringBuilder result = new StringBuilder();
        for (int index = 0; index < data.length(); index++) {
            char character = data.charAt(index);
            if (character < 32 || character > 127 || character == 38) {
                result.append("&#" + (int) character + ";");
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }
}
