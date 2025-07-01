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

public class FxmlComponent extends StackPane {

    private final StringProperty fxml = new SimpleStringProperty();

    private final StringProperty direction = new SimpleStringProperty("unidirectional");

    private final ObjectProperty<Object> data = new SimpleObjectProperty<>();

    public FxmlComponent() {
        fxml.addListener((obs, oldVal, newVal) -> load());
        data.addListener((obs, oldVal, newVal) -> injectData());
    }

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

    private void injectData() {
        if (!getChildren().isEmpty() && getData() != null) {
            Object controller = getControllerFromChild(getChildren().get(0));
            if (controller != null) {
                injectDataToController(controller, getData());
            }
        }
    }

    public String getFxml() {
        return fxml.get();
    }

    public void setFxml(String fxml) {
        this.fxml.set(fxml);
    }

    public Object getData() {
        return data.get();
    }

    public void setData(Object data) {
        this.data.set(data);
    }

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

    private Object getControllerFromChild(Node node) {
        if (node.getProperties().containsKey("fx:controller")) {
            return node.getProperties().get("fx:controller");
        }
        return null;
    }

    public StringProperty fxmlProperty() {
        return fxml;
    }

    public StringProperty directionProperty() {
        return direction;
    }

    public String getDirection() {
        return direction.get();
    }

    public void setDirection(String direction) {
        this.direction.set(direction);
    }

    public ObjectProperty<Object> dataProperty() {
        return data;
    }
}

