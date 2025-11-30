package de.neitzel.core.commandline;

import java.util.Iterator;

/**
 * The ArgumentProvider class is a helper utility for iterating over an array
 * of command-line arguments. It provides methods to check for available arguments
 * and retrieve them in sequence.
 * <p>
 * This class is designed to work seamlessly with a command-line parser and handle
 * tokenized inputs efficiently. It implements the {@link Iterator} interface for ease
 * of iteration and provides convenience methods for peeking ahead.
 */
public class ArgumentProvider implements Iterator<String> {

    /**
     * A String array representing the command-line arguments provided to the application.
     * This array holds each tokenized element of the command-line input for further parsing
     * and processing by the application logic. It is used within the {@code ArgumentProvider}
     * class to iterate through and retrieve arguments systematically.
     */
    private final String[] arguments;

    /**
     * Tracks the current position within an array of arguments.
     * This variable is used to index into the arguments array, enabling sequential access
     * to command-line tokens. It is incremented as arguments are processed or retrieved
     * using methods such as {@code next()} or {@code peek()} in the {@link ArgumentProvider}
     * class. Its value starts at 0 and increases until it reaches the length of the provided
     * arguments array, at which point iteration ends.
     */
    private int current = 0;

    /**
     * Creates an instance of the ArgumentProvider class to iterate over the given array of arguments.
     * If the provided array is null, it initializes the arguments with an empty array.
     *
     * @param arguments The array of command-line arguments to be iterated over.
     *                  If null, it defaults to an empty array.
     */
    public ArgumentProvider(final String[] arguments) {
        if (arguments == null) {
            this.arguments = new String[]{};
        } else {
            this.arguments = arguments;
        }
    }

    /**
     * Determines if the specified number of additional arguments are available in the collection.
     *
     * @param count the number of additional arguments to check for availability
     * @return true if there are at least the specified number of arguments available, otherwise false
     */
    public boolean hasNext(final int count) {
        return current + count <= arguments.length;
    }

    /**
     * Returns the next argument in the sequence without advancing the iterator.
     * If there are no more arguments available, this method returns null.
     *
     * @return the next argument in the sequence or null if no arguments are available
     */
    public String peek() {
        if (!hasNext()) return null;
        return arguments[current];
    }

    /**
     * Checks if there are more arguments available to iterate over.
     *
     * @return true if there are additional arguments available; false otherwise.
     */
    public boolean hasNext() {
        return current < arguments.length;
    }

    /**
     * Retrieves the next argument in the sequence.
     * If no more arguments are available, returns {@code null}.
     *
     * @return the next argument as a {@code String}, or {@code null} if no further arguments are available
     */
    public String next() {
        if (!hasNext()) return null;

        String result = arguments[current];
        current++;
        return result;
    }
}
