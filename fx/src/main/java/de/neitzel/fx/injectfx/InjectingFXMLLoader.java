package de.neitzel.fx.injectfx;

import de.neitzel.injection.ComponentScanner;
import javafx.fxml.FXMLLoader;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URL;

/**
 * The InjectingFXMLLoader class provides a custom implementation of JavaFX's FXMLLoader
 * that supports dependency injection. It facilitates the loading of FXML files while
 * dynamically injecting dependencies into controllers during the loading process.
 * <p>
 * This class utilizes the InjectingControllerFactory to enable seamless integration
 * of dependency injection with JavaFX controllers. Dependencies can be added to the
 * controller factory, and the loader will use this factory to instantiate controllers
 * with the appropriate dependencies.
 * <p>
 * Features of this loader include:
 * - Support for dependency injection into JavaFX controllers by using a custom factory.
 * - Adding custom dependency mappings at runtime.
 * - Scanning and initializing injectable components from a specified package.
 */
@Slf4j
public class InjectingFXMLLoader {

    /**
     * Represents an instance of the {@link InjectingControllerFactory}, which is used for creating
     * and managing controller instances with dependency injection capabilities in a JavaFX application.
     * <p>
     * This factory facilitates the injection of dependencies by dynamically resolving and supplying
     * required objects during controller instantiation. It plays a critical role in enabling seamless
     * integration of dependency injection with JavaFX's {@link FXMLLoader}.
     * <p>
     * The `controllerFactory` is used internally by the {@link InjectingFXMLLoader} to provide a consistent
     * and extensible mechanism for controller creation while maintaining loose coupling and enhancing testability.
     * <p>
     * Key responsibilities:
     * - Manages a mapping of classes to their injectable instances required for controller instantiation.
     * - Dynamically analyzes and invokes appropriate constructors for controllers based on the availability
     * of dependencies.
     * - Ensures that controllers are created with required dependencies, preventing manual resolution of injections.
     * <p>
     * This variable is initialized in the {@link InjectingFXMLLoader} and can be extended with additional
     * mappings at runtime using relevant methods.
     */
    private final InjectingControllerFactory controllerFactory;

    /**
     * Default constructor for the InjectingFXMLLoader class.
     * This initializes the loader with a new instance of the {@link InjectingControllerFactory},
     * which is used to provide dependency injection capabilities for JavaFX controllers.
     * <p>
     * The {@link InjectingControllerFactory} allows for the registration and dynamic injection
     * of dependencies into controllers when they are instantiated during the FXML loading process.
     */
    public InjectingFXMLLoader() {
        controllerFactory = new InjectingControllerFactory();
    }

    /**
     * Constructs a new instance of the InjectingFXMLLoader class with a specified
     * InjectingControllerFactory for dependency injection. This enables the FXMLLoader
     * to use the provided controller factory to instantiate controllers with their
     * required dependencies during the FXML loading process.
     *
     * @param controllerFactory The controller factory responsible for creating controller
     *                          instances with dependency injection support.
     */
    public InjectingFXMLLoader(InjectingControllerFactory controllerFactory) {
        this.controllerFactory = controllerFactory;
    }

    /**
     * Constructs an instance of `InjectingFXMLLoader` and initializes the component scanner
     * for dependency injection based on components found within a specified package.
     * The scanned components are analyzed and their instances are prepared for injection
     * using the internal `InjectingControllerFactory`.
     *
     * @param packageName The package name to be scanned for injectable components.
     *                    Classes within the specified package will be identified
     *                    as potential dependencies and made available for injection
     *                    into JavaFX controllers during FXML loading.
     */
    public InjectingFXMLLoader(String packageName) {
        controllerFactory = new InjectingControllerFactory();
        ComponentScanner scanner = new ComponentScanner(packageName);
        FXMLComponentInstances instances = new FXMLComponentInstances(scanner);
        addInjectingData(instances);
    }

    /**
     * Adds all injectable data from the given {@code FXMLComponentInstances} to the controller factory.
     * Iterates through the classes in the instance map and delegates adding each class-instance pair
     * to the {@link #addInjectingData(Class, Object)} method.
     *
     * @param instances An {@code FXMLComponentInstances} object containing the mapping of classes
     *                  to their respective singleton instances. This data represents the components
     *                  available for dependency injection.
     */
    private void addInjectingData(FXMLComponentInstances instances) {
        for (var clazz : instances.getInstanceMap().keySet()) {
            addInjectingData(clazz, instances.getInstance(clazz));
        }
    }

    /**
     * Adds a specific class-object mapping to the controller factory for dependency injection.
     * This method allows the association of a given class type with its corresponding instance,
     * enabling dependency injection into controllers during the FXML loading process.
     *
     * @param clazz  The class type to be associated with the provided object instance.
     * @param object The object instance to be injected for the specified class type.
     */
    public void addInjectingData(Class<?> clazz, Object object) {
        controllerFactory.addInjectingData(clazz, object);
    }

    /**
     * Loads an FXML resource from the provided URL and injects dependencies into its controller
     * using the configured {@code InjectingControllerFactory}.
     *
     * @param url the URL of the FXML file to be loaded
     * @param <T> the type of the root element defined in the FXML
     * @return the root element of the FXML file
     * @throws IOException if an error occurs during the loading of the FXML file
     */
    public <T> T load(URL url) throws IOException {
        FXMLLoader loader = new FXMLLoader(url);
        loader.setControllerFactory(controllerFactory);
        return loader.load();
    }
}
