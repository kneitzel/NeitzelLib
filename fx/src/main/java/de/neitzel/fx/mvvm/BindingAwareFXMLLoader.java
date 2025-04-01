package de.neitzel.fx.mvvm;

import javafx.beans.property.Property;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TextInputControl;
import lombok.RequiredArgsConstructor;

import java.io.InputStream;

@RequiredArgsConstructor
/**
 * Custom FXMLLoader that binds JavaFX controls to a GenericViewModel using metadata from FXML.
 * It supports automatic binding setup based on properties defined in the FXML's node properties.
 *
 * @param <T> the type of the model used in the ViewModel
 */
public class BindingAwareFXMLLoader<T> {

    /**
     * The model instance used to construct the GenericViewModel.
     */
    private final T model;

    /**
     * Loads an FXML file and performs automatic binding setup using the GenericViewModel.
     *
     * @param fxml the input stream of the FXML file
     * @return the root node of the loaded scene graph
     * @throws Exception if loading the FXML or binding fails
     */
    public Parent load(InputStream fxml) throws Exception {
        FXMLLoader loader = new FXMLLoader();
        loader.setControllerFactory(param -> {
            GenericViewController<T> controller = new GenericViewController<>();
            controller.setModel(model);
            return controller;
        });

        Parent root = loader.load(fxml);

        GenericViewController<T> controller = loader.getController();
        GenericViewModel<T> viewModel = controller.getViewModel();

        // Traverse Nodes and evaluate binding info
        bindNodesRecursively(root, viewModel);

        return root;
    }

    /**
     * Recursively traverses the scene graph and binds controls to properties
     * in the ViewModel based on custom metadata in the node properties map.
     *
     * @param node the current node to inspect
     * @param viewModel the ViewModel holding the properties
     */
    private void bindNodesRecursively(Node node, GenericViewModel<T> viewModel) {
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                bindNodesRecursively(child, viewModel);
            }
        }

        Object userData = node.getProperties().get("bind:property");
        if (userData instanceof String propertyName) {
            BindDirection direction = getDirection(node);

            if (node instanceof TextInputControl control) {
                Property<String> prop = viewModel.property(StringProperty.class, propertyName);
                bind(control.textProperty(), prop, direction);
            } else if (node instanceof javafx.scene.control.Label label) {
                Property<String> prop = viewModel.property(StringProperty.class, propertyName);
                bind(label.textProperty(), prop, direction);
            } else if (node instanceof javafx.scene.control.CheckBox checkBox) {
                Property<Boolean> prop = viewModel.property(javafx.beans.property.BooleanProperty.class, propertyName);
                bind(checkBox.selectedProperty(), prop, direction);
            } else if (node instanceof javafx.scene.control.Slider slider) {
                Property<Number> prop = viewModel.property(javafx.beans.property.DoubleProperty.class, propertyName);
                bind(slider.valueProperty(), prop, direction);
            } else if (node instanceof javafx.scene.control.DatePicker datePicker) {
                @SuppressWarnings("unchecked")
                Property<java.time.LocalDate> prop = (Property<java.time.LocalDate>) viewModel.property(javafx.beans.property.ObjectProperty.class, propertyName);
                bind(datePicker.valueProperty(), prop, direction);
            }
        }
    }

    /**
     * Binds two JavaFX properties according to the specified binding direction.
     *
     * @param controlProperty the property of the control (e.g. TextField.textProperty)
     * @param modelProperty the property from the ViewModel
     * @param direction the direction of the binding
     * @param <V> the value type of the property
     */
    private <V> void bind(Property<V> controlProperty, Property<V> modelProperty, BindDirection direction) {
        switch (direction) {
            case BIDIRECTIONAL -> controlProperty.bindBidirectional(modelProperty);
            case READ -> controlProperty.bind(modelProperty);
            case WRITE -> modelProperty.bind(controlProperty);
        }
    }

    /**
     * Retrieves the binding direction for a node based on its "bind:direction" property.
     *
     * @param node the node whose direction property is read
     * @return the BindDirection specified or a default
     */
    private BindDirection getDirection(final Node node) {
        return BindDirection.fromString((String) node.getProperties().get("bind:direction"));
    }
}