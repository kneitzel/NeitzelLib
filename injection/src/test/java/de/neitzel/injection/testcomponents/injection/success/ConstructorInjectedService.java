package de.neitzel.injection.testcomponents.injection.success;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ConstructorInjectedService {
    private final RootDependency rootDependency;

    @Inject
    public ConstructorInjectedService(RootDependency rootDependency) {
        this.rootDependency = rootDependency;
    }

    public RootDependency getRootDependency() {
        return rootDependency;
    }
}

