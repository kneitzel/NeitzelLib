package de.neitzel.injection.testcomponents.injection.success;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class SetterInjectedService {
    private RootDependency rootDependency;

    public RootDependency getRootDependency() {
        return rootDependency;
    }

    @Inject
    public void setRootDependency(RootDependency rootDependency) {
        this.rootDependency = rootDependency;
    }
}
