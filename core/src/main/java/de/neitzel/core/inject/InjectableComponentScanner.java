package de.neitzel.core.inject;

import de.neitzel.core.inject.annotation.Component;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.stream.Collectors;

/**
 * InjectableComponentScanner is responsible for scanning packages to detect classes annotated
 * with @FXMLComponent and analyzing their compatibility for instantiation and dependency injection.
 * The resulting analysis identifies unique and shared interfaces/superclasses as well as
 * potentially instantiable components, collecting relevant errors if instantiation is not feasible.
 */
public class InjectableComponentScanner {

    /**
     * A set that stores classes annotated with {@code @Component}, representing
     * FXML-compatible components detected during the scanning process.
     * These components are used as part of the dependency injection mechanism
     * within the {@code InjectableComponentScanner}.
     *
     * The {@code fxmlComponents} set is populated during the component scanning
     * phase by identifying all classes within a specified package hierarchy
     * annotated with the {@code @Component} annotation. These classes serve
     * as the primary source for further analysis, resolution, and instantiation
     * in the scanning workflow.
     *
     * This field is immutable, ensuring thread safety and consistent state
     * throughout the lifetime of the {@code InjectableComponentScanner}.
     */
    private final Set<Class<?>> fxmlComponents = new HashSet<>();

    /**
     * A set of component types that are not uniquely associated with a single implementation.
     *
     * This collection is populated during the analysis of discovered components to identify
     * types that have multiple implementations, preventing them from being uniquely resolved.
     * For example, if multiple components implement the same interface, that interface will be
     * added to this set.
     *
     * It is primarily used during the component registration process to avoid ambiguities
     * in the dependency injection system, ensuring that only resolvable, uniquely identifiable
     * components can be instantiated and injected.
     */
    private final Set<Class<?>> notUniqueTypes = new HashSet<>();

    /**
     * A mapping of unique super types to their corresponding component classes.
     * The key represents a super type (interface or abstract class), and the value
     * is the specific implementation or subclass that is uniquely identified among
     * the scanned components.
     *
     * This map is populated during the component analysis process. For each super type
     * in the scanned components, if exactly one implementation is found, it is
     * considered unique and added to this mapping. In case multiple implementations
     * exist for a given super type, it is excluded from this map and classified as
     * a non-unique type.
     *
     * This mapping is utilized for resolving dependencies and determining which components
     * can be instantiated based on unique type information.
     */
    private final Map<Class<?>, Class<?>> uniqueTypeToComponent = new HashMap<>();

    /**
     * A mapping of components that can be instantiated with their corresponding types.
     * This map links a component's class (key) to the specific implementation class (value)
     * that can be instantiated. The entries are resolved through the scanning and analysis
     * of available component types, ensuring that only components with resolvable
     * dependencies are included.
     *
     * This field is part of the dependency injection process, where components annotated
     * with {@code @Component} or similar annotations are scanned, analyzed, and registered.
     * The resolution process checks if a component can be instantiated based on its
     * constructor dependencies being resolvable using known types or other registered components.
     */
    private final Map<Class<?>, Class<?>> instantiableComponents = new HashMap<>();

    /**
     * A list of error messages encountered during the scanning and analysis
     * of components in the dependency injection process.
     *
     * This list is populated when components cannot be resolved due to issues
     * such as multiple implementations of a superclass or interface,
     * unmet construction requirements, or unresolved dependencies.
     *
     * Errors added to this list provide detailed descriptions of the specific
     * issues that prevent certain components from being instantiated correctly.
     */
    private final List<String> errors = new ArrayList<>();

    /**
     * Initializes a new instance of the {@code InjectableComponentScanner} class, which scans for injectable
     * components within the specified base package and resolves the set of recognizable and instantiable components.
     *
     * @param basePackage the base package to scan for injectable components
     */
    public InjectableComponentScanner(String basePackage) {
        scanForComponents(basePackage);
        analyzeComponentTypes();
        resolveInstantiableComponents();
    }

    /**
     * Scans the specified base package for classes annotated with {@link Component}.
     * Identified component classes are added to a collection for further processing.
     *
     * @param basePackage the package to scan for component annotations
     */
    private void scanForComponents(String basePackage) {
        Reflections reflections = new Reflections(basePackage);
        fxmlComponents.addAll(reflections.getTypesAnnotatedWith(Component.class));
    }

    /**
     * Analyzes component types within the injected components and classifies them based on their
     * inheritance hierarchy and relationships.
     *
     * This method performs the following operations:
     * 1. Maps each component to all of its superclasses and interfaces.
     * 2. Identifies which superclasses or interfaces have multiple implementing components.
     * 3. Populates:
     *    - A list of superclasses or interfaces that are not uniquely linked to a single component.
     *    - A map where unique superclasses or interfaces are associated with a specific implementing component.
     *
     * The mappings are built using the following data structures:
     * - A map from superclasses/interfaces to the list of components that implement or extend them.
     * - A list of non-unique types where multiple components exist for the same superclass or interface.
     * - A map of unique superclasses/interfaces to their corresponding component.
     *
     * This method is a key part of the component scanning and resolution process, facilitating
     * the identification of potential instantiation ambiguities or conflicts.
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
     * Resolves components from the set of scanned classes that can be instantiated based on their constructors
     * and existing known types. The method iteratively processes classes to identify those whose dependencies
     * can be satisfied, marking them as resolved and registering them alongside their supertypes.
     *
     * If progress is made during a pass (i.e., a component is resolved), the process continues until no more
     * components can be resolved. Any components that remain unresolved are recorded for further inspection.
     *
     * The resolved components are stored in a map where each type and its supertypes are associated
     * with the component class itself. This map allows for subsequent lookups when verifying dependency satisfiability.
     *
     * If unresolved components remain after the resolution process, detailed instantiation errors are collected
     * for debugging or logging purposes.
     *
     * This method depends on the following utility methods:
     * - {@link #canInstantiate(Class, Set)}: Determines if a component can be instantiated based on existing known types.
     * - {@link #registerComponentWithSuperTypes(Class, Map)}: Registers a component and its supertypes in the known types map.
     * - {@link #collectInstantiationErrors(Set)}: Collects detailed error messages for unresolved components.
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
     * Registers the given component class in the provided map. The component is registered along with all of its
     * accessible superclasses and interfaces, unless those types are identified as non-unique.
     *
     * @param component the component class to register
     * @param knownTypes the map where the component and its types will be registered
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
     * Determines whether a given class can be instantiated based on its constructors and
     * the provided known types. A class is considered instantiable if it has a parameterless
     * constructor or if all the parameter types of its constructors are present in the known types.
     *
     * @param component  the class to check for instantiation eligibility
     * @param knownTypes the set of types known to be instantiable and available for constructor injection
     * @return true if the class can be instantiated; false otherwise
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
     * Collects the instantiation errors for a set of unresolved classes and appends
     * detailed error messages to the internal error list. This method analyzes unresolved
     * components based on their constructors, determining if all required types are
     * instantiable or if there are conflicting types that could prevent instantiation.
     *
     * @param unresolved the set of classes for which instantiation errors are collected
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
     * Identifies and retrieves a comma-separated string of conflicting types for a given component.
     * A conflicting type is a parameter type in the constructor of the given component
     * that is already marked as not unique within the application context.
     *
     * @param component the class for which conflicting types need to be identified.
     * @return a comma-separated string of fully qualified names of conflicting parameter types,
     *         or an empty string if no conflicts are found.
     */
    private String getConflictingTypes(Class<?> component) {
        return Arrays.stream(component.getConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .filter(notUniqueTypes::contains)
                .map(Class::getName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Retrieves all superclasses and interfaces of the specified class, excluding the {@code Object} class.
     *
     * @param clazz the class for which to retrieve all superclasses and interfaces
     * @return a set of all superclasses and interfaces implemented by the specified class
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
     * Retrieves a map of classes representing component types to their corresponding instantiable implementations.
     *
     * @return A map where the key is a class type and the value is the corresponding class implementation
     *         that can be instantiated.
     */
    public Map<Class<?>, Class<?>> getInstantiableComponents() {
        return instantiableComponents;
    }

    /**
     * Retrieves a set of types that have multiple implementations, making them
     * non-unique. These types could not be uniquely resolved during the
     * component scanning and analysis process.
     *
     * @return a set of classes representing the types that have multiple
     *         implementations and cannot be resolved uniquely
     */
    public Set<Class<?>> getNotUniqueTypes() {
        return notUniqueTypes;
    }

    /**
     * Returns a mapping of types to their unique component implementations.
     * This map includes only those types that have a single corresponding component
     * implementation, ensuring no ambiguity in resolution.
     *
     * @return a map where the keys are types (e.g., interfaces or superclasses)
     *         and the values are their unique component implementations.
     */
    public Map<Class<?>, Class<?>> getUniqueTypeToComponent() {
        return uniqueTypeToComponent;
    }

    /**
     * Retrieves a list of error messages recorded during the scanning and analysis process.
     * The errors typically indicate issues such as components that cannot be instantiated due to
     * missing dependencies, unresolvable component types, or multiple implementations of a type
     * that are not uniquely resolved.
     *
     * @return a list of error messages explaining the issues encountered.
     */
    public List<String> getErrors() {
        return errors;
    }
}