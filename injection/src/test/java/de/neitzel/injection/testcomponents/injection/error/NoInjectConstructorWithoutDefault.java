package de.neitzel.injection.testcomponents.injection.error;

import jakarta.inject.Singleton;

/**
 * Fixture with no default constructor and no {@code @Inject} constructor.
 */
@Singleton
public class NoInjectConstructorWithoutDefault {
    /**
     * Accepts a string and is not marked for injection.
     *
     * @param firstDependency unused string dependency
     */
    public NoInjectConstructorWithoutDefault(String firstDependency) {
    }

    /**
     * Accepts an integer and is not marked for injection.
     *
     * @param secondDependency unused integer dependency
     */
    public NoInjectConstructorWithoutDefault(Integer secondDependency) {
    }
}
