package de.neitzel.injection.testcomponents.injection.success;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Fixture whose dependency is injected into a field.
 */
@Singleton
public class FieldInjectedService {
    /**
     * Dependency injected into the field.
     */
    @Inject
    private RootDependency rootDependency;

    /**
     * Returns the injected dependency.
     *
     * @return the dependency
     */
    public RootDependency getRootDependency() {
        return rootDependency;
    }
}
