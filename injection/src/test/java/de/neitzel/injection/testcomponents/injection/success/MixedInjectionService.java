package de.neitzel.injection.testcomponents.injection.success;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Fixture that receives dependencies by constructor, field, and setter.
 */
@Singleton
public class MixedInjectionService {
    /**
     * Dependency received through the constructor.
     */
    private final ConstructorInjectedService constructorInjectedService;

    /**
     * Dependency injected into the field.
     */
    @Inject
    private FieldInjectedService fieldInjectedService;

    /**
     * Dependency injected through the setter.
     */
    private SetterInjectedService setterInjectedService;

    /**
     * Stores the constructor dependency.
     *
     * @param constructorInjectedService the constructor dependency
     */
    @Inject
    public MixedInjectionService(ConstructorInjectedService constructorInjectedService) {
        this.constructorInjectedService = constructorInjectedService;
    }

    /**
     * Returns the constructor dependency.
     *
     * @return the constructor dependency
     */
    public ConstructorInjectedService getConstructorInjectedService() {
        return constructorInjectedService;
    }

    /**
     * Returns the field dependency.
     *
     * @return the field dependency
     */
    public FieldInjectedService getFieldInjectedService() {
        return fieldInjectedService;
    }

    /**
     * Returns the setter dependency.
     *
     * @return the setter dependency
     */
    public SetterInjectedService getSetterInjectedService() {
        return setterInjectedService;
    }

    /**
     * Stores the setter dependency.
     *
     * @param setterInjectedService the setter dependency
     */
    @Inject
    public void setSetterInjectedService(SetterInjectedService setterInjectedService) {
        this.setterInjectedService = setterInjectedService;
    }
}
