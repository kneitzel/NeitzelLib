package de.neitzel.injection.testcomponents.injection.error;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class MultipleInjectConstructorsComponent {
    @Inject
    public MultipleInjectConstructorsComponent(String firstDependency) {
    }

    @Inject
    public MultipleInjectConstructorsComponent(Integer secondDependency) {
    }
}
