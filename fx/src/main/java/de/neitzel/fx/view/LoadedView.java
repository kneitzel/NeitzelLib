package de.neitzel.fx.view;

import javafx.beans.property.Property;
import javafx.scene.Parent;

/**
 * The scene-graph root and ViewModel produced by loading a View for a Model instance.
 */
public final class LoadedView {

    private final Parent root;
    private final ViewModel viewModel;

    /**
     * Creates the result of one load.
     *
     * @param root      the scene-graph root
     * @param viewModel the ViewModel built for the Model instance
     */
    LoadedView(Parent root, ViewModel viewModel) {
        this.root = root;
        this.viewModel = viewModel;
    }

    /**
     * Returns the scene-graph root.
     *
     * @return the root
     */
    public Parent getRoot() {
        return root;
    }

    /**
     * Returns the ViewModel the View is bound to.
     *
     * @return the ViewModel
     */
    public ViewModel getViewModel() {
        return viewModel;
    }

    /**
     * Returns the JavaFX property for an Attribute name.
     * <p>
     * The property's class follows the Attribute's declared type. The name {@code latestMessage}
     * is a string property holding the text of the latest Message.
     *
     * @param name the Attribute name, or {@code latestMessage}
     * @return the property, or {@code null} when that name is not a read-write Attribute
     *         and is not {@code latestMessage}
     */
    public Property<?> property(String name) {
        return viewModel.property(name);
    }
}
