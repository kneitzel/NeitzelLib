package de.neitzel.fx.example.view;

import jakarta.inject.Named;

/**
 * The suffix an {@link AgeConverter} writes in front of an age.
 * <p>
 * {@link Named} marks the class for scanning the same way {@link jakarta.inject.Singleton} does.
 * The container does not read the annotation value, and the instance is still cached.
 */
@Named
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
