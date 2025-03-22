package de.neitzel.neitzelfx.injectfx;

import java.lang.reflect.Constructor;
import java.util.*;

/**
 * Manages the creation and storage of singleton instances for all @FXMLComponent classes.
 * It ensures that each component is instantiated only once and resolves dependencies recursively.
 */
public class FXMLComponentInstances {

    /** Map holding instances of all @FXMLComponent classes, indexed by class and its unique superclasses/interfaces. */
    private final Map<Class<?>, Object> instanceMap = new HashMap<>();

    /** The InjectableComponents instance that provides information about instantiable components. */
    private final InjectableComponentScanner injectableScanner;

    /**
     * Constructs an FXMLComponentInstances manager and initializes all component instances.
     *
     * @param injectableComponents The InjectableComponents instance containing resolved component types.
     */
    public FXMLComponentInstances(InjectableComponentScanner injectableScanner) {
        this.injectableScanner = injectableScanner;
        createAllInstances();
    }

    /**
     * Creates instances for all registered @FXMLComponent classes.
     */
    private void createAllInstances() {
        for (Class<?> componentClass : injectableScanner.getInstantiableComponents().keySet()) {
            getInstance(componentClass); // Ensures each component is instantiated once
        }
    }

    /**
     * Retrieves an instance of a given component class, creating it if necessary.
     *
     * @param componentClass The class for which an instance is needed.
     * @return An instance of the requested class.
     */
    public Object getInstance(Class<?> componentClass) {
        if (instanceMap.containsKey(componentClass)) {
            return instanceMap.get(componentClass);
        }

        Class<?> concreteClass = injectableScanner.getInstantiableComponents().get(componentClass);
        if (concreteClass == null) {
            throw new IllegalStateException("No concrete implementation found for: " + componentClass.getName());
        }

        Object instance = createInstance(concreteClass);
        registerInstance(concreteClass, instance);

        return instance;
    }

    /**
     * Creates an instance of the given class by selecting the best constructor.
     *
     * @param concreteClass The class to instantiate.
     * @return A new instance of the class.
     */
    private Object createInstance(Class<?> concreteClass) {
        try {
            Constructor<?> bestConstructor = findBestConstructor(concreteClass);
            if (bestConstructor == null) {
                throw new IllegalStateException("No suitable constructor found for: " + concreteClass.getName());
            }

            Class<?>[] paramTypes = bestConstructor.getParameterTypes();
            Object[] params = Arrays.stream(paramTypes)
                    .map(this::getInstance) // Recursively resolve dependencies
                    .toArray();

            return bestConstructor.newInstance(params);

        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate " + concreteClass.getName(), e);
        }
    }

    /**
     * Finds the best constructor for a given class by checking which one has resolvable parameters.
     *
     * @param concreteClass The class to analyze.
     * @return The best constructor or {@code null} if none can be used.
     */
    private Constructor<?> findBestConstructor(Class<?> concreteClass) {
        Constructor<?>[] constructors = concreteClass.getConstructors();

        // Prefer constructors with all parameters resolvable
        for (Constructor<?> constructor : constructors) {
            if (canUseConstructor(constructor)) {
                return constructor;
            }
        }

        return null;
    }

    /**
     * Checks whether a given constructor can be used by ensuring all parameters are known.
     *
     * @param constructor The constructor to check.
     * @return {@code true} if all parameters can be resolved, otherwise {@code false}.
     */
    private boolean canUseConstructor(Constructor<?> constructor) {
        return Arrays.stream(constructor.getParameterTypes())
                .allMatch(injectableScanner.getInstantiableComponents()::containsKey);
    }

    /**
     * Registers an instance in the map for all unique interfaces and superclasses it implements.
     *
     * @param concreteClass The concrete component class.
     * @param instance      The instance to store.
     */
    private void registerInstance(Class<?> concreteClass, Object instance) {
        instanceMap.put(concreteClass, instance);

        for (Map.Entry<Class<?>, Class<?>> entry : injectableScanner.getInstantiableComponents().entrySet()) {
            if (entry.getValue().equals(concreteClass)) {
                instanceMap.put(entry.getKey(), instance);
            }
        }
    }

    /**
     * Retrieves the instance map containing all component instances.
     *
     * @return A map of class types to their instances.
     */
    public Map<Class<?>, Object> getInstanceMap() {
        return instanceMap;
    }
}