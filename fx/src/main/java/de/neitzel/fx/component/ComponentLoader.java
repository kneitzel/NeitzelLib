package de.neitzel.fx.component;

import de.neitzel.fx.component.controls.Binding;
import de.neitzel.fx.component.controls.FxmlComponent;
import de.neitzel.fx.component.model.BindingData;
import javafx.beans.property.Property;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ComponentLoader is responsible for loading JavaFX FXML components and binding
 * them to automatically generated ViewModels based on simple POJO models.
 */
@Slf4j
public class ComponentLoader {
    private Map<String, Map<String, String>> nfxBindingMap = new HashMap<>();

    @Getter
    private Object controller;

    public Parent load(URL fxmlPath) {
        return load(null, fxmlPath);
    }

    public Parent load(String fxmlPath) {
        return load(null, fxmlPath);
    }

    public Parent load(Object model, URL fxmlPath) {
        try {
            AutoViewModel<?> viewModel = new AutoViewModel<>(model);
            FXMLLoader loader = new FXMLLoader(fxmlPath);
            loader.setControllerFactory(type -> new ComponentController(viewModel));
            Parent root = loader.load();
            controller = loader.getController();
            return root;
        } catch (IOException e) {
            throw new RuntimeException("unable to load fxml: " + fxmlPath, e);
        }
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

    public static <T extends Parent> T load(URL fxmlUrl, Object controller, String nothing) throws IOException {
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

    private static <T> void bindBidirectionalSafe(@NotNull Property<T> source, Property<?> target) {
        try {
            Property<T> targetCasted = (Property<T>) target;
            source.bindBidirectional(targetCasted);
        } catch (ClassCastException e) {
            log.error("⚠️ Typkonflikt beim Binding: %s ⇄ %s%n", source.getClass(), target.getClass(), e);
        }
    }

    private static <T> void bindSafe(@NotNull Property<T> source, Property<?> target) {
        try {
            Property<T> targetCasted = (Property<T>) target;
            source.bind(targetCasted);
        } catch (ClassCastException e) {
            log.error("⚠️ Typkonflikt beim Binding: %s ⇄ %s%n", source.getClass(), target.getClass(), e);
        }
    }

    private static void evaluateBindings(List<BindingData> bindings, Map<String, Object> namespace) {
        for (var binding : bindings) {
            try {
                Object source = resolveExpression(binding.getSource(), namespace);
                Object target = resolveExpression(binding.getTarget(), namespace);

                if (source instanceof Property && target instanceof Property) {
                    Property<?> sourceProp = (Property<?>) source;
                    Property<?> targetProp = (Property<?>) target;

                    Class<?> sourceType = getPropertyType(sourceProp);
                    Class<?> targetType = getPropertyType(targetProp);

                    boolean bindableForward = targetType.isAssignableFrom(sourceType);
                    boolean bindableBackward = sourceType.isAssignableFrom(targetType);

                    switch (binding.getDirection().toLowerCase()) {
                        case "bidirectional":
                            if (bindableForward && bindableBackward) {
                                bindBidirectionalSafe(sourceProp, targetProp);
                            } else {
                                log.error("⚠️ Kann bidirektionales Binding nicht durchführen: Typen inkompatibel (%s ⇄ %s)%n", sourceType, targetType);
                            }
                            break;
                        case "unidirectional":
                        default:
                            if (bindableForward) {
                                bindSafe(sourceProp, targetProp);
                            } else {
                                log.error("⚠️ Kann unidirektionales Binding nicht durchführen: %s → %s nicht zuweisbar%n", sourceType, targetType);
                            }
                            break;
                    }
                }
            } catch (Exception e) {
                log.error("Fehler beim Binding: " + binding.getSource() + " → " + binding.getTarget(), e);
            }
        }
    }

    private static Class<?> getPropertyType(Property<?> prop) {
        try {
            Method getter = prop.getClass().getMethod("get");
            return getter.getReturnType();
        } catch (Exception e) {
            return Object.class; // Fallback
        }
    }

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
     * Binds a JavaFX UI control to a ViewModel property according to the specified direction.
     *
     * @param node      the UI node (e.g., TextField)
     * @param vmProp    the ViewModel property to bind to
     * @param direction the direction of the binding (e.g., "bidirectional", "read")
     */
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
