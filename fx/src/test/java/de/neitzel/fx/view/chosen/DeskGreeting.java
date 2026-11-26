package de.neitzel.fx.view.chosen;


/**
 * Service whose text a controller must show, so a test can see that injection ran.
 */

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
