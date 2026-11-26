package de.neitzel.injection;

import de.neitzel.injection.annotation.Config;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the context of the application and serves as the foundation of a dependency
 * injection framework.
 * <p>
 * The {@code ApplicationContext} is responsible for scanning, instantiating, and managing
 * the lifecycle of components. Components are identified through the specified base package
 * (or derived from a configuration class) and instantiated based on their dependency
 * requirements and scope. The framework supports constructor-based dependency injection
 * for resolving and creating components.
 */
public class ApplicationContext {
    /**
     * Components this context can create, keyed by the registered type.
     * The component scan fills the map, and manual singleton registration updates it.
     */
    private final Map<Class<?>, ComponentData> components;

    /**
     * Erstellt einen ApplicationContext auf Basis eines expliziten Package-Namens.
     *
     * @param basePackage das Basis-Package, das nach Komponenten durchsucht werden soll
     */
    public ApplicationContext(String basePackage) {
        ComponentScanner scanner = new ComponentScanner(basePackage);
        this.components = scanner.getInstantiableComponents();
    }

    /**
     * Erstellt einen ApplicationContext auf Basis einer Startklasse.
     * Das zu scannende Package wird aus der @Config Annotation gelesen (basePackage).
     * Wenn keine Annotation vorhanden ist oder kein basePackage gesetzt wurde, wird
     * das Package der übergebenen Klasse verwendet.
     *
     * @param configClass Klasse mit oder ohne @Config Annotation
     */
    public ApplicationContext(Class<?> configClass) {
        String basePackage;
        Config config = configClass.getAnnotation(Config.class);
        if (config != null && !config.basePackage().isEmpty()) {
            basePackage = config.basePackage();
        } else {
            basePackage = configClass.getPackageName();
        }
        ComponentScanner scanner = new ComponentScanner(basePackage);
        this.components = scanner.getInstantiableComponents();
    }

    /**
     * Retrieves an instance of the specified component type from the application context.
     * <p>
     * This method uses dependency injection to construct the component if it is not already
     * a singleton instance. The component's type and scope are determined by the framework,
     * and the appropriate initialization and lifecycle management are performed.
     *
     * @param <T>  the type of the component to retrieve
     * @param type the {@code Class} object representing the type of the component
     * @return an instance of the requested component type
     * @throws IllegalArgumentException if no component is found for the specified type
     * @throws IllegalStateException    if no suitable constructor is found for the component
     * @throws RuntimeException         if any error occurs during instantiation
     */
    @SuppressWarnings("unchecked")
    public <T> T getComponent(Class<? extends T> type) {
        ComponentData data = componentData(type);
        Scope scope = data.getScope();
        Object instance = data.getInstance();

        if (scope == Scope.SINGLETON && instance != null) {
            return (T) instance;
        }

        instance = createInjected(data);
        if (scope == Scope.SINGLETON) {
            data.setInstance(instance);
        }
        return (T) instance;
    }

    /**
     * Loads a dependency whose type was read by reflection.
     *
     * @param type the dependency type
     * @return the component for that type
     */
    private Object component(Class<?> type) {
        return getComponent(type.asSubclass(Object.class));
    }

    /**
     * Creates a new injected instance without replacing a cached singleton.
     * <p>
     * Dependencies of the new instance are the objects {@link #getComponent} returns, so a
     * singleton dependency is still shared. The new instance itself is not stored as the
     * singleton for {@code type}.
     *
     * @param <T>  the type of the component to create
     * @param type the {@code Class} object representing the type of the component
     * @return a new instance of the requested component type
     * @throws IllegalArgumentException if no component is found for the specified type
     * @throws IllegalStateException    if no suitable constructor is found for the component
     * @throws RuntimeException         if any error occurs during instantiation
     */
    @SuppressWarnings("unchecked")
    public <T> T createComponent(Class<? extends T> type) {
        return (T) createInjected(componentData(type));
    }

    /**
     * Returns the scanned data for a component type.
     *
     * @param type the component type
     * @return the component data
     * @throws IllegalArgumentException if no component is found for the specified type
     */
    private ComponentData componentData(Class<?> type) {
        ComponentData data = components.get(type);
        if (data == null) {
            throw new MissingComponentException(type);
        }
        return data;
    }

    /**
     * Constructs a component and injects its dependencies.
     * <p>
     * The instance is not stored. {@link #getComponent} decides whether a singleton caches it.
     *
     * @param data the component to construct
     * @return the new instance
     */
    private Object createInjected(ComponentData data) {
        try {
            Constructor<?> injectionConstructor = data.getInjectionConstructor();
            if (injectionConstructor == null) {
                throw new IllegalStateException("No injection constructor resolved for " + data.getType().getName());
            }

            Class<?>[] paramTypes = injectionConstructor.getParameterTypes();
            Object[] parameters = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                parameters[i] = component(paramTypes[i]);
            }

            Object instance = injectionConstructor.newInstance(parameters);

            for (Field field : data.getInjectableFields()) {
                field.setAccessible(true);
                field.set(instance, component(field.getType()));
            }

            for (Method setter : data.getInjectableSetters()) {
                setter.setAccessible(true);
                Class<?> dependencyType = setter.getParameterTypes()[0];
                setter.invoke(instance, component(dependencyType));
            }

            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Erstellen der Instanz für " + data.getType().getName(), e);
        }
    }

    /**
     * Registers a singleton instance of a specific type into the application context.
     * <p>
     * This method allows manual addition of singleton components to the context by
     * providing a concrete instance of the required type. If the type is already mapped
     * to a different instance or type within the context, an {@link IllegalStateException}
     * is thrown to prevent replacement of an existing singleton.
     *
     * @param <T>      the type of the component to register
     * @param type     the class type of the component being added
     * @param instance the singleton instance of the component to register
     * @throws IllegalStateException if the type is already registered with a different instance
     */
    public <T> void addSingleton(Class<? extends T> type, T instance) {
        if (components.get(type) == null ||
                !Objects.equals(components.get(type).getType().getName(), type.getName())) {

            components.put(type, new ComponentData(type, instance));
        } else {
            throw new IllegalStateException("Type cannot be replaced: " + type.getName());
        }
    }
}
