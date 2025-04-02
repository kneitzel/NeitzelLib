package de.neitzel.fx.injectfx;

import de.neitzel.fx.injectfx.testcomponents.test1ok.SuperClass;
import de.neitzel.fx.injectfx.testcomponents.test1ok.TestComponent1_1;
import de.neitzel.fx.injectfx.testcomponents.test1ok.TestInterface1_1;
import de.neitzel.fx.injectfx.testcomponents.test1ok.TestInterface1_2;
import de.neitzel.fx.injectfx.testcomponents.test1ok.sub.TestComponent1_2;
import de.neitzel.inject.InjectableComponentScanner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InjectableComponentScannerTest {

    /**
     * Tests loading of multiple FXMLComponents including sub packages.
     */
    @Test
    void testLoadComponents() {
        InjectableComponentScanner scanner = new InjectableComponentScanner("de.neitzel.injectfx.testcomponents.test1ok");
        var instantiableComponents = scanner.getInstantiableComponents();
        var nonUniqueTypes = scanner.getNotUniqueTypes();

        assertAll(
                () -> assertNotNull(instantiableComponents),
                () -> assertEquals(3, instantiableComponents.size()),
                () -> assertTrue(instantiableComponents.containsKey(TestComponent1_1.class)),
                () -> assertTrue(instantiableComponents.containsKey(TestComponent1_2.class)),
                () -> assertTrue(instantiableComponents.containsKey(TestInterface1_1.class)),
                () -> assertTrue(nonUniqueTypes.contains(SuperClass.class)),
                () -> assertTrue(nonUniqueTypes.contains(TestInterface1_2.class)),
                () -> assertTrue(scanner.getErrors().isEmpty())
        );
    }

    /**
     * Tests failing to load a FXMLComponent which has an unknwown parameter.
     */
    @Test
    void testComponentsFailWithUnknownParameters() {
        InjectableComponentScanner scanner = new InjectableComponentScanner("de.neitzel.injectfx.testcomponents.test2fail");
        var instantiableComponents = scanner.getInstantiableComponents();
        var nonUniqueTypes = scanner.getNotUniqueTypes();

        assertAll(
                () -> assertNotNull(instantiableComponents),
                () -> assertEquals(0, instantiableComponents.size()),
                () -> assertFalse(scanner.getErrors().isEmpty())
        );
    }
}