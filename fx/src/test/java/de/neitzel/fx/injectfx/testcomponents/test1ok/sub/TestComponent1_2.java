package de.neitzel.fx.injectfx.testcomponents.test1ok.sub;

import de.neitzel.fx.injectfx.testcomponents.test1ok.SuperClass;
import de.neitzel.fx.injectfx.testcomponents.test1ok.TestInterface1_1;
import de.neitzel.fx.injectfx.testcomponents.test1ok.TestInterface1_2;
import de.neitzel.inject.annotation.Component;

@Component
public class TestComponent1_2 extends SuperClass implements TestInterface1_1, TestInterface1_2 {
    public TestComponent1_2() {
    }
}
