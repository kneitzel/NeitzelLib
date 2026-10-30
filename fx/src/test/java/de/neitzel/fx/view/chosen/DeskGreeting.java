package de.neitzel.fx.view.chosen;

import jakarta.inject.Singleton;

/**
 * Service whose text a Controller must show, so a test can see that injection ran.
 */
@Singleton
public class DeskGreeting {

    /**
     * Returns the greeting written on the label.
     *
     * @return the greeting
     */
    public String text() {
        return "from-the-desk";
    }
}
