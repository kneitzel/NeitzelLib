package de.neitzel.fx.view;

import de.neitzel.injection.ApplicationContext;
import javafx.beans.DefaultProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The list of binding elements declared inside {@code fx:define}.
 * <p>
 * The list is not a scene-graph child. FXML adds each {@link DirectBinding} child to
 * {@link #getElements()}.
 */
@DefaultProperty("elements")
public class BindingList {

    private final List<DirectBinding> elements = new ArrayList<>();

    /**
     * Creates a list and registers it with the load in progress.
     */
    public BindingList() {
        LoadSession.register(this);
    }

    /**
     * Returns the Direct bindings in this list.
     * <p>
     * FXML appends child elements to the returned list.
     *
     * @return the mutable list of Direct bindings
     */
    public List<DirectBinding> getElements() {
        return elements;
    }

    /**
     * Applies every Direct binding in this list.
     *
     * @param namespace objects addressed by {@code fx:id}
     * @param viewModel the ViewModel for this View
     * @param context   the container that creates a named Converter
     */
    void apply(Map<String, Object> namespace, ViewModel viewModel, ApplicationContext context) {
        for (DirectBinding binding : elements) {
            DirectBindingApplier.apply(namespace, viewModel, binding, context);
        }
    }
}
