package de.neitzel.core.inject;

import de.neitzel.core.inject.annotation.Component;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.stream.Collectors;

/**
 * InjectableComponents scans a package for classes annotated with {@link Component}.
 * It determines which components can be instantiated and manages type mappings for dependency injection.
 */
public class InjectableComponentScanner {

    /** Set of all detected @FXMLComponent classes within the given package. */
    private final Set<Class<?>> fxmlComponents = new HashSet<>();

    /** Set of all superclasses and interfaces that are implemented by multiple @FXMLComponent classes. */
    private final Set<Class<?>> notUniqueTypes = new HashSet<>();

    /** Map of unique superclasses/interfaces to a single corresponding @FXMLComponent class. */
    private final Map<Class<?>, Class<?>> uniqueTypeToComponent = new HashMap<>();

    /** Map of instantiable @FXMLComponent classes and their corresponding interfaces/superclasses (if unique). */
    private final Map<Class<?>, Class<?>> instantiableComponents = new HashMap<>();

    /** List of error messages generated when resolving component instantiability. */
    private final List<String> errors = new ArrayList<>();

    /**
     * Constructs an InjectableComponents instance and scans the given package for @FXMLComponent classes.
     *
     * @param basePackage The base package to scan for @FXMLComponent classes.
     */
    public InjectableComponentScanner(String basePackage) {
        scanForComponents(basePackage);
        analyzeComponentTypes();
        resolveInstantiableComponents();
    }

    /**
     * Scans the specified package for classes annotated with @FXMLComponent.
     *
     * @param basePackage The package to scan.
     */
    private void scanForComponents(String basePackage) {
        Reflections reflections = new Reflections(basePackage);
        fxmlComponents.addAll(reflections.getTypesAnnotatedWith(Component.class));
    }

    /**
     * Analyzes the collected @FXMLComponent classes to determine unique and non-unique superclasses/interfaces.
     */
    private void analyzeComponentTypes() {
        Map<Class<?>, List<Class<?>>> superTypesMap = new HashMap<>();

        for (Class<?> component : fxmlComponents) {
            Set<Class<?>> allSuperTypes = getAllSuperTypes(component);

            for (Class<?> superType : allSuperTypes) {
                superTypesMap.computeIfAbsent(superType, k -> new ArrayList<>()).add(component);
            }
        }

        for (Map.Entry<Class<?>, List<Class<?>>> entry : superTypesMap.entrySet()) {
            Class<?> superType = entry.getKey();
            List<Class<?>> implementations = entry.getValue();

            if (implementations.size() > 1) {
                notUniqueTypes.add(superType);
            } else {
                uniqueTypeToComponent.put(superType, implementations.get(0));
            }
        }
    }

    /**
     * Determines which @FXMLComponent classes can be instantiated based on known dependencies.
     * It registers valid components and collects errors for components that cannot be instantiated.
     */
    private void resolveInstantiableComponents() {
        Set<Class<?>> resolved = new HashSet<>();
        Set<Class<?>> unresolved = new HashSet<>(fxmlComponents);
        Map<Class<?>, Class<?>> knownTypes = new HashMap<>();

        boolean progress;
        do {
            progress = false;
            Iterator<Class<?>> iterator = unresolved.iterator();

            while (iterator.hasNext()) {
                Class<?> component = iterator.next();
                if (canInstantiate(component, knownTypes.keySet())) {
                    resolved.add(component);
                    registerComponentWithSuperTypes(component, knownTypes);
                    iterator.remove();
                    progress = true;
                }
            }
        } while (progress);

        instantiableComponents.putAll(knownTypes);

        if (!unresolved.isEmpty()) {
            collectInstantiationErrors(unresolved);
        }
    }

    /**
     * Registers a component along with its unique superclasses and interfaces in the known types map.
     *
     * @param component  The component class.
     * @param knownTypes The map of known instantiable types.
     */
    private void registerComponentWithSuperTypes(Class<?> component, Map<Class<?>, Class<?>> knownTypes) {
        knownTypes.put(component, component);

        for (Class<?> superType : getAllSuperTypes(component)) {
            if (!notUniqueTypes.contains(superType)) {
                knownTypes.put(superType, component);
            }
        }
    }

    /**
     * Checks whether a given @FXMLComponent class can be instantiated based on known dependencies.
     *
     * @param component  The component class to check.
     * @param knownTypes Set of known instantiable types.
     * @return {@code true} if the component can be instantiated, otherwise {@code false}.
     */
    private boolean canInstantiate(Class<?> component, Set<Class<?>> knownTypes) {
        for (Constructor<?> constructor : component.getConstructors()) {
            Class<?>[] paramTypes = constructor.getParameterTypes();
            if (paramTypes.length == 0 || Arrays.stream(paramTypes).allMatch(knownTypes::contains)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collects error messages for components that cannot be instantiated and adds them to the error list.
     *
     * @param unresolved Set of components that could not be instantiated.
     */
    private void collectInstantiationErrors(Set<Class<?>> unresolved) {
        for (Class<?> component : unresolved) {
            StringBuilder errorMsg = new StringBuilder("Component cannot be instantiated: " + component.getName());

            boolean possibleWithUniqueTypes = false;

            for (Constructor<?> constructor : component.getConstructors()) {
                Class<?>[] paramTypes = constructor.getParameterTypes();
                List<Class<?>> problematicTypes = Arrays.stream(paramTypes)
                        .filter(t -> !instantiableComponents.containsKey(t) && !notUniqueTypes.contains(t))
                        .collect(Collectors.toList());

                if (problematicTypes.isEmpty()) {
                    possibleWithUniqueTypes = true;
                } else {
                    errorMsg.append("\n  ➤ Requires unknown types: ").append(problematicTypes);
                }
            }

            if (possibleWithUniqueTypes) {
                errorMsg.append("\n  ➤ Could be instantiated if multiple implementations of ")
                        .append("interfaces/superclasses were resolved uniquely: ")
                        .append(getConflictingTypes(component));
            }

            errors.add(errorMsg.toString());
        }
    }

    /**
     * Returns a comma-separated list of conflicting types that prevent instantiation.
     *
     * @param component The component class.
     * @return A string listing the conflicting types.
     */
    private String getConflictingTypes(Class<?> component) {
        return Arrays.stream(component.getConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .filter(notUniqueTypes::contains)
                .map(Class::getName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Retrieves all superclasses and interfaces of a given class.
     *
     * @param clazz The class to analyze.
     * @return A set of all superclasses and interfaces of the given class.
     */
    private Set<Class<?>> getAllSuperTypes(Class<?> clazz) {
        Set<Class<?>> result = new HashSet<>();
        Class<?> superClass = clazz.getSuperclass();

        while (superClass != null && superClass != Object.class) {
            result.add(superClass);
            superClass = superClass.getSuperclass();
        }

        result.addAll(Arrays.asList(clazz.getInterfaces()));
        return result;
    }

    /**
     * Returns a map of instantiable @FXMLComponent classes and their associated interfaces/superclasses.
     *
     * @return A map of instantiable components.
     */
    public Map<Class<?>, Class<?>> getInstantiableComponents() {
        return instantiableComponents;
    }

    /**
     * Returns the set of non-unique types (superclasses/interfaces with multiple implementations).
     *
     * @return A set of non-unique types.
     */
    public Set<Class<?>> getNotUniqueTypes() {
        return notUniqueTypes;
    }

    /**
     * Returns the map of unique types to corresponding @FXMLComponent implementations.
     *
     * @return A map of unique types.
     */
    public Map<Class<?>, Class<?>> getUniqueTypeToComponent() {
        return uniqueTypeToComponent;
    }

    /**
     * Returns a list of errors encountered during instantiation resolution.
     *
     * @return A list of error messages.
     */
    public List<String> getErrors() {
        return errors;
    }
}