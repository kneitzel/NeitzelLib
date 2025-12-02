package de.neitzel.injection;

import lombok.Getter;

/**
 * Represents a component in a dependency injection framework.
 * <p>
 * A component is a unit of functionality that is managed by the framework, allowing
 * for controlled instantiation and lifecycle management. The component is associated
 * with a specific type and a scope. The scope determines whether the component is
 * instantiated as a singleton or as a prototype.
 */
@Getter
public class ComponentData {

    /**
     * Represents the type of the component being managed by the dependency injection framework.
     * <p>
     * This variable holds the class object corresponding to the specific type of the component.
     * It is used to identify and instantiate the component during the dependency injection process.
     * The type is immutable and is specified when the component is created.
     */
    private final Class<?> type;

    /**
     * Defines the lifecycle and instantiation rules for the associated component.
     * <p>
     * The {@code Scope} determines whether the component is created as a singleton
     * (a single shared instance) or as a prototype (a new instance for each request).
     * <p>
     * This variable is immutable and represents the specific {@code Scope} assigned
     * to the component, influencing its behavior within the dependency injection framework.
     */
    private final Scope scope;

    /**
     * Stores the instantiated object associated with the component.
     * <p>
     * This field holds the actual instance of the component's type within the dependency
     * injection framework. For components with a {@code SINGLETON} scope, this field is
     * set only once and shared across the entire application. For components with a
     * {@code PROTOTYPE} scope, this field may be null since a new instance is created
     * upon each request.
     */
    private Object instance;

    /**
     * Constructs a new Component instance with the specified type and scope.
     *
     * @param type  the class type of the component
     * @param scope the scope of the component, which determines its lifecycle
     */
    public ComponentData(Class<?> type, Scope scope) {
        this.type = type;
        this.scope = scope;
    }

    /**
     * Constructs a new ComponentData instance with the specified type and an initial instance.
     * <p>
     * This can be used to add Singletons manually without scanning for them.
     *
     * @param type     the class type of the component
     * @param instance the initial instance of the component
     */
    public ComponentData(Class<?> type, Object instance) {
        this.type = type;
        this.scope = Scope.SINGLETON;
        this.instance = instance;
    }

    /**
     * Sets the instance for this component if it is configured with a {@code SINGLETON} scope.
     * This method ensures that the instance is only set once for a {@code SINGLETON} component.
     * If an instance has already been set, it throws an {@link IllegalStateException}.
     *
     * @param instance the object instance to associate with this component
     * @throws IllegalStateException if an instance has already been set for this {@code SINGLETON} component
     */
    public void setInstance(Object instance) {
        if (scope != Scope.SINGLETON) {
            return;
        }
        if (this.instance != null) {
            throw new IllegalStateException("Instance already set for singleton: " + type.getName());
        }
        this.instance = instance;
    }
}
