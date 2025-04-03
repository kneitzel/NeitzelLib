package de.neitzel.core.util;

/**
 * Utility Class with static methods about arrays.
 */
public class ArrayUtils {

    /**
     * Checks if a given char is inside an array of chars.
     * @param array Array of chars to seach in.
     * @param ch Character to search.
     * @return true if character was found, else false.
     */
    public static boolean contains(char[] array, char ch) {
        for(int index=0; index < array.length; index++) {
            if (array[index] == ch) return true;
        }
        return false;
    }
}
