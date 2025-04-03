package de.neitzel.core.util;

import lombok.NonNull;

import java.util.Map;

/**
 * Utility Methods for Strings
 */
public class Strings {
    /**
     * Environment variables of the system.
     */
    private static Map<String, String> environmentVariables = System.getenv();

    /**
     * Expands environment Variables inside a string.
     * <p>
     *     The environment variables should be given as ${variable}.
     * </p>
     * <p>
     *     Backslash inside variable values are replaced with slashes to avoid that they are seen as escape character.
     *     This makes the use for paths on windows possible but reduces the use with non paths in which you need a backslash.
     * </p>
     * @param text Test in which environment variables should be expanded.
     * @return String with all environment variables expanded.
     */
    public static String expandEnvironmentVariables(String text) {
        if (text == null) return null;
        for (Map.Entry<String, String> entry : environmentVariables.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().replace('\\', '/');
            text = text.replaceAll("\\$\\{" + key + "\\}", value);
        }
        return text;
    }

    /**
     * Checks if the given String is null or empty.
     * @param string String to check.
     * @return true if given String is null or empty.
     */
    public static boolean isNullOrEmpty(final String string) {
        return (string == null || string.length()==0);
    }

    /**
     * Removes a leading / ending quote if there.
     * @param value String to remove the quotes.
     * @return Parameter string if it does not start / end with a quote, else string without leading/ending quote.
     */
    public static String removeQuotes(final String value) {
        String trimmedValue = value.trim();
        if (trimmedValue.length() > 1 && trimmedValue.startsWith("\"") && trimmedValue.endsWith("\""))
            return trimmedValue.substring(1, trimmedValue.length()-1);

        return value;
    }

    /**
     * Filters a given String and removes all illegal characters.
     * @param value String value to filter all illegal characters from.
     * @param illegalCharacters String with all Illegal Characters to filter out.
     * @param replacement String to replace illegal characters with.
     * @param combine Should multiple illegal characters that are together be replaced with one replacement string only?
     * @return A new string where all illegal characters are replaced with the replacement string.
     */
    public static String replaceIllegalCharacters(final String value, final String illegalCharacters, final String replacement, final boolean combine) {
        String replacementRegex = "[" + illegalCharacters + "]" + (combine ? "+" : "");
        return value.replaceAll(replacementRegex, replacement);
    }

    /**
     * Filters a given String and removes all illegal characters.
     * <p>
     *     All characters, that are not inside allowedCharacters, are illegal characters.
     * </p>
     * @param value String value to filter all illegal characters from.
     * @param allowedCharacters String with all allowed Characters to filter out.
     * @param replacement String to replace illegal characters with.
     * @param combine Should multiple illegal characters that are together be replaced with one replacement string only?
     * @return A new string where all illegal characters are replaced with the replacement string.
     */
    public static String replaceNonAllowedCharacters(final String value, final String allowedCharacters, final String replacement, final boolean combine) {
        String replacementRegex = "[^" + allowedCharacters + "]" + (combine ? "+" : "");
        return value.replaceAll(replacementRegex, replacement);
    }

    /**
     * Gets the next Element which follows to the current element.
     * @param element to get the precessor.
     * @return The next element after this one.
     */
    public static String increment(@NonNull final String element) {
        if (element.isEmpty()) return "1";

        String firstPart = element.substring(0, element.length()-1);
        char lastChar = element.charAt(element.length()-1);
        if (lastChar == '9') return firstPart + 'A';
        if (lastChar == 'Z') return increment(firstPart) + '0';
        return firstPart + (char)(lastChar+1);
    }

    public static String limitCharNumber(final String original, final int maxLength) {
        if (original == null || original.length() <= maxLength) return original;

        return original.substring(0, maxLength);
    }
}
