package de.neitzel.injection;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * InjectableComponentScanner is responsible for scanning packages to detect classes annotated
 * with @FXMLComponent and analyzing their compatibility for instantiation and dependency injection.
 * The resulting analysis identifies unique and shared interfaces/superclasses as well as
 * potentially instantiable components, collecting relevant errors if instantiation is not feasible.
 */
public class ComponentScanner {

    /**
     * A set that stores classes annotated with {@code @Component}, representing
     * FXML-compatible components detected during the scanning process.
     * These components are used as part of the dependency injection mechanism
     * within the {@code InjectableComponentScanner}.
     * <p>
     * The {@code fxmlComponents} set is populated during the component scanning
     * phase by identifying all classes within a specified package hierarchy
     * annotated with the {@code @Component} annotation. These classes serve
     * as the primary source for further analysis, resolution, and instantiation
     * in the scanning workflow.
     * <p>
     * This field is immutable, ensuring thread safety and consistent state
     * throughout the lifetime of the {@code InjectableComponentScanner}.
     */
    private final Set<Class<?>> components = new HashSet<>();

    /**
     * A set of component types that are not uniquely associated with a single implementation.
     * <p>
     * This collection is populated during the analysis of discovered components to identify
     * types that have multiple implementations, preventing them from being uniquely resolved.
     * For example, if multiple components implement the same interface, that interface will be
     * added to this set.
     * <p>
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
     * <p>
     * This map is populated during the component analysis process. For each super type
     * in the scanned components, if exactly one implementation is found, it is
     * considered unique and added to this mapping. In case multiple implementations
     * exist for a given super type, it is excluded from this map and classified as
     * a non-unique type.
     * <p>
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
     * <p>
     * This field is part of the dependency injection process, where components annotated
     * with {@code @Component} or similar annotations are scanned, analyzed, and registered.
     * The resolution process checks if a component can be instantiated based on its
     * constructor dependencies being resolvable using known types or other registered components.
     */
    private final Map<Class<?>, ComponentData> instantiableComponents = new HashMap<>();

    /**
     * A list of error messages encountered during the scanning and analysis
     * of components in the dependency injection process.
     * <p>
     * This list is populated when components cannot be resolved due to issues
     * such as multiple implementations of a superclass or interface,
     * unmet construction requirements, or unresolved dependencies.
     * <p>
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
    public ComponentScanner(String basePackage) {
        scanForComponents(basePackage);
        analyzeComponentTypes();
        resolveInstantiableComponents();
    }

    /**
     * Scans the specified base package for classes annotated with {@link Singleton}.
     * Identified component classes are added to a collection for further processing.
     *
     * @param basePackage the package to scan for component annotations
     */
    private void scanForComponents(String basePackage) {
        Reflections reflections = new Reflections(basePackage);
        components.addAll(reflections.getTypesAnnotatedWith(Singleton.class));
    }

    /**
     * Analyzes component types within the injected components and classifies them based on their
     * inheritance hierarchy and relationships.
     * <p>
     * This method performs the following operations:
     * 1. Maps each component to all of its superclasses and interfaces.
     * 2. Identifies which superclasses or interfaces have multiple implementing components.
     * 3. Populates:
     * - A list of superclasses or interfaces that are not uniquely linked to a single component.
     * - A map where unique superclasses or interfaces are associated with a specific implementing component.
     * <p>
     * The mappings are built using the following data structures:
     * - A map from superclasses/interfaces to the list of components that implement or extend them.
     * - A list of non-unique types where multiple components exist for the same superclass or interface.
     * - A map of unique superclasses/interfaces to their corresponding component.
     * <p>
     * This method is a key part of the component scanning and resolution process, facilitating
     * the identification of potential instantiation ambiguities or conflicts.
     */
    private void analyzeComponentTypes() {
        Map<Class<?>, List<Class<?>>> superTypesMap = new HashMap<>();

        for (Class<?> component : components) {
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
     * Resolves and identifies instantiable components from a set of scanned components.
     * This process determines which components can be instantiated based on their dependencies
     * and class relationships, while tracking unresolved types and potential conflicts.
     * <p>
     * The resolution process involves:
     * 1. Iteratively determining which components can be instantiated using the known types
     * map. A component is resolvable if its dependencies can be satisfied by the current
     * set of known types.
     * 2. Registering resolvable components and their superclasses/interfaces into the known
     * types map for future iterations.
     * 3. Removing successfully resolved components from the unresolved set.
     * 4. Repeating the process until no further components can be resolved in a given iteration.
     * <p>
     * At the end of the resolution process:
     * - Resolvable components are added to the `instantiableComponents` map, which maps types
     * to their corresponding instantiable implementations.
     * - Unresolved components are identified, and error details are collected to highlight
     * dependencies or conflicts preventing their instantiation.
     * <p>
     * If errors are encountered due to unresolved components, they are logged for further analysis.
     */
    private void resolveInstantiableComponents() {
        Set<Class<?>> resolved = new HashSet<>();
        Set<Class<?>> unresolved = new HashSet<>(components);
        Map<Class<?>, ComponentData> knownTypes = new HashMap<>();
        Set<Class<?>> resolvableNow;

        do {
            resolvableNow = unresolved.stream()
                    .filter(c -> canInstantiate(c, knownTypes.keySet()))
                    .collect(Collectors.toSet());

            for (Class<?> clazz : resolvableNow) {
                Singleton annotation = clazz.getAnnotation(Singleton.class);
                ComponentData componentInfo = new ComponentData(clazz, Scope.SINGLETON);

                resolved.add(clazz);
                registerComponentWithSuperTypes(componentInfo, knownTypes);
            }

            unresolved.removeAll(resolvableNow);
        } while (!resolvableNow.isEmpty());

        instantiableComponents.putAll(knownTypes);

        if (!unresolved.isEmpty()) {
            collectInstantiationErrors(unresolved);
        }
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
     * Determines whether the specified component class can be instantiated based on the provided set
     * of known types. A component is considered instantiable if it has at least one constructor where
     * all parameter types are contained in the known types set or if it has a no-argument constructor.
     * Additionally, all fields annotated with {@code @Inject} must have their types present in the
     * known types set.
     *
     * @param component  the class to check for instantiability
     * @param knownTypes the set of currently known types that can be used to satisfy dependencies
     * @return {@code true} if the component can be instantiated; {@code false} otherwise
     */
    private boolean canInstantiate(Class<?> component, Set<Class<?>> knownTypes) {
        boolean hasValidConstructor = false;

        for (Constructor<?> constructor : component.getConstructors()) {
            Class<?>[] paramTypes = constructor.getParameterTypes();
            if (paramTypes.length == 0 || Arrays.stream(paramTypes).allMatch(knownTypes::contains)) {
                hasValidConstructor = true;
                break;
            }
        }

        if (!hasValidConstructor) {
            return false;
        }

        for (var field : component.getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class) && !knownTypes.contains(field.getType())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Registers a component and its superclasses or interfaces in the provided map of known types.
     * This method ensures that the component and its inheritance hierarchy are associated with the
     * component's data unless the supertype is marked as non-unique.
     *
     * @param component  the {@code ComponentData} instance representing the component to be registered
     * @param knownTypes the map where component types and their data are stored
     */
    private void registerComponentWithSuperTypes(ComponentData component, Map<Class<?>, ComponentData> knownTypes) {
        knownTypes.put(component.getType(), component);

        for (Class<?> superType : getAllSuperTypes(component.getType())) {
            if (!notUniqueTypes.contains(superType)) {
                knownTypes.put(superType, component);
            }
        }
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
                        .toList();

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
     * or an empty string if no conflicts are found.
     */
    private String getConflictingTypes(Class<?> component) {
        return Arrays.stream(component.getConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .filter(notUniqueTypes::contains)
                .map(Class::getName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Retrieves a map of classes representing component types to their corresponding instantiable implementations.
     *
     * @return A map where the key is a class type and the value is the corresponding class implementation
     * that can be instantiated.
     */
    public Map<Class<?>, ComponentData> getInstantiableComponents() {
        return instantiableComponents;
    }

    /**
     * Retrieves a set of types that have multiple implementations, making them
     * non-unique. These types could not be uniquely resolved during the
     * component scanning and analysis process.
     *
     * @return a set of classes representing the types that have multiple
     * implementations and cannot be resolved uniquely
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
     * and the values are their unique component implementations.
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