package de.neitzel.injection;

import de.neitzel.injection.testcomponents.injection.error.MultipleInjectConstructorsComponent;
import de.neitzel.injection.testcomponents.injection.error.NoInjectConstructorWithoutDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentScannerInjectionPlanTest {

    @Test
    void reportsMultipleInjectConstructorsAsError() {
        ComponentScanner scanner = new ComponentScanner("de.neitzel.injection.testcomponents.injection.error");
        assertFalse(scanner.getErrors().isEmpty());
        assertTrue(scanner.getErrors().stream().anyMatch(error -> error.contains(MultipleInjectConstructorsComponent.class.getName())));
    }

    @Test
    void reportsMissingUsableConstructorAsError() {
        ComponentScanner scanner = new ComponentScanner("de.neitzel.injection.testcomponents.injection.error");
        assertTrue(scanner.getErrors().stream().anyMatch(error -> error.contains(NoInjectConstructorWithoutDefault.class.getName())));
    }
}

