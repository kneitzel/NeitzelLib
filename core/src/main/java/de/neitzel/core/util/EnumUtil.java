package de.neitzel.core.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Enumeration Utilities
 */
public class EnumUtil {
    /**
     * Creates a regular expression to match a comma separated list of Flags.
     * @return Regular expression to match flags.
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
     * Parse a list of comma separated flags into a List of RequestFlags.
     * @param flags String with comma separated flags.
     * @return List of RequestFlags.
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
