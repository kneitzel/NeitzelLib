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

    @Test
    void createComponentDoesNotBecomeTheSingleton() {
        MixedInjectionService created = context.createComponent(MixedInjectionService.class);
        MixedInjectionService singleton = context.getComponent(MixedInjectionService.class);
        assertNotSame(created, singleton);
        assertSame(singleton, context.getComponent(MixedInjectionService.class));
        assertSame(created.getConstructorInjectedService(), singleton.getConstructorInjectedService());
    }
}

