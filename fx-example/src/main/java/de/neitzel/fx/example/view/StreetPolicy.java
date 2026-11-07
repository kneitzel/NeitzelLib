package de.neitzel.fx.example.view;

import jakarta.inject.Singleton;

/**
 * The rule a {@link StreetConverter} applies when a street is empty.
 */
@Singleton
public class StreetPolicy {

    /**
     * Creates the policy used by the desk.
     */
    public StreetPolicy() {
    }

    /**
     * Returns the notice text for an empty street.
     *
     * @return the notice text
     */
    public String message() {
        return "Street is required";
    }
}
