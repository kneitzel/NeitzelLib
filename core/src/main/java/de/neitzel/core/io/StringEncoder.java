package de.neitzel.core.io;

/**
 * An encoder for Strings.
 * <p>
 *     All characters with unicode number less than 32 or greater than 127 or 38 (Ampersend)
 *     will be encoded with &#number; with number as the decimal unicode number.
 */
public class StringEncoder {

    /**
     * Decodes the encoded String.
     * @param data Encoded string.
     * @return Decoded String.
     */
    public static String decodeData(final String data) {
        if (data == null) return "";

        String remaining = data;
        StringBuilder result = new StringBuilder();
        while (!remaining.isEmpty()) {
            int indexAmp = remaining.indexOf("&");
            if (indexAmp == -1) {
                result.append(remaining);
                remaining="";
            } else {
                // First get the elements till the &
                if (indexAmp > 0) {
                    result.append(remaining.substring(0, indexAmp));
                    remaining = remaining.substring(indexAmp);
                }
                int endSpecial=remaining.indexOf(";");
                if (endSpecial == -1) throw new IllegalArgumentException("String couldn't be decoded! (" + data + ")");
                String special = remaining.substring(0, endSpecial+1);
                remaining = remaining.substring(endSpecial+1);
                result.append(decodeCharacter(special));
            }
        }
        return result.toString();
    }

    /**
     * Decode a single character.
     * @param data String in the form &#xxx; with xxx a decimal number.
     * @return The decoded character.
     */
    public static char decodeCharacter(final String data) {
        if (!data.startsWith("&#")) throw new IllegalArgumentException("Data does not start with &# (" + data + ")");
        if (!data.endsWith(";")) throw new IllegalArgumentException("Data does not end with ; (" + data + ")");
        return (char)Integer.parseInt(data.substring(2, data.length()-1));
    }

    /**
     * Encode data to a better String representation.
     * <p>
     *     All Characters between from ascii 32 to 127 (except 38 / &)
     *     are replaced with a &#code; where code is the number of the character.
     * @param data String that should be encoded.
     * @return Encoded String.
     */
    public static String encodeData(final String data) {
        if (data == null) return "";

        StringBuilder result = new StringBuilder();
        for (int index=0; index < data.length(); index++) {
            char character = data.charAt(index);
            if (character < 32 || character > 127 || character == 38) {
                result.append("&#" + (int)character + ";");
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }
}
