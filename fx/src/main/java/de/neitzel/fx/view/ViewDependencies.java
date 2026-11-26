package de.neitzel.fx.view;

import java.util.Optional;

/** Supplies a View's objects from the application's dependency injection configuration. */
public interface ViewDependencies {

    /** Checks whether the exact FXML type is a Component, without creating an instance. */
    boolean isComponent(Class<?> type);

    /**
     * Looks up an application-owned service such as a Converter or Localizer.
     * Report lookup and creation failures as exceptions. An empty result means the service is absent.
     */
    <T> Optional<T> findService(Class<T> type);

    /**
     * Creates a fresh Controller or Component and supplies its release action.
     * The adapter owns any partial allocations until it returns the object.
     */
    <T> ViewObject<T> createViewObject(Class<T> type);
}
