package de.neitzel.core.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for working with Enum types, providing methods to parse and generate
 * patterns for Enum flags.
 */
public class EnumUtil {
    /**
     * Private constructor to prevent instantiation of the utility class.
     * This utility class is not meant to be instantiated, as it only provides
     * static utility methods for array-related operations.
     *
     * @throws UnsupportedOperationException always, to indicate that this class
     *                                        should not be instantiated.
     */
    private EnumUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Generates a regular expression pattern that matches all possible case-insensitive representations
     * of the enum constants within the specified Enum class. The pattern also includes optional delimiters
     * such as commas, spaces, and empty values, enabling the matching of lists or sequences of flag values.
     *
     * @param <T> The type of the Enum.
     * @param clazz The Enum class for which the regular expression is to be generated.
     * @return A String containing the regular expression pattern for matching the Enum's flag values.
     */
    public static <T extends Enum<T>> String getFlagRegEx(Class<T> clazz) {
        StringBuilder result = new StringBuilder();
        result.append("(|,|\\s");

        for (T flag: clazz.getEnumConstants()) {
            result.append("|");
            for(char ch: flag.toString().toUpperCase().toCharArray()) {
                if (Character.isAlphabetic(ch)) {
                    result.append("[");
                    result.append(ch);
                    result.append(Character.toLowerCase(ch));
                    result.append("]");
                } else {
                    result.append(ch);
                }
            }
        }
        result.append(")*");
        return result.toString();
    }

    /**
     * Parses a delimited string containing Enum constant names into a list of Enum constants.
     *
     * This method takes an Enum class and a string of flags, splits the string by commas
     * or whitespace, and converts each substring into an Enum constant of the given class.
     * The flag names are case-insensitive and will be converted to uppercase before matching
     * to the Enum constants.
     *
     * @param <T> The type of the Enum.
     * @param clazz The Enum class to which the flags should be parsed.
     * @param flags A string containing the delimited list of flag names to parse.
     *              Individual names can be separated by commas or whitespace.
     * @return A list of Enum constants parsed from the input string. If the input string
     *         is null or empty, an empty list is returned.
     * @throws IllegalArgumentException if any of the flag names do not match the constants in the given Enum class.
     * @throws NullPointerException if the clazz parameter is null.
     */
    public static <T extends Enum<T>> List<T> parseFlags(final Class<T> clazz, final String flags) {
        List<T> result = new ArrayList<>();
        if (flags != null) {
            for (String flag: flags.split("[,\\s]")) {
                if (!flag.isEmpty()) result.add(T.valueOf(clazz, flag.toUpperCase()));
            }
        }
        return result;
    }
}
