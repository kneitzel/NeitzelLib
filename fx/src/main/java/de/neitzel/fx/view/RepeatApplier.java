package de.neitzel.fx.view;

import de.neitzel.injection.ApplicationContext;
import javafx.beans.property.Property;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

import java.util.Map;

/**
 * Keeps one Component child in a pane for each Model instance in a collection.
 */
final class RepeatApplier {

    private RepeatApplier() {
    }

    /**
     * Fills the named pane with one new Component for each element, in list order.
     * <p>
     * Each Component is a new injected instance. The cached singleton of that class is left
     * in place. The child's model property is the element. Adding or removing an element
     * adds or removes the matching child. The list is the one the entity holds, so a change
     * on either side is visible in the pane.
     *
     * @param namespace objects addressed by {@code fx:id}
     * @param viewModel the ViewModel for this View
     * @param repeat    the declaration
     * @param context   the container that creates each Component
     */
    static void apply(Map<String, Object> namespace, ViewModel viewModel, Repeat repeat,
                      ApplicationContext context) {
        Object named = namespace.get(repeat.getPane());
        if (!(named instanceof Pane pane)) {
            throw new IllegalArgumentException("No pane \"" + repeat.getPane() + "\"");
        }
        Property<?> property = viewModel.property(repeat.getCollection());
        if (property == null || !(property.getValue() instanceof ObservableList<?> values)) {
            throw new IllegalArgumentException("No collection \"" + repeat.getCollection() + "\"");
        }
        Class<?> type = componentType(repeat.getComponent());
        for (Object element : values) {
            pane.getChildren().add(child(type, context, element));
        }
        listen(pane, type, context, values);
    }

    @SuppressWarnings("unchecked")
    private static void listen(Pane pane, Class<?> type, ApplicationContext context, ObservableList<?> values) {
        ObservableList<Object> elements = (ObservableList<Object>) values;
        elements.addListener((ListChangeListener<Object>) change -> {
            while (change.next()) {
                if (change.wasPermutated() || change.wasUpdated()) {
                    continue;
                }
                if (change.getRemovedSize() > 0) {
                    pane.getChildren().remove(change.getFrom(), change.getFrom() + change.getRemovedSize());
                }
                for (int index = 0; index < change.getAddedSize(); index++) {
                    pane.getChildren().add(
                            change.getFrom() + index,
                            child(type, context, change.getAddedSubList().get(index)));
                }
            }
        });
    }

    private static Node child(Class<?> type, ApplicationContext context, Object element) {
        Object component = Components.fresh(type, context);
        Components.setModel(component, element);
        return (Node) component;
    }

    private static Class<?> componentType(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException failure) {
            throw new IllegalArgumentException("No component " + className, failure);
        }
    }
}
