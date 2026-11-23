package de.neitzel.fx.view;

import de.neitzel.injection.ApplicationContext;
import javafx.beans.DefaultProperty;
import javafx.scene.Parent;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The list of binding elements declared inside {@code fx:define}.
 * <p>
 * The list is not a scene-graph child. FXML adds each {@link DirectBinding},
 * {@link ComputedBinding}, {@link CommandBinding}, or {@link Repeat} child to {@link #getElements()}.
 */
@DefaultProperty("elements")
public class BindingList {

    /**
     * The binding elements, in document order.
     */
    @Getter
    private final List<Object> elements = new ArrayList<>();

    /**
     * Creates a list and registers it with the load in progress.
     */
    public BindingList() {
        LoadSession.register(this);
    }

    /**
     * Applies every binding element in this list.
     *
     * @param namespace  objects addressed by {@code fx:id}
     * @param viewModel  the ViewModel for this View, or a replacement that exposes JavaFX properties
     *                   by name
     * @param controller the Controller for this View, or {@code null} when the document names none
     * @param root       the scene-graph root of this View
     * @param context    the container that creates a named Converter
     */
    void apply(Map<String, Object> namespace, Object viewModel, Object controller, Parent root,
               ApplicationContext context, ViewLifetime lifetime) {
        for (Object element : elements) {
            if (element instanceof DirectBinding directBinding) {
                DirectBindingApplier.apply(namespace, viewModel, directBinding, context, lifetime);
            } else if (element instanceof ComputedBinding computedBinding) {
                ComputedBindingApplier.apply(namespace, viewModel, controller, root, computedBinding, lifetime);
            } else if (element instanceof CommandBinding commandBinding) {
                CommandBindingApplier.apply(namespace, viewModel, commandBinding, lifetime);
            } else if (element instanceof Repeat repeat) {
                RepeatApplier.apply(namespace, viewModel, repeat, context, lifetime);
            } else {
                throw new IllegalArgumentException("Not a binding element: " + element);
            }
        }
    }
}
