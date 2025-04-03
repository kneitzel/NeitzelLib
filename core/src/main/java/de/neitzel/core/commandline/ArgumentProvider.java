package de.neitzel.core.commandline;

import java.util.Iterator;

/**
 * Provides one argument after the other.
 */
public class ArgumentProvider implements Iterator<String> {

    /**
     * List of Arguments (from command line)
     */
    private String[] arguments;

    /**
     * Current element we work on.
     */
    private int current = 0;

    /**
     * Creates a new instance of ArgumentProvider.
     * @param arguments List of Arguments.
     */
    public ArgumentProvider(final String[] arguments) {
        if (arguments == null) {
            this.arguments = new String[] {};
        } else {
            this.arguments = arguments;
        }
    }

    /**
     * Checks if more arguments are available.
     * @return True if more arguments are available else false.
     */
    public boolean hasNext() {
        return current < arguments.length;
    }

    /**
     * Checks if count more arguments are available.
     * @param count Number of arguments we want to get.
     * @return True if count more arguments are available else false.
     */
    public boolean hasNext(final int count) {
        return current + count <= arguments.length;
    }

    /**
     * Get the next token.
     * @return The next token or null if no more available.
     */
    public String next() {
        if (!hasNext()) return null;

        String result = arguments[current];
        current++;
        return result;
    }

    /**
     * Get the next token without removing it.
     * @return The next token or null if no more available.
     */
    public String peek() {
        if (!hasNext()) return null;
        return arguments[current];
    }
}
