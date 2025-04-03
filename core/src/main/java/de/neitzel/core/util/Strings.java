package de.neitzel.core.util;

import lombok.NonNull;

import java.util.Map;

/**
 * Utility class providing common string manipulation methods.
 */
public class Strings {
    /**
     * Private constructor to prevent instantiation of the utility class.
     * This utility class is not meant to be instantiated, as it only provides
     * static utility methods for array-related operations.
     *
     * @throws UnsupportedOperationException always, to indicate that this class
     *                                        should not be instantiated.
     */
    private Strings() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * A map that holds the system's environment variables. The keys represent
     * environment variable names, and the values represent their corresponding
     * values. It is initialized with the current system environment variables using
     * {@link System#getenv()}.
     *
     * This variable provides access to the underlying system environment, which can
     * be used for various purposes such as configuration, path resolution, or
     * dynamic value substitution.
     *
     * Note: Modifications to this map will not affect the system environment, as it
     * is a copy of the environment at the time of initialization.
     */
    private static Map<String, String> environmentVariables = System.getenv();

    /**
     * Expands environment variable placeholders within the provided text.
     * Placeholders in the format ${VARIABLE_NAME} are replaced with their
     * corresponding values from the environment variables.
     *
     * @param text The input string containing environment variable placeholders.
     *             If null, the method returns null.
     * @return A string with the placeholders replaced by their corresponding
     *         environment variable values. If no placeholders are found,
     *         the original string is returned.
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
     * Checks whether a given string is either null or empty.
     *
     * @param string the string to be checked for nullity or emptiness.
     * @return true if the string is null or has a length of 0; false otherwise.
     */
    public static boolean isNullOrEmpty(final String string) {
        return (string == null || string.length()==0);
    }

    /**
     * Removes surrounding double quotes from the input string if they are present.
     * Trims leading and trailing whitespaces from the input before processing.
     *
     * @param value The input string that might contain surrounding double quotes.
     *              The input cannot be null, and leading/trailing whitespaces will be ignored.
     * @return A string with leading and trailing double quotes removed if present.
     *         Returns the original string if no surrounding double quotes are detected.
     */
    public static String removeQuotes(final String value) {
        String trimmedValue = value.trim();
        if (trimmedValue.length() > 1 && trimmedValue.startsWith("\"") && trimmedValue.endsWith("\""))
            return trimmedValue.substring(1, trimmedValue.length()-1);

        return value;
    }

    /**
     * Replaces illegal characters in a given string with a specified replacement string.
     * Optionally, consecutive illegal characters can be combined into a single replacement.
     *
     * @param value The input string in which illegal characters are to be replaced.
     * @param illegalCharacters A string specifying the set of illegal characters to be matched.
     * @param replacement The string to replace each illegal character or group of characters.
     * @param combine A boolean flag indicating whether consecutive illegal characters should be replaced
     *                with a single replacement string. If true, groups of illegal characters are combined.
     * @return A new string with the illegal characters replaced by the provided replacement string.
     */
    public static String replaceIllegalCharacters(final String value, final String illegalCharacters, final String replacement, final boolean combine) {
        String replacementRegex = "[" + illegalCharacters + "]" + (combine ? "+" : "");
        return value.replaceAll(replacementRegex, replacement);
    }

    /**
     * Replaces characters in the input string that are not in the allowed characters set
     * with a specified replacement string. Optionally combines consecutive non-allowed
     * characters into a single replacement.
     *
     * @param value The input string to be processed.
     * @param allowedCharacters A string containing the set of characters that are allowed.
     *                           Any character not in this set will be replaced.
     * @param replacement The string to replace non-allowed characters with.
     * @param combine If true, consecutive non-allowed characters will be replaced with
     *                a single instance of the replacement string.
     * @return A new string with non-allowed characters replaced according to the specified rules.
     */
    public static String replaceNonAllowedCharacters(final String value, final String allowedCharacters, final String replacement, final boolean combine) {
        String replacementRegex = "[^" + allowedCharacters + "]" + (combine ? "+" : "");
        return value.replaceAll(replacementRegex, replacement);
    }

    /**
     * Increments a string value by modifying its last character, or leading characters if necessary.
     * The increment follows these rules:
     * - If the last character is a digit '9', it wraps to 'A'.
     * - If the last character is 'Z', the preceding part of the string is recursively incremented, and '0' is appended.
     * - If the string is empty, it returns "1".
     * - For all other characters, increments the last character by one.
     *
     * @param element The string whose value is to be incremented. Must not be null.
     * @return The incremented string value. If the input string is empty, returns "1".
     */
    public static String increment(@NonNull final String element) {
        if (element.isEmpty()) return "1";

        String firstPart = element.substring(0, element.length()-1);
        char lastChar = element.charAt(element.length()-1);
        if (lastChar == '9') return firstPart + 'A';
        if (lastChar == 'Z') return increment(firstPart) + '0';
        return firstPart + (char)(lastChar+1);
    }

    /**
     * Trims the input string to a specified maximum length, if necessary.
     * If the input string's length is less than or equal to the specified max length,
     * the string is returned unchanged. If the input string's length exceeds the max
     * length, it is truncated to that length.
     *
     * @param original The original input string to be processed. If null, the method returns null.
     * @param maxLength The maximum number of characters allowed in the resulting string.
     * @return The original string if its length is less than or equal to maxLength,
     *         otherwise a truncated version of the string up to maxLength characters.
     */
    public static String limitCharNumber(final String original, final int maxLength) {
        if (original == null || original.length() <= maxLength) return original;

        return original.substring(0, maxLength);
    }
}
