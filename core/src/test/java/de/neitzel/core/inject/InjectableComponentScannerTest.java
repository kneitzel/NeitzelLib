package de.neitzel.core.inject;

import de.neitzel.core.inject.testcomponents.test1ok.SuperClass;
import de.neitzel.core.inject.testcomponents.test1ok.TestComponent1_1;
import de.neitzel.core.inject.testcomponents.test1ok.TestInterface1_1;
import de.neitzel.core.inject.testcomponents.test1ok.TestInterface1_2;
import de.neitzel.core.inject.testcomponents.test1ok.sub.TestComponent1_2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InjectableComponentScannerTest {

    /**
     * Tests loading of multiple FXMLComponents including sub packages.
     */
    @Test
    void testLoadComponents() {
        ComponentScanner scanner = new ComponentScanner("de.neitzel.core.inject.testcomponents.test1ok");
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
        ComponentScanner scanner = new ComponentScanner("de.neitzel.core.inject.testcomponents.test2fail");
        var instantiableComponents = scanner.getInstantiableComponents();
        var nonUniqueTypes = scanner.getNotUniqueTypes();

        assertAll(
                () -> assertNotNull(instantiableComponents),
                () -> assertEquals(0, instantiableComponents.size()),
                () -> assertFalse(scanner.getErrors().isEmpty())
        );
    }
}