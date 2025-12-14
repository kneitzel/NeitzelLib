package de.neitzel.fx.component;

import de.neitzel.fx.component.controls.Binding;
import de.neitzel.fx.component.controls.FxmlComponent;
import de.neitzel.fx.component.model.BindingData;
import javafx.beans.property.Property;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ComponentLoader is responsible for loading JavaFX FXML components and binding
 * them to automatically generated ViewModels based on simple POJO models.
 */
@Slf4j
public class ComponentLoader {

    /**
     * The controller object associated with the ComponentLoader.
     * It is typically used to manage the behavior and interactions of the UI components
     * defined in an FXML file.
     */
    private Object controller;

    /**
     * Default constructor only
     */
    public ComponentLoader() {
        // default constructor only
    }

    /**
     * Loads an FXML file from the specified URL, sets the provided controller, and processes bindings.
     * This method parses the FXML file, initializes the specified controller, and evaluates binding controls
     * defined within the FXML namespace.
     *
     * @param <T>        the type of the root node, which must extend {@code Parent}
     * @param fxmlUrl    the URL of the FXML file to be loaded
     * @param controller the controller to associate with the FXML file
     * @param nothing    an unused parameter, included for compatibility
     * @return the root node loaded from the FXML file
     * @throws IOException if there is an error reading the FXML file
     */
    public static <T extends Parent> T load(URL fxmlUrl, Object controller, @SuppressWarnings("unused") String nothing) throws IOException {
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        loader.setController(controller);
        T root = loader.load();

        Map<String, Object> namespace = loader.getNamespace();

        // Nach allen BindingControls suchen:
        List<Binding> bindingControls = collectAllNodes(root).stream()
                .filter(n -> n instanceof Binding)
                .map(n -> (Binding) n)
                .toList();

        for (Binding bc : bindingControls) {
            evaluateBindings(bc.getBindings(), namespace);
        }

        return root;
    }

    /**
     * Safely binds two properties bidirectionally while ensuring type compatibility.
     * If the target property's type is not compatible with the source property's type, the binding will
     * not be performed, and an error will be logged.
     *
     * @param source the source property to bind, must not be null
     * @param target the target property to bind to; the type will be checked for compatibility at runtime
     * @param <T>    the type of the source property's value
     */
    @SuppressWarnings("unchecked")
    private static <T> void bindBidirectionalSafe(@NotNull Property<T> source, Property<?> target) {
        try {
            Property<T> targetCasted = (Property<T>) target;
            source.bindBidirectional(targetCasted);
        } catch (ClassCastException e) {
            log.error("⚠️ Typkonflikt beim Binding: {} ⇄ {}", source.getClass(), target.getClass(), e);
        }
    }

    /**
     * Attempts to bind the source property to the target property in a type-safe manner.
     * If the target property's type is incompatible with the source property's type, the binding operation
     * will be skipped, and an error message will be logged.
     *
     * @param source the source property to bind, must not be null
     * @param target the target property to which the source will be bound
     * @param <T>    the type of the source property's value
     */
    @SuppressWarnings("unchecked")
    private static <T> void bindSafe(@NotNull Property<T> source, Property<?> target) {
        try {
            Property<T> targetCasted = (Property<T>) target;
            source.bind(targetCasted);
        } catch (ClassCastException e) {
            log.error("⚠️ Typkonflikt beim Binding: {} ⇄ {}", source.getClass(), target.getClass(), e);
        }
    }

    /**
     * Evaluates and establishes bindings between source and target properties based on the provided
     * bindings list and namespace. Supports unidirectional and bidirectional bindings, ensuring type
     * compatibility before binding. Logs errors for unsuccessful binding attempts or exceptions.
     *
     * @param bindings  a list of BindingData objects that define the source, target, and direction for the bindings
     * @param namespace a map representing the namespace from which expressions in the bindings are resolved
     */
    private static void evaluateBindings(List<BindingData> bindings, Map<String, Object> namespace) {
        for (var binding : bindings) {
            try {
                Object source = resolveExpression(binding.getSource(), namespace);
                Object target = resolveExpression(binding.getTarget(), namespace);

                if (source instanceof Property<?> sourceProp && target instanceof Property<?> targetProp) {

                    Class<?> sourceType = getPropertyType(sourceProp);
                    Class<?> targetType = getPropertyType(targetProp);

                    boolean bindableForward = targetType.isAssignableFrom(sourceType);
                    boolean bindableBackward = sourceType.isAssignableFrom(targetType);

                    switch (binding.getDirection().toLowerCase()) {
                        case "bidirectional":
                            if (bindableForward && bindableBackward) {
                                bindBidirectionalSafe(sourceProp, targetProp);
                            } else {
                                log.error("⚠️ Kann bidirektionales Binding nicht durchführen: Typen inkompatibel ({} ⇄ {})", sourceType, targetType);
                            }
                            break;
                        case "unidirectional":
                        default:
                            if (bindableForward) {
                                bindSafe(sourceProp, targetProp);
                            } else {
                                log.error("⚠️ Kann unidirektionales Binding nicht durchführen: {} → {} nicht zuweisbar", sourceType, targetType);
                            }
                            break;
                    }
                }
            } catch (Exception e) {
                log.error("Fehler beim Binding: {} → {}", binding.getSource(), binding.getTarget(), e);
            }
        }
    }

    /**
     * Determines the type of the value held by the specified property.
     *
     * @param prop the property whose value type is to be determined, must not be null
     * @return the class of the property's value type; returns Object.class if the type cannot be determined
     */
    private static Class<?> getPropertyType(Property<?> prop) {
        try {
            Method getter = prop.getClass().getMethod("get");
            return getter.getReturnType();
        } catch (Exception e) {
            return Object.class; // Fallback
        }
    }

    /**
     * Resolves a dot-separated expression (e.g., "viewModel.username") by navigating through the provided namespace
     * map and invoking the corresponding getter methods to access nested properties.
     *
     * @param expr      the dot-separated expression to resolve, must not be null
     * @param namespace a map containing the initial objects to resolve the expression from, must not be null
     * @return the value resolved from the specified expression
     * @throws Exception if an error occurs while invoking getter methods or resolving the expression
     */
    private static Object resolveExpression(@NotNull String expr, @NotNull Map<String, Object> namespace) throws Exception {
        // z.B. "viewModel.username"
        String[] parts = expr.split("\\.");
        Object current = namespace.get(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            String getter = "get" + Character.toUpperCase(parts[i].charAt(0)) + parts[i].substring(1);
            current = current.getClass().getMethod(getter).invoke(current);
        }
        return current;
    }

    /**
     * Recursively collects all nodes in a scene graph starting from the given root node.
     *
     * @param root the starting node from which all descendant nodes will be collected
     * @return a list containing all nodes, including the root node and its descendants
     */
    private static @NotNull List<Node> collectAllNodes(Node root) {
        List<Node> nodes = new ArrayList<>();
        nodes.add(root);
        if (root instanceof Parent parent && !(root instanceof FxmlComponent)) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                nodes.addAll(collectAllNodes(child));
            }
        }
        return nodes;
    }

    /**
     * Returns the controller associated with the ComponentLoader.
     *
     * @return the controller object
     */
    public Object getController() {
        return controller;
    }

    /**
     * Loads an FXML file and returns the root JavaFX node without binding to a data model.
     * This method uses the given {@code fxmlPath} to locate and load the FXML file.
     *
     * @param fxmlPath the URL of the FXML file to be loaded
     * @return the root JavaFX node loaded from the specified FXML file
     */
    public Parent load(URL fxmlPath) {
        return load(null, fxmlPath);
    }

    /**
     * Loads an FXML file and binds its elements to a generated ViewModel
     * based on the given POJO model and specified FXML path.
     *
     * @param model    the data model (POJO) to bind to the UI
     * @param fxmlPath the URL path to the FXML file
     * @return the root JavaFX node loaded from FXML
     * @throws RuntimeException if the FXML could not be loaded
     */
    public Parent load(Object model, URL fxmlPath) {
        try {
            AutoViewModel<?> viewModel = new AutoViewModel<>(model);
            FXMLLoader loader = new FXMLLoader(fxmlPath);
            loader.setControllerFactory(type -> {
                Objects.requireNonNull(type);
                return new ComponentController(viewModel);
            });
            Parent root = loader.load();
            controller = loader.getController();
            return root;
        } catch (IOException e) {
            throw new RuntimeException("unable to load fxml: " + fxmlPath, e);
        }
    }

    /**
     * Loads an FXML file and returns its root node.
     *
     * @param fxmlPath the relative path to the FXML file
     * @return the root JavaFX node loaded from the FXML file
     */
    public Parent load(String fxmlPath) {
        return load(null, fxmlPath);
    }

    /**
     * Loads an FXML file and binds its elements to a generated ViewModel
     * based on the given POJO model.
     *
     * @param model    the data model (POJO) to bind to the UI
     * @param fxmlPath the path to the FXML file
     * @return the root JavaFX node loaded from FXML
     */
    public Parent load(Object model, String fxmlPath) {
        return load(model, getClass().getResource(fxmlPath));
    }

    /**
     * Binds a JavaFX UI control to a ViewModel property according to the specified direction.
     *
     * @param node      the UI node (e.g., TextField)
     * @param vmProp    the ViewModel property to bind to
     * @param direction the direction of the binding (e.g., "bidirectional", "read")
     */
    @SuppressWarnings("unused")
    private void bindNodeToProperty(javafx.scene.Node node, Property<?> vmProp, String direction) {
        if (node instanceof javafx.scene.control.TextField tf && vmProp instanceof javafx.beans.property.StringProperty sp) {
            if ("bidirectional".equalsIgnoreCase(direction)) {
                tf.textProperty().bindBidirectional(sp);
            } else if ("read".equalsIgnoreCase(direction)) {
                tf.textProperty().bind(sp);
            }
        }
        // Additional control types (e.g., CheckBox, ComboBox) can be added here
    }
}
