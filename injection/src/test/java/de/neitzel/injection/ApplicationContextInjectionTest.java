package de.neitzel.injection;

import de.neitzel.injection.testcomponents.injection.success.ConstructorInjectedService;
import de.neitzel.injection.testcomponents.injection.success.FieldInjectedService;
import de.neitzel.injection.testcomponents.injection.success.MixedInjectionService;
import de.neitzel.injection.testcomponents.injection.success.RootDependency;
import de.neitzel.injection.testcomponents.injection.success.SetterInjectedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ApplicationContextInjectionTest {

    private ApplicationContext context;

    @BeforeEach
    void setUp() {
        context = new ApplicationContext("de.neitzel.injection.testcomponents.injection.success");
    }

    @Test
    void resolvesConstructorInjection() {
        ConstructorInjectedService service = context.getComponent(ConstructorInjectedService.class);
        assertNotNull(service.getRootDependency());
    }

    @Test
    void resolvesFieldInjection() {
        FieldInjectedService service = context.getComponent(FieldInjectedService.class);
        assertNotNull(service.getRootDependency());
    }

    @Test
    void resolvesSetterInjection() {
        SetterInjectedService service = context.getComponent(SetterInjectedService.class);
        assertNotNull(service.getRootDependency());
    }

    @Test
    void resolvesMixedInjectionGraphAndSingletonReuse() {
        MixedInjectionService service = context.getComponent(MixedInjectionService.class);
        RootDependency rootDependency = context.getComponent(RootDependency.class);
        assertSame(rootDependency, service.getConstructorInjectedService().getRootDependency());
        assertSame(rootDependency, service.getFieldInjectedService().getRootDependency());
        assertSame(rootDependency, service.getSetterInjectedService().getRootDependency());
    }
}

