package de.neitzel.fx.injectfx;

import javafx.util.Callback;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The InjectingControllerFactory is responsible for creating controller instances for JavaFX that
 * support dependency injection. It uses a parameter map to resolve and supply dependencies to
 * controller constructors dynamically during instantiation.
 *
 * This class simplifies the process of injecting dependencies into JavaFX controllers by analyzing
 * the constructors of the given controller classes at runtime. It selects the constructor that best
 * matches the available dependencies in the parameter map and creates an instance of the controller.
 *
 * It implements the Callback interface to provide compatibility with the JavaFX FXMLLoader, allowing
 * controllers with dependencies to be injected seamlessly during the FXML loading process.
 */
public class InjectingControllerFactory implements Callback<Class<?>, Object> {
    /**
     * A map that stores class-to-object mappings used for dependency injection
     * in controller instantiation. This map is utilized to resolve and supply
     * the required dependencies for constructors during the creation of controller
     * instances.
     *
     * Each key in the map represents a class type, and the corresponding value
     * is the instance of that type. This allows the {@link InjectingControllerFactory}
     * to use the stored instances to dynamically match and inject dependencies
     * into controllers at runtime.
     */
    private final Map<Class<?>, Object> parameterMap = new HashMap<>();

    /**
     * Adds a mapping between a class and its corresponding object instance
     * to the parameter map used for dependency injection.
     *
     * @param clazz  The class type to be associated with the provided object instance.
     * @param object The object instance to be injected for the specified class type.
     */
    public void addInjectingData(Class<?> clazz, Object object) {
        parameterMap.put(clazz, object);
    }

    /**
     * Creates an instance of a controller class using a constructor that matches the dependencies
     * defined in the parameter map. The method dynamically analyzes the constructors of the given
     * class and attempts to instantiate the class by injecting required dependencies.
     *
     * @param controllerClass the class of the controller to be instantiated
     * @return an instance of the specified controller class
     * @throws RuntimeException if an error occurs while creating the controller instance or if no suitable constructor is found
     */
    @Override
    public Object call(Class<?> controllerClass) {
        try {
            Optional<Constructor<?>> bestConstructor = Stream.of(controllerClass.getConstructors())
                    .filter(constructor -> canBeInstantiated(constructor, parameterMap))
                    .findFirst();

            if (bestConstructor.isPresent()) {
                Constructor<?> constructor = bestConstructor.get();
                Object[] parameters = Stream.of(constructor.getParameterTypes())
                        .map(parameterMap::get)
                        .toArray();

                return constructor.newInstance(parameters);
            } else {
                throw new IllegalStateException("Kein passender Konstruktor gefunden für " + controllerClass.getName());
            }
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Erstellen der Controller-Instanz für " + controllerClass.getName(), e);
        }
    }

    /**
     * Determines if a given constructor can be instantiated using the provided parameter map.
     * This method checks if the parameter map contains entries for all parameter types required
     * by the specified constructor.
     *
     * @param constructor The constructor to be evaluated for instantiability.
     * @param parameterMap A map where keys are parameter types and values are the corresponding
     *                     instances available for injection.
     * @return {@code true} if the constructor can be instantiated with the given parameter map;
     *         {@code false} otherwise.
     */
    private boolean canBeInstantiated(Constructor<?> constructor, Map<Class<?>, Object> parameterMap) {
        return Stream.of(constructor.getParameterTypes()).allMatch(parameterMap::containsKey);
    }
}
