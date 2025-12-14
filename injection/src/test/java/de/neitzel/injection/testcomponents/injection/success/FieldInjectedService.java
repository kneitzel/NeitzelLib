package de.neitzel.injection.testcomponents.injection.success;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class FieldInjectedService {
    @Inject
    private RootDependency rootDependency;

    public RootDependency getRootDependency() {
        return rootDependency;
    }
}
