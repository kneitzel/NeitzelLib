package de.neitzel.fx.view;

import de.neitzel.injection.ApplicationContext;
import javafx.beans.DefaultProperty;
import javafx.scene.Parent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The list of binding elements declared inside {@code fx:define}.
 * <p>
 * The list is not a scene-graph child. FXML adds each {@link DirectBinding} or
 * {@link ComputedBinding} child to {@link #getElements()}.
 */
@DefaultProperty("elements")
public class BindingList {

    private final List<Object> elements = new ArrayList<>();

    /**
     * Creates a list and registers it with the load in progress.
     */
    public BindingList() {
        LoadSession.register(this);
    }

    /**
     * Returns the binding elements in this list.
     * <p>
     * FXML appends child elements to the returned list. A child is a {@link DirectBinding} or a
     * {@link ComputedBinding}.
     *
     * @return the mutable list of binding elements
     */
    public List<Object> getElements() {
        return elements;
    }

    /**
     * Applies every binding element in this list.
     *
     * @param namespace  objects addressed by {@code fx:id}
     * @param viewModel  the ViewModel for this View
     * @param controller the Controller for this View, or {@code null} when the document names none
     * @param root       the scene-graph root of this View
     * @param context    the container that creates a named Converter
     */
    void apply(Map<String, Object> namespace, ViewModel viewModel, Object controller, Parent root,
               ApplicationContext context) {
        for (Object element : elements) {
            if (element instanceof DirectBinding directBinding) {
                DirectBindingApplier.apply(namespace, viewModel, directBinding, context);
            } else if (element instanceof ComputedBinding computedBinding) {
                ComputedBindingApplier.apply(namespace, viewModel, controller, root, computedBinding);
            } else {
                throw new IllegalArgumentException("Not a binding element: " + element);
            }
        }
    }
}
