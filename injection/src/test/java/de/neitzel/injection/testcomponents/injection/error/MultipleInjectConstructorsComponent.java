package de.neitzel.injection.testcomponents.injection.error;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Fixture with two {@code @Inject} constructors, which the scanner must reject.
 */
@Singleton
public class MultipleInjectConstructorsComponent {
    /**
     * Declares an injectable constructor that takes a string.
     *
     * @param firstDependency unused string dependency
     */
    @Inject
    public MultipleInjectConstructorsComponent(String firstDependency) {
    }

    /**
     * Declares a second injectable constructor that takes an integer.
     *
     * @param secondDependency unused integer dependency
     */
    @Inject
    public MultipleInjectConstructorsComponent(Integer secondDependency) {
    }
}
