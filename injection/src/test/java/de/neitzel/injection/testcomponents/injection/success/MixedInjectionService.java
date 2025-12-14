package de.neitzel.injection.testcomponents.injection.success;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class MixedInjectionService {
    private final ConstructorInjectedService constructorInjectedService;

    @Inject
    private FieldInjectedService fieldInjectedService;

    private SetterInjectedService setterInjectedService;

    @Inject
    public MixedInjectionService(ConstructorInjectedService constructorInjectedService) {
        this.constructorInjectedService = constructorInjectedService;
    }

    public ConstructorInjectedService getConstructorInjectedService() {
        return constructorInjectedService;
    }

    public FieldInjectedService getFieldInjectedService() {
        return fieldInjectedService;
    }

    public SetterInjectedService getSetterInjectedService() {
        return setterInjectedService;
    }

    @Inject
    public void setSetterInjectedService(SetterInjectedService setterInjectedService) {
        this.setterInjectedService = setterInjectedService;
    }
}
