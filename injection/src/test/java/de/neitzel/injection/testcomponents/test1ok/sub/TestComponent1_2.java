package de.neitzel.injection.testcomponents.test1ok.sub;

import de.neitzel.injection.annotation.Component;
import de.neitzel.injection.testcomponents.test1ok.SuperClass;
import de.neitzel.injection.testcomponents.test1ok.TestInterface1_1;
import de.neitzel.injection.testcomponents.test1ok.TestInterface1_2;

@Component
public class TestComponent1_2 extends SuperClass implements TestInterface1_1, TestInterface1_2 {
    public TestComponent1_2() {
    }
}
