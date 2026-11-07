package de.neitzel.injection.testcomponents.test2fail;

import jakarta.inject.Singleton;

/**
 * TestComponent1 that should fail.
 */
@Singleton
public class TestComponent2_1 {
    /**
     * Requires a string the scanner cannot resolve.
     *
     * @param test unresolved constructor dependency
     */
    public TestComponent2_1(String test) {
    }
}
