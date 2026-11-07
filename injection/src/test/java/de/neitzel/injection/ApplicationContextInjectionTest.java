package de.neitzel.injection;

import de.neitzel.injection.testcomponents.injection.success.ConstructorInjectedService;
import de.neitzel.injection.testcomponents.injection.success.FieldInjectedService;
import de.neitzel.injection.testcomponents.injection.success.MixedInjectionService;
import de.neitzel.injection.testcomponents.injection.success.RootDependency;
import de.neitzel.injection.testcomponents.injection.success.SetterInjectedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Checks that {@link ApplicationContext} injects constructor, field, and setter dependencies.
 */
class ApplicationContextInjectionTest {

    /**
     * Context scanned from the successful injection fixtures.
     */
    private ApplicationContext context;

    /**
     * Scans the successful injection fixtures before each test.
     */
    @BeforeEach
    void setUp() {
        context = new ApplicationContext("de.neitzel.injection.testcomponents.injection.success");
    }

    /**
     * Resolves a service whose dependency is passed to the constructor.
     */
    @Test
    void resolvesConstructorInjection() {
        ConstructorInjectedService service = context.getComponent(ConstructorInjectedService.class);
        assertNotNull(service.getRootDependency());
    }

    /**
     * Resolves a service whose dependency is injected into a field.
     */
    @Test
    void resolvesFieldInjection() {
        FieldInjectedService service = context.getComponent(FieldInjectedService.class);
        assertNotNull(service.getRootDependency());
    }

    /**
     * Resolves a service whose dependency is injected through a setter.
     */
    @Test
    void resolvesSetterInjection() {
        SetterInjectedService service = context.getComponent(SetterInjectedService.class);
        assertNotNull(service.getRootDependency());
    }

    /**
     * Resolves a mixed graph and reuses the same singleton dependency throughout.
     */
    @Test
    void resolvesMixedInjectionGraphAndSingletonReuse() {
        MixedInjectionService service = context.getComponent(MixedInjectionService.class);
        RootDependency rootDependency = context.getComponent(RootDependency.class);
        assertSame(rootDependency, service.getConstructorInjectedService().getRootDependency());
        assertSame(rootDependency, service.getFieldInjectedService().getRootDependency());
        assertSame(rootDependency, service.getSetterInjectedService().getRootDependency());
    }

    /**
     * Creates a new instance while singleton dependencies stay the instances already in the context.
     */
    @Test
    void createComponentInjectsANewInstanceAndKeepsTheSingleton() {
        MixedInjectionService singleton = context.getComponent(MixedInjectionService.class);
        MixedInjectionService created = context.createComponent(MixedInjectionService.class);
        RootDependency root = context.getComponent(RootDependency.class);
        assertNotSame(singleton, created);
        assertSame(singleton, context.getComponent(MixedInjectionService.class));
        assertSame(root, created.getConstructorInjectedService().getRootDependency());
        assertSame(root, created.getFieldInjectedService().getRootDependency());
        assertSame(root, created.getSetterInjectedService().getRootDependency());
        assertSame(singleton.getConstructorInjectedService(), created.getConstructorInjectedService());
        assertSame(singleton.getFieldInjectedService(), created.getFieldInjectedService());
        assertSame(singleton.getSetterInjectedService(), created.getSetterInjectedService());
    }

    /**
     * Keeps a created instance out of the singleton cache.
     */
    @Test
    void createComponentDoesNotBecomeTheSingleton() {
        MixedInjectionService created = context.createComponent(MixedInjectionService.class);
        MixedInjectionService singleton = context.getComponent(MixedInjectionService.class);
        assertNotSame(created, singleton);
        assertSame(singleton, context.getComponent(MixedInjectionService.class));
        assertSame(created.getConstructorInjectedService(), singleton.getConstructorInjectedService());
    }
}

