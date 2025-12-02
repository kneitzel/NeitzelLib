package de.neitzel.injection;

import de.neitzel.injection.annotation.Config;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

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
        ComponentData data = components.get(type);
        if (data == null) {
            throw new IllegalArgumentException("No component found for type: " + type.getName());
        }

        Scope scope = data.getScope();
        Object instance = data.getInstance();

        if (scope == Scope.SINGLETON && instance != null) {
            return (T) instance;
        }

        try {
            Constructor<?>[] constructors = data.getType().getConstructors();
            for (Constructor<?> constructor : constructors) {
                if (canBeInstantiated(constructor, components)) {
                    Class<?>[] paramTypes = constructor.getParameterTypes();
                    Object[] parameters = new Object[paramTypes.length];
                    for (int i = 0; i < paramTypes.length; i++) {
                        parameters[i] = getComponent(paramTypes[i]);
                    }
                    instance = constructor.newInstance(parameters);
                    if (scope == Scope.SINGLETON) {
                        data.setInstance(instance);
                    }
                    return (T) instance;
                }
            }

            throw new IllegalStateException("Kein passender Konstruktor gefunden für " + type.getName());
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Erstellen der Instanz für " + type.getName(), e);
        }
    }

    /**
     * Determines if a given constructor can be instantiated using the provided parameter map.
     * <p>
     * The method evaluates whether every parameter type required by the constructor
     * is available in the given parameter map. If all parameter types are present,
     * the constructor is considered instantiable.
     *
     * @param constructor  the constructor to check for instantiation feasibility
     * @param parameterMap a map containing available parameter types and their associated component data
     * @return true if all parameter types required by the constructor are contained in the parameter map, false otherwise
     */
    private boolean canBeInstantiated(Constructor<?> constructor, Map<Class<?>, ComponentData> parameterMap) {
        return Stream.of(constructor.getParameterTypes()).allMatch(parameterMap::containsKey);
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
