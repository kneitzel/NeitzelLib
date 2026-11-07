package de.neitzel.injection.testcomponents.injection.success;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Fixture whose dependency is passed to the constructor.
 */
@Singleton
public class ConstructorInjectedService {
    /**
     * Dependency received through the constructor.
     */
    private final RootDependency rootDependency;

    /**
     * Stores the constructor dependency.
     *
     * @param rootDependency the dependency
     */
    @Inject
    public ConstructorInjectedService(RootDependency rootDependency) {
        this.rootDependency = rootDependency;
    }

    /**
     * Returns the constructor dependency.
     *
     * @return the dependency
     */
    public RootDependency getRootDependency() {
        return rootDependency;
    }
}

