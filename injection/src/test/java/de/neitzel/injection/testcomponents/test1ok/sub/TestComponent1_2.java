package de.neitzel.injection.testcomponents.test1ok.sub;

import de.neitzel.injection.testcomponents.test1ok.SuperClass;
import de.neitzel.injection.testcomponents.test1ok.TestInterface1_1;
import de.neitzel.injection.testcomponents.test1ok.TestInterface1_2;
import jakarta.inject.Singleton;

/**
 * Singleton component in a subpackage of the successful scan package.
 */
@Singleton
public class TestComponent1_2 extends SuperClass implements TestInterface1_1, TestInterface1_2 {
    /**
     * Creates the component with no dependencies.
     */
    public TestComponent1_2() {
    }
}
