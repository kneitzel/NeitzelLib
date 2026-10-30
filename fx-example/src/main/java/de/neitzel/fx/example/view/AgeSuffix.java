package de.neitzel.fx.example.view;

import jakarta.inject.Singleton;

/**
 * The suffix an {@link AgeConverter} writes in front of an age.
 */
@Singleton
public class AgeSuffix {

    /**
     * Creates the suffix used by the desk.
     */
    public AgeSuffix() {
    }

    /**
     * Returns the text written in front of the age.
     *
     * @return the suffix, including its trailing space
     */
    public String prefix() {
        return "years ";
    }
}
