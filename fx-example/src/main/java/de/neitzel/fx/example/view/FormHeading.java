package de.neitzel.fx.example.view;

import jakarta.inject.Singleton;

/**
 * The heading an address form shows above its fields.
 */
@Singleton
public class FormHeading {

    /**
     * Creates the heading used by the desk.
     */
    public FormHeading() {
    }

    /**
     * Returns the heading text.
     *
     * @return the heading
     */
    public String text() {
        return "Address";
    }
}
