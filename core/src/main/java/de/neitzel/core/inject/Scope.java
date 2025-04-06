package de.neitzel.core.inject;

/**
 * Represents the scope of a component in a dependency injection framework.
 * <p>
 * The scope determines the lifecycle and visibility of a component instance
 * within the framework. The available scope types are:
 * <p>
 * - {@code SINGLETON}: A single instance of the component is created and
 * shared across the entire application.
 * - {@code PROTOTYPE}: A new instance of the component is created
 * each time it is requested or injected.
 * <p>
 * This enumeration is typically used in conjunction with the {@code @Component}
 * annotation to specify the instantiation behavior of a component.
 */
public enum Scope {
    /**
     * Specifies that the component should be instantiated as a singleton within the
     * dependency injection framework. A single instance of the component is created
     * and shared across the entire application lifecycle, ensuring efficient reuse
     * of resources and consistent behavior for the component throughout the application.
     * <p>
     * This scope is typically applied to components where maintaining a single shared
     * instance is necessary or beneficial, such as managing shared state or providing
     * utility services.
     */
    SINGLETON,
    /**
     * Specifies that the component should be instantiated as a prototype within the
     * dependency injection framework. A new instance of the component is created
     * each time it is requested or injected, ensuring that no two requests or injections
     * share the same instance.
     * <p>
     * This scope is typically applied to components where maintaining unique instances
     * per request is necessary, such as request-specific data processing or handling
     * of transient state. It allows for complete isolation between multiple users
     * or operations requiring individual resources.
     */
    PROTOTYPE
}
