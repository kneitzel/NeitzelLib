package de.neitzel.fx.view;

import javafx.beans.property.Property;

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
        Resolved resolved = resolve(viewModel, binding.getPath());
        if (resolved == null) {
            return;
        }
        Property<?> controlProperty = controlProperty(control, binding.getProperty());
        Property<?> attribute = resolved.attribute;
        if (resolved.complex) {
            switch (binding.getDirection()) {
                case "both" -> alignBothComplex(controlProperty, attribute);
                case "view" -> alignTowardViewComplex(controlProperty, attribute);
                case "model" -> alignTowardModelComplex(controlProperty, attribute);
                default -> throw new IllegalArgumentException("Not a direction: " + binding.getDirection());
            }
            return;
        }
        switch (binding.getDirection()) {
            case "both" -> alignBoth(controlProperty, attribute);
            case "view" -> alignTowardView(controlProperty, attribute);
            case "model" -> alignTowardModel(controlProperty, attribute);
            default -> throw new IllegalArgumentException("Not a direction: " + binding.getDirection());
        }
    }

    /**
     * The Attribute a Path names, and whether that Attribute is complex.
     */
    private static final class Resolved {
        private final Property<?> attribute;
        private final boolean complex;

        private Resolved(Property<?> attribute, boolean complex) {
            this.attribute = attribute;
            this.complex = complex;
        }
    }

    /**
     * Follows a Path to the Attribute it names.
     * <p>
     * One name is an Attribute of {@code viewModel}. Each earlier name steps into the ViewModel
     * held by that Complex attribute. A null Complex attribute along the Path leaves the binding
     * unapplied.
     *
     * @param viewModel the ViewModel for this View
     * @param path      the Path
     * @return the Attribute for the last name, or {@code null} when a Complex attribute
     *         along the Path is null
     */
    private static Resolved resolve(ViewModel viewModel, String path) {
        String[] names = path.split("\\.", -1);
        ViewModel current = viewModel;
        for (int index = 0; index < names.length - 1; index++) {
            String name = names[index];
            Property<?> step = current.property(name);
            if (step == null || !current.isComplex(name)) {
                throw missing(path);
            }
            Object nested = step.getValue();
            if (nested == null) {
                return null;
            }
            if (!(nested instanceof ViewModel child)) {
                throw missing(path);
            }
            current = child;
        }
        String last = names[names.length - 1];
        Property<?> attribute = current.property(last);
        if (attribute == null) {
            throw missing(path);
        }
        return new Resolved(attribute, current.isComplex(last));
    }

    private static IllegalArgumentException missing(String path) {
        return new IllegalArgumentException("No read-write attribute for path \"" + path + "\"");
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
    private static void alignBothComplex(Property<?> controlProperty, Property<?> attribute) {
        Property<Object> control = (Property<Object>) controlProperty;
        Property<Object> source = (Property<Object>) attribute;
        control.setValue(modelBehind(source.getValue()));
        source.addListener((observable, oldValue, newValue) -> {
            Object model = modelBehind(newValue);
            if (control.getValue() != model) {
                control.setValue(model);
            }
        });
        control.addListener((observable, oldValue, newValue) -> {
            if (modelBehind(source.getValue()) == newValue) {
                return;
            }
            source.setValue(newValue == null ? null : ViewModels.of(newValue));
        });
    }

    @SuppressWarnings("unchecked")
    private static void alignTowardViewComplex(Property<?> controlProperty, Property<?> attribute) {
        Property<Object> control = (Property<Object>) controlProperty;
        Property<Object> source = (Property<Object>) attribute;
        control.setValue(modelBehind(source.getValue()));
        source.addListener((observable, oldValue, newValue) -> control.setValue(modelBehind(newValue)));
    }

    @SuppressWarnings("unchecked")
    private static void alignTowardModelComplex(Property<?> controlProperty, Property<?> attribute) {
        Property<Object> control = (Property<Object>) controlProperty;
        Property<Object> source = (Property<Object>) attribute;
        control.setValue(modelBehind(source.getValue()));
        control.addListener((observable, oldValue, newValue) -> {
            if (modelBehind(source.getValue()) == newValue) {
                return;
            }
            source.setValue(newValue == null ? null : ViewModels.of(newValue));
        });
    }

    private static Object modelBehind(Object value) {
        if (value instanceof ViewModel viewModel) {
            return viewModel.model();
        }
        return null;
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
