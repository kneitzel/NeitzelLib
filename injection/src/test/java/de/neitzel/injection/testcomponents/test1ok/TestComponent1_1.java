package de.neitzel.injection.testcomponents.test1ok;

import jakarta.inject.Singleton;

/**
 * Singleton component in the root of the successful scan package.
 */
@Singleton
public class TestComponent1_1 extends SuperClass implements TestInterface1_2 {
    /**
     * Creates the component with no dependencies.
     */
    public TestComponent1_1() {
    }
}
