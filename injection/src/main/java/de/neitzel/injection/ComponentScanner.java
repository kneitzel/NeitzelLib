package de.neitzel.injection;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scans a base package for Jakarta CDI-style components and derives dependency-injection metadata.
 * <p>
 * The scanner detects component classes annotated with {@link jakarta.inject.Singleton} or
 * {@link jakarta.inject.Named}. It then:
 * <ul>
 *   <li>Analyzes the discovered components' super types (interfaces and superclasses) to detect
 *       unique vs. ambiguous (non-unique) injectable types.</li>
 *   <li>Determines an {@code InjectionPlan} for each component (constructor + field + setter injection).</li>
 *   <li>Iteratively resolves which components are instantiable based on already-known resolvable types.</li>
 *   <li>Collects errors for components that cannot be resolved or instantiated.</li>
 * </ul>
 * <p>
 * The result is a mapping from injectable types to {@link ComponentData} describing how to create
 * and inject the corresponding component.
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
     * Cache of computed {@link InjectionPlan}s per component class.
     * <p>
     * Plans are derived once via reflection and then reused to avoid repeated expensive analysis.
     */
    private final Map<Class<?>, InjectionPlan> injectionPlans = new HashMap<>();

    /**
     * Tracks component classes that are known to be invalid (i.e., no usable injection plan).
     * <p>
     * This prevents repeated analysis and also avoids emitting duplicate error messages.
     */
    private final Set<Class<?>> invalidComponents = new HashSet<>();

    /**
     * Final mapping of resolvable injectable types to their corresponding {@link ComponentData}.
     * <p>
     * This map includes concrete component classes as keys and, where unique, their injectable
     * super types (interfaces/superclasses) as additional keys.
     */
    private final Map<Class<?>, ComponentData> instantiableComponents = new HashMap<>();

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
     * Scans the specified base package for classes annotated with {@link Singleton} or {@link Named}.
     * Identified component classes are added to a collection for further processing.
     *
     * @param basePackage the package to scan for component annotations
     */
    private void scanForComponents(String basePackage) {
        Reflections reflections = new Reflections(basePackage);
        components.addAll(reflections.getTypesAnnotatedWith(Singleton.class));
        components.addAll(reflections.getTypesAnnotatedWith(Named.class));
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

        while (true) {
            Map<Class<?>, ComponentData> newlyResolvable = new HashMap<>();

            for (Class<?> clazz : unresolved) {
                if (invalidComponents.contains(clazz)) {
                    continue;
                }

                ComponentData componentInfo = createComponentData(clazz, knownTypes.keySet());
                if (componentInfo != null) {
                    newlyResolvable.put(clazz, componentInfo);
                }
            }

            if (newlyResolvable.isEmpty()) {
                break;
            }

            for (ComponentData componentInfo : newlyResolvable.values()) {
                resolved.add(componentInfo.getType());
                registerComponentWithSuperTypes(componentInfo, knownTypes);
            }

            unresolved.removeAll(newlyResolvable.keySet());
        }

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
     * Attempts to create {@link ComponentData} for a component if an injection plan can be determined
     * and all required dependencies are already known.
     *
     * @param component  the concrete component class to analyze
     * @param knownTypes the set of injectable types that are already resolvable in the current iteration
     * @return component metadata if the component is currently instantiable; otherwise {@code null}
     */
    private ComponentData createComponentData(Class<?> component, Set<Class<?>> knownTypes) {
        InjectionPlan plan = getInjectionPlan(component);
        if (plan == null) {
            return null;
        }

        if (!areDependenciesKnown(plan, knownTypes)) {
            return null;
        }

        return new ComponentData(component, Scope.SINGLETON, plan.constructor(), plan.fields(), plan.setters());
    }

    /**
     * Registers an instantiable component under its concrete class and (if unique) under its
     * injectable super types.
     * <p>
     * Non-unique super types are intentionally skipped to prevent ambiguous dependency resolution.
     *
     * @param component  the instantiable component metadata
     * @param knownTypes the mutable registry of resolvable injectable types for the resolution loop
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
     * Builds human-readable error messages for components that could not be resolved/instantiated.
     * <p>
     * Missing dependencies are computed from the component's {@link InjectionPlan}. If no missing
     * dependencies can be identified, the component is assumed to be blocked by ambiguous (non-unique)
     * super types or other conflicts.
     *
     * @param unresolved set of component classes that remained unresolved after the resolution loop
     */
    private void collectInstantiationErrors(Set<Class<?>> unresolved) {
        for (Class<?> component : unresolved) {
            if (invalidComponents.contains(component)) {
                continue;
            }

            InjectionPlan plan = getInjectionPlan(component);
            if (plan == null) {
                continue;
            }

            List<Class<?>> missingConstructor = new ArrayList<>();
            for (Class<?> parameterType : plan.constructor().getParameterTypes()) {
                if (isUnknownDependency(parameterType)) {
                    missingConstructor.add(parameterType);
                }
            }

            List<Class<?>> missingFields = new ArrayList<>();
            for (Field field : plan.fields()) {
                if (isUnknownDependency(field.getType())) {
                    missingFields.add(field.getType());
                }
            }

            List<Class<?>> missingSetters = new ArrayList<>();
            for (Method setter : plan.setters()) {
                Class<?> dependencyType = setter.getParameterTypes()[0];
                if (isUnknownDependency(dependencyType)) {
                    missingSetters.add(dependencyType);
                }
            }

            StringBuilder errorMessage = new StringBuilder("Component cannot be instantiated: ");
            errorMessage.append(component.getName());
            if (!missingConstructor.isEmpty()) {
                errorMessage.append(" | Missing constructor dependencies: ").append(missingConstructor);
            }
            if (!missingFields.isEmpty()) {
                errorMessage.append(" | Missing field dependencies: ").append(missingFields);
            }
            if (!missingSetters.isEmpty()) {
                errorMessage.append(" | Missing setter dependencies: ").append(missingSetters);
            }
            if (missingConstructor.isEmpty() && missingFields.isEmpty() && missingSetters.isEmpty()) {
                errorMessage.append(" | Conflicting super types detected");
            }

            errors.add(errorMessage.toString());
        }
    }

    /**
     * Returns a cached {@link InjectionPlan} for the given component class.
     * <p>
     * If plan determination fails, the component is marked as invalid and {@code null} is returned.
     *
     * @param component the component class
     * @return the computed injection plan, or {@code null} if none can be determined
     */
    private InjectionPlan getInjectionPlan(Class<?> component) {
        if (injectionPlans.containsKey(component)) {
            return injectionPlans.get(component);
        }
        if (invalidComponents.contains(component)) {
            return null;
        }

        InjectionPlan plan = determineInjectionPlan(component);
        if (plan == null) {
            invalidComponents.add(component);
        } else {
            injectionPlans.put(component, plan);
        }
        return plan;
    }

    /**
     * Checks whether all dependencies referenced by the given plan are present in the known type set.
     *
     * @param plan       the injection plan to evaluate
     * @param knownTypes currently resolvable injectable types
     * @return {@code true} if all constructor/field/setter dependencies are known; otherwise {@code false}
     */
    private boolean areDependenciesKnown(InjectionPlan plan, Set<Class<?>> knownTypes) {
        if (!Arrays.stream(plan.constructor().getParameterTypes()).allMatch(knownTypes::contains)) {
            return false;
        }

        for (Field field : plan.fields()) {
            if (!knownTypes.contains(field.getType())) {
                return false;
            }
        }

        for (Method setter : plan.setters()) {
            Class<?> dependencyType = setter.getParameterTypes()[0];
            if (!knownTypes.contains(dependencyType)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Determines an {@link InjectionPlan} for a component by selecting a constructor and collecting
     * injectable fields and setter methods.
     * <p>
     * A field is injectable if annotated with {@link jakarta.inject.Inject}.
     * A method is treated as setter injection if annotated with {@link jakarta.inject.Inject} and
     * matches the JavaBeans setter shape ({@code void setX(T value)}).
     *
     * @param component the component class
     * @return the injection plan, or {@code null} if no usable constructor can be selected
     */
    private InjectionPlan determineInjectionPlan(Class<?> component) {
        Constructor<?> constructor = selectConstructor(component);
        if (constructor == null) {
            return null;
        }

        List<Field> fields = Arrays.stream(component.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Inject.class))
                .peek(field -> field.setAccessible(true))
                .toList();

        List<Method> setters = Arrays.stream(component.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Inject.class))
                .filter(method -> isValidSetter(component, method))
                .peek(method -> method.setAccessible(true))
                .toList();

        return new InjectionPlan(constructor, fields, setters);
    }

    /**
     * Selects the constructor to use for instantiation.
     * <ul>
     *   <li>If exactly one constructor is annotated with {@link jakarta.inject.Inject}, it is selected.</li>
     *   <li>If multiple constructors are annotated with {@code @Inject}, the component is invalid.</li>
     *   <li>If there is exactly one declared constructor, it is selected.</li>
     *   <li>Otherwise, a no-arg constructor is required and selected if present.</li>
     * </ul>
     *
     * @param component the component class
     * @return the selected constructor, or {@code null} if none can be selected
     */
    private Constructor<?> selectConstructor(Class<?> component) {
        Constructor<?>[] constructors = component.getDeclaredConstructors();
        List<Constructor<?>> injectConstructors = Arrays.stream(constructors)
                .filter(c -> c.isAnnotationPresent(Inject.class))
                .toList();

        if (injectConstructors.size() > 1) {
            errors.add("Multiple @Inject constructors found for " + component.getName());
            return null;
        }

        Constructor<?> selected;
        if (injectConstructors.size() == 1) {
            selected = injectConstructors.get(0);
        } else if (constructors.length == 1) {
            selected = constructors[0];
        } else {
            selected = Arrays.stream(constructors)
                    .filter(c -> c.getParameterCount() == 0)
                    .findFirst()
                    .orElse(null);
            if (selected == null) {
                errors.add("No @Inject constructor and no no-arg constructor found for " + component.getName());
                return null;
            }
        }

        selected.setAccessible(true);
        return selected;
    }

    /**
     * Validates whether a method annotated for injection is a compatible setter.
     * <p>
     * A valid setter must:
     * <ul>
     *   <li>Have exactly one parameter</li>
     *   <li>Return {@code void}</li>
     *   <li>Start with the prefix {@code set}</li>
     * </ul>
     *
     * @param component the declaring component class (used for error messages)
     * @param method    the method to validate
     * @return {@code true} if the method is a valid setter; otherwise {@code false}
     */
    private boolean isValidSetter(Class<?> component, Method method) {
        boolean isSetter = method.getParameterCount() == 1
                && method.getReturnType() == Void.TYPE
                && method.getName().startsWith("set");

        if (!isSetter) {
            errors.add("Invalid @Inject method detected in " + component.getName() + ": " + method.getName());
        }

        return isSetter;
    }

    /**
     * Determines whether a dependency type is unknown to the container.
     * <p>
     * A dependency is considered unknown if it is neither resolvable (present in
     * {@link #instantiableComponents}) nor explicitly marked as ambiguous (present in
     * {@link #notUniqueTypes}).
     *
     * @param type the dependency type
     * @return {@code true} if the dependency cannot currently be resolved; otherwise {@code false}
     */
    private boolean isUnknownDependency(Class<?> type) {
        return !instantiableComponents.containsKey(type) && !notUniqueTypes.contains(type);
    }

    /**
     * Returns the mapping of resolvable injectable types to their {@link ComponentData}.
     *
     * @return a map of types to instantiation/injection metadata
     */
    public Map<Class<?>, ComponentData> getInstantiableComponents() {
        return instantiableComponents;
    }

    /**
     * Returns the set of super types (interfaces/superclasses) that are implemented by multiple
     * components and therefore cannot be uniquely resolved.
     *
     * @return set of non-unique injectable types
     */
    public Set<Class<?>> getNotUniqueTypes() {
        return notUniqueTypes;
    }

    /**
     * Returns collected errors discovered during scanning and resolution.
     *
     * @return list of human-readable error messages
     */
    public List<String> getErrors() {
        return errors;
    }

    /**
     * Immutable description of how to inject a component.
     *
     * @param constructor constructor selected for instantiation
     * @param fields      fields annotated with {@link jakarta.inject.Inject}
     * @param setters     setter methods annotated with {@link jakarta.inject.Inject}
     */
    private record InjectionPlan(Constructor<?> constructor, List<Field> fields, List<Method> setters) {
    }
}

