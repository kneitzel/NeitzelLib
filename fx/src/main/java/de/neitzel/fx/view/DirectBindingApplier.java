package de.neitzel.fx.view;

import de.neitzel.injection.ApplicationContext;
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
     * @param context   the container that creates a named Converter
     */
    static void apply(Map<String, Object> namespace, ViewModel viewModel, DirectBinding binding,
                      ApplicationContext context) {
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
        String converterName = binding.getConverter();
        if (converterName == null || converterName.isEmpty()) {
            alignPlain(controlProperty, attribute, binding.getDirection());
            return;
        }
        Converter converter = converter(context, converterName);
        switch (binding.getDirection()) {
            case "both" -> alignConverted(
                    controlProperty, attribute, converter, viewModel, binding.getPath(), true, true);
            case "view" -> alignConverted(
                    controlProperty, attribute, converter, viewModel, binding.getPath(), true, false);
            case "model" -> alignConverted(
                    controlProperty, attribute, converter, viewModel, binding.getPath(), false, true);
            default -> throw new IllegalArgumentException("Not a direction: " + binding.getDirection());
        }
    }

    private static void alignPlain(Property<?> controlProperty, Property<?> attribute, String direction) {
        switch (direction) {
            case "both" -> alignBoth(controlProperty, attribute);
            case "view" -> alignTowardView(controlProperty, attribute);
            case "model" -> alignTowardModel(controlProperty, attribute);
            default -> throw new IllegalArgumentException("Not a direction: " + direction);
        }
    }

    private static Converter converter(ApplicationContext context, String className) {
        try {
            Class<?> type = Class.forName(className);
            Object instance = context.getComponent(type.asSubclass(Object.class));
            if (!(instance instanceof Converter created)) {
                throw new IllegalArgumentException("Not a converter: " + className);
            }
            return created;
        } catch (ClassNotFoundException failure) {
            throw new IllegalArgumentException("No converter " + className, failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static void alignConverted(Property<?> controlProperty, Property<?> attribute, Converter converter,
                                       ViewModel viewModel, String path, boolean towardView, boolean towardModel) {
        Property<Object> control = (Property<Object>) controlProperty;
        Property<Object> source = (Property<Object>) attribute;
        Link link = new Link();
        pushControl(control, source, converter, viewModel, path, link);
        if (towardView) {
            source.addListener((observable, oldValue, newValue) -> {
                if (!link.applying) {
                    pushControl(control, source, converter, viewModel, path, link);
                }
            });
        }
        if (towardModel) {
            control.addListener((observable, oldValue, newValue) -> {
                if (!link.applying) {
                    pushAttribute(control, source, converter, viewModel, path, link);
                }
            });
        }
    }

    private static void pushControl(Property<Object> control, Property<Object> attribute, Converter converter,
                                    ViewModel viewModel, String path, Link link) {
        try {
            Object shown = converter.toControlValue(attribute.getValue());
            link.applying = true;
            try {
                control.setValue(shown);
                link.committedControl = shown;
            } finally {
                link.applying = false;
            }
            viewModel.clearBindingFailure(path);
        } catch (RuntimeException failure) {
            viewModel.reportBindingFailure(path, failureText(failure));
        }
    }

    private static void pushAttribute(Property<Object> control, Property<Object> attribute, Converter converter,
                                      ViewModel viewModel, String path, Link link) {
        try {
            Object converted = converter.toAttributeValue(control.getValue());
            link.applying = true;
            try {
                attribute.setValue(converted);
                link.committedControl = control.getValue();
            } finally {
                link.applying = false;
            }
            viewModel.clearBindingFailure(path);
        } catch (RuntimeException failure) {
            link.applying = true;
            try {
                control.setValue(link.committedControl);
            } finally {
                link.applying = false;
            }
            viewModel.reportBindingFailure(path, failureText(failure));
        }
    }

    private static String failureText(RuntimeException failure) {
        String text = failure.getMessage();
        if (text == null || text.isEmpty()) {
            return "Conversion failed";
        }
        return text;
    }

    /**
     * Guards one converted binding against hearing its own writes, and remembers the last
     * control value that was successfully shown.
     */
    private static final class Link {
        private boolean applying;
        private Object committedControl;
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
