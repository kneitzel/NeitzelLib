package de.neitzel.fx.view;

import de.neitzel.injection.ApplicationContext;
import javafx.beans.property.Property;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Keeps one Component child in a pane for each Model instance in a collection.
 */
final class RepeatApplier {

    /**
     * Prevents instantiation.
     */
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
     * @param viewModel the ViewModel for this View, or a replacement that exposes the collection by name
     * @param repeat    the declaration
     * @param context   the container that creates each Component
     */
    static void apply(Map<String, Object> namespace, Object viewModel, Repeat repeat,
                      ApplicationContext context, ViewLifetime lifetime) {
        Object named = namespace.get(repeat.getPane());
        if (!(named instanceof Pane pane)) {
            throw new IllegalArgumentException("No pane \"" + repeat.getPane() + "\"");
        }
        Property<?> property = collection(viewModel, repeat.getCollection());
        if (property == null || !(property.getValue() instanceof ObservableList<?> values)) {
            throw new IllegalArgumentException("No collection \"" + repeat.getCollection() + "\"");
        }
        Class<?> type = componentType(repeat.getComponent());
        int offset = pane.getChildren().size();
        List<RepeatedChild> children = new ArrayList<>();
        lifetime.own(() -> pane.getChildren().removeAll(children.stream().map(RepeatedChild::node).toList()));
        for (Object element : values) {
            RepeatedChild child = child(type, context, element, lifetime);
            children.add(child);
            pane.getChildren().add(child.node());
        }
        listen(pane, type, context, values, lifetime, offset, children);
    }

    /**
     * Returns the collection Attribute named on the ViewModel.
     *
     * @param viewModel the ViewModel for this View
     * @param name      the collection Attribute
     * @return the property, or {@code null} when the name is not an Attribute
     */
    private static Property<?> collection(Object viewModel, String name) {
        if (viewModel instanceof ViewModel model) {
            return model.property(name);
        }
        return NamedProperties.get(viewModel, name);
    }

    /**
     * Adds and removes Component children as the collection changes.
     * <p>
     * A permutation or an element update is left as it is. An add or remove keeps the children
     * in list order.
     *
     * @param pane    the pane named by the repeat
     * @param type    the Component class
     * @param context the container that creates each Component
     * @param values  the collection
     */
    @SuppressWarnings("unchecked")
    private static void listen(Pane pane, Class<?> type, ApplicationContext context, ObservableList<?> values,
                               ViewLifetime lifetime, int offset, List<RepeatedChild> children) {
        ObservableList<Object> elements = (ObservableList<Object>) values;
        lifetime.listen(elements, change -> {
            while (change.next()) {
                if (change.wasPermutated() || change.wasUpdated()) {
                    continue;
                }
                for (int removed = 0; removed < change.getRemovedSize(); removed++) {
                    RepeatedChild child = children.remove(change.getFrom());
                    pane.getChildren().remove(child.node());
                    child.lifetime().close();
                }
                for (int index = 0; index < change.getAddedSize(); index++) {
                    RepeatedChild child = child(type, context, change.getAddedSubList().get(index), lifetime);
                    int position = change.getFrom() + index;
                    children.add(position, child);
                    pane.getChildren().add(offset + position, child.node());
                }
            }
        });
    }

    /**
     * Creates one Component and sets its model to {@code element}.
     *
     * @param type    the Component class
     * @param context the container
     * @param element the Model instance
     * @return the loaded Component
     */
    private static RepeatedChild child(Class<?> type, ApplicationContext context, Object element,
                                       ViewLifetime owner) {
        ViewLifetime lifetime = owner.child();
        try {
            Object component = Components.fresh(type, context, lifetime);
            Components.setModel(component, element, lifetime);
            return new RepeatedChild((Node) component, lifetime);
        } catch (RuntimeException | Error failure) {
            lifetime.rollback(failure);
            throw failure;
        }
    }

    private record RepeatedChild(Node node, ViewLifetime lifetime) {}

    /**
     * Returns the Component class named by the repeat.
     *
     * @param className the class name
     * @return the class
     */
    private static Class<?> componentType(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException failure) {
            throw new IllegalArgumentException("No component " + className, failure);
        }
    }
}
