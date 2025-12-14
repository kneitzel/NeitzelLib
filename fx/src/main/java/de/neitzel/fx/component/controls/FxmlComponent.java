package de.neitzel.fx.component.controls;

import de.neitzel.fx.component.ComponentLoader;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;

import java.util.Arrays;

/**
 * A reusable JavaFX component that loads and embeds an FXML-defined UI fragment.
 * <p>
 * The component exposes three properties:
 * <ul>
 *   <li>fxml: the resource path to the FXML file to load (relative to the component class)</li>
 *   <li>direction: a textual hint that describes binding directionality (default "unidirectional")</li>
 *   <li>data: an arbitrary object that will be injected into the loaded controller if a suitable setter exists</li>
 * </ul>
 * <p>
 * When the {@code fxml} property changes the FXML is loaded via {@link ComponentLoader}. After loading the
 * controller instance available from the loader, this component attempts to inject the object from the
 * {@code data} property by calling a matching {@code setData(...)} method via reflection.
 */
public class FxmlComponent extends StackPane {

    private final StringProperty fxml = new SimpleStringProperty();

    private final StringProperty direction = new SimpleStringProperty("unidirectional");

    private final ObjectProperty<Object> data = new SimpleObjectProperty<>();

    /**
     * Creates a new instance of {@code FxmlComponent}.
     * <p>
     * The constructor registers listeners on the {@code fxml} and {@code data} properties to
     * perform lazy loading and data injection when values change.
     */
    public FxmlComponent() {
        fxml.addListener((obs, oldVal, newVal) -> load());
        data.addListener((obs, oldVal, newVal) -> injectData());
    }

    /**
     * Loads the FXML specified by the {@link #fxml} property and replaces the current children
     * with the loaded root node. If no FXML is configured or the path is blank, the method returns
     * without performing any action.
     */
    private void load() {
        if (getFxml() == null || getFxml().isBlank()) return;
        ComponentLoader loader = new ComponentLoader();
        Parent content = loader.load(getClass().getResource(getFxml()));

        getChildren().setAll(content);

        Object controller = loader.getController();
        if (controller != null && getData() != null) {
            injectDataToController(controller, getData());
        }
    }

    /**
     * Attempts to inject the {@link #data} object into the controller of the currently loaded child node,
     * if a controller is present and defines a single-argument method named {@code setData}.
     */
    private void injectData() {
        if (!getChildren().isEmpty() && getData() != null) {
            Object controller = getControllerFromChild(getChildren().get(0));
            if (controller != null) {
                injectDataToController(controller, getData());
            }
        }
    }

    /**
     * Returns the configured FXML resource path.
     *
     * @return the FXML path or {@code null} if not set
     */
    public String getFxml() {
        return fxml.get();
    }

    /**
     * Sets the FXML resource path to load. Changing this value triggers a reload of the component content.
     *
     * @param fxml resource path relative to the component class
     */
    public void setFxml(String fxml) {
        this.fxml.set(fxml);
    }

    /**
     * Returns the data object that will be injected into the loaded controller when available.
     *
     * @return the data object or {@code null}
     */
    public Object getData() {
        return data.get();
    }

    /**
     * Sets the data object to be injected into the controller of the loaded FXML.
     * Changing the value will attempt injection into the currently loaded controller.
     *
     * @param data the data object to inject
     */
    public void setData(Object data) {
        this.data.set(data);
    }

    /**
     * Uses reflection to find a single-argument method named {@code setData} on the controller and
     * invokes it with the provided {@code dataObject}.
     *
     * @param controller the controller instance to inject into
     * @param dataObject the object to inject
     */
    private void injectDataToController(Object controller, Object dataObject) {
        // Daten-Objekt per Reflection zuweisen
        // Beispiel: Controller hat `setData(User data)`
        Arrays.stream(controller.getClass().getMethods())
                .filter(m -> m.getName().equals("setData") && m.getParameterCount() == 1)
                .findFirst()
                .ifPresent(method -> {
                    try {
                        method.invoke(controller, dataObject);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

        // Optional: automatisch Binding ausführen (s. u.)
    }

    /**
     * Extracts the controller instance from a child node's properties map if present.
     *
     * @param node the child node to inspect
     * @return the controller object stored under the property key {@code "fx:controller"} or {@code null}
     */
    private Object getControllerFromChild(Node node) {
        if (node.getProperties().containsKey("fx:controller")) {
            return node.getProperties().get("fx:controller");
        }
        return null;
    }

    /**
     * Returns the JavaFX property representing the configured FXML path.
     *
     * @return the FXML property
     */
    public StringProperty fxmlProperty() {
        return fxml;
    }

    /**
     * Returns the JavaFX property representing the binding direction hint.
     *
     * @return the direction property
     */
    public StringProperty directionProperty() {
        return direction;
    }

    /**
     * Returns the configured direction string.
     *
     * @return the direction hint (e.g., "unidirectional")
     */
    public String getDirection() {
        return direction.get();
    }

    /**
     * Sets the direction hint used by higher-level binding logic.
     *
     * @param direction textual direction hint
     */
    public void setDirection(String direction) {
        this.direction.set(direction);
    }

    /**
     * Returns the JavaFX property representing the data object used for injection.
     *
     * @return the data property
     */
    public ObjectProperty<Object> dataProperty() {
        return data;
    }
}
