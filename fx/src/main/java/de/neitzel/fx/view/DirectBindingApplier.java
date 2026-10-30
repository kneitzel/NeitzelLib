package de.neitzel.fx.view;

import javafx.beans.property.Property;
import javafx.collections.ObservableList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Connects one Direct binding to a control property and a ViewModel Attribute.
 */
final class DirectBindingApplier {

    private DirectBindingApplier() {
    }

    /**
     * Applies one Direct binding.
     *
     * @param namespace objects addressed by {@code fx:id}
     * @param viewModel the ViewModel for this View
     * @param binding   the declaration
     */
    static void apply(Map<String, Object> namespace, ViewModel viewModel, DirectBinding binding) {
        Object control = namespace.get(binding.getControl());
        if (control == null) {
            throw new IllegalArgumentException("No control \"" + binding.getControl() + "\"");
        }
        Property<?> controlProperty = controlProperty(control, binding.getProperty());
        Property<?> attribute = viewModel.property(binding.getPath());
        if (attribute == null) {
            throw new IllegalArgumentException(
                    "No read-write attribute for path \"" + binding.getPath() + "\"");
        }
        if (attribute.getValue() instanceof ObservableList<?> values) {
            assignList(controlProperty, values);
            return;
        }
        switch (binding.getDirection()) {
            case "both" -> alignBoth(controlProperty, attribute);
            case "view" -> alignTowardView(controlProperty, attribute);
            case "model" -> alignTowardModel(controlProperty, attribute);
            default -> throw new IllegalArgumentException("Not a direction: " + binding.getDirection());
        }
    }

    private static Property<?> controlProperty(Object control, String propertyName) {
        String methodName = propertyName + "Property";
        try {
            Method method = control.getClass().getMethod(methodName);
            Object value = method.invoke(control);
            if (!(value instanceof Property<?> property)) {
                throw new IllegalArgumentException(methodName + " did not return a property");
            }
            return property;
        } catch (NoSuchMethodException failure) {
            throw new IllegalArgumentException(
                    "No property " + propertyName + " on " + control.getClass().getName(), failure);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Could not read property " + propertyName, failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static void assignList(Property<?> controlProperty, ObservableList<?> values) {
        ((Property<Object>) controlProperty).setValue(values);
    }

    @SuppressWarnings("unchecked")
    private static void alignBoth(Property<?> controlProperty, Property<?> attribute) {
        ((Property<Object>) controlProperty).bindBidirectional((Property<Object>) attribute);
    }

    @SuppressWarnings("unchecked")
    private static void alignTowardView(Property<?> controlProperty, Property<?> attribute) {
        Property<Object> control = (Property<Object>) controlProperty;
        Property<Object> source = (Property<Object>) attribute;
        control.setValue(source.getValue());
        source.addListener((observable, oldValue, newValue) -> control.setValue(newValue));
    }

    @SuppressWarnings("unchecked")
    private static void alignTowardModel(Property<?> controlProperty, Property<?> attribute) {
        Property<Object> control = (Property<Object>) controlProperty;
        Property<Object> target = (Property<Object>) attribute;
        control.setValue(target.getValue());
        control.addListener((observable, oldValue, newValue) -> target.setValue(newValue));
    }
}
