package de.neitzel.injection.testcomponents.injection.success;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Fixture whose dependency is injected through a setter.
 */
@Singleton
public class SetterInjectedService {
    /**
     * Dependency injected through the setter.
     */
    private RootDependency rootDependency;

    /**
     * Returns the injected dependency.
     *
     * @return the dependency
     */
    public RootDependency getRootDependency() {
        return rootDependency;
    }

    /**
     * Stores the injected dependency.
     *
     * @param rootDependency the dependency
     */
    @Inject
    public void setRootDependency(RootDependency rootDependency) {
        this.rootDependency = rootDependency;
    }
}
