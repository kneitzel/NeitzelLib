package de.neitzel.core.util;

import java.util.Objects;

/**
 * Utility class providing helper methods for working with arrays.
 */
public class ArrayUtils {

    /**
     * Private constructor to prevent instantiation of the utility class.
     * This utility class is not meant to be instantiated, as it only provides
     * static utility methods for array-related operations.
     *
     * @throws UnsupportedOperationException always, to indicate that this class
     *                                       should not be instantiated.
     */
    private ArrayUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Checks if the given character array contains the specified character.
     *
     * @param array the character array to be searched
     * @param ch    the character to search for in the array
     * @return true if the character is found in the array, false otherwise
     */
    public static boolean contains(char[] array, char ch) {
        for (int index = 0; index < array.length; index++) {
            if (array[index] == ch) return true;
        }
        return false;
    }

    /**
     * Checks if the specified integer array contains the given integer value.
     *
     * @param array the array of integers to search
     * @param ch    the integer value to search for in the array
     * @return true if the array contains the specified integer value, false otherwise
     */
    public static boolean contains(int[] array, int ch) {
        for (int index = 0; index < array.length; index++) {
            if (array[index] == ch) return true;
        }
        return false;
    }

    /**
     * Checks if the specified long array contains the given long value.
     *
     * @param array the array of long values to search
     * @param ch    the long value to search for in the array
     * @return true if the array contains the specified long value, false otherwise
     */
    public static boolean contains(long[] array, long ch) {
        for (int index = 0; index < array.length; index++) {
            if (array[index] == ch) return true;
        }
        return false;
    }

    /**
     * Checks if the specified array contains the given element.
     *
     * @param array the array to be searched
     * @param ch    the element to search for in the array
     * @param <T>   the type of the elements in the array
     * @return true if the element is found in the array, false otherwise
     */
    public static <T> boolean contains(T[] array, T ch) {
        for (int index = 0; index < array.length; index++) {
            if (Objects.equals(array[index], ch)) return true;
        }
        return false;
    }
}
