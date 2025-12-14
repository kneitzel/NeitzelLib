package de.neitzel.injection.testcomponents.injection.error;

import jakarta.inject.Singleton;

@Singleton
public class NoInjectConstructorWithoutDefault {
    public NoInjectConstructorWithoutDefault(String firstDependency) {
    }

    public NoInjectConstructorWithoutDefault(Integer secondDependency) {
    }
}
