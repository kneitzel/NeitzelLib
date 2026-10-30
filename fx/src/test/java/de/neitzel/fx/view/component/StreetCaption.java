package de.neitzel.fx.view.component;

import jakarta.inject.Singleton;

/**
 * Service whose text an address component must show, so a test can see that injection ran.
 */
@Singleton
public class StreetCaption {

    /**
     * Returns the prefix written on the street label.
     *
     * @return the prefix
     */
    public String prefix() {
        return "Lane";
    }
}
