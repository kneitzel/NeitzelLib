package de.neitzel.fx.view.fixture;


/**
 * Service whose text a converter must show, so a test can see that injection ran.
 */

public class AgeUnit {

    /**
     * Returns the prefix written in front of an age.
     *
     * @return the prefix
     */
    public String prefix() {
        return "years:";
    }
}
