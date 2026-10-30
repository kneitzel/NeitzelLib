package de.neitzel.fx.view;

import de.neitzel.injection.ApplicationContext;
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
     * @param context   the container that creates a named Converter
     */
    static void apply(Map<String, Object> namespace, ViewModel viewModel, DirectBinding binding,
                      ApplicationContext context) {
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
        if (attribute.getValue() instanceof ObservableList<?> values) {
            assignList(controlProperty, values);
            return;
        }
        String converterName = binding.getConverter();
        if (converterName == null || converterName.isEmpty()) {
            alignPlain(controlProperty, attribute, binding.getDirection());
            return;
        }
        Converter converter = converter(context, converterName);
        switch (binding.getDirection()) {
            case "both" -> alignConverted(
                    controlProperty, attribute, converter, resolved.owner, resolved.name, true, true);
            case "view" -> alignConverted(
                    controlProperty, attribute, converter, resolved.owner, resolved.name, true, false);
            case "model" -> alignConverted(
                    controlProperty, attribute, converter, resolved.owner, resolved.name, false, true);
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

    /**
     * The Attribute a Path names, the ViewModel that owns it, and whether that Attribute is complex.
     * <p>
     * {@code name} is the last segment. A conversion failure is recorded on {@code owner} with that
     * name as the source, so a parent only carries the extended copy.
     */
    private static final class Resolved {
        private final Property<?> attribute;
        private final boolean complex;
        private final ViewModel owner;
        private final String name;

        private Resolved(Property<?> attribute, boolean complex, ViewModel owner, String name) {
            this.attribute = attribute;
            this.complex = complex;
            this.owner = owner;
            this.name = name;
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
        return new Resolved(attribute, current.isComplex(last), current, last);
    }

    private static IllegalArgumentException missing(String path) {
        return new IllegalArgumentException("No read-write attribute for path \"" + path + "\"");
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

    /**
     * Aligns a converted Attribute. Failures are recorded on {@code owner} with {@code sourceName},
     * which is the last name of the Path.
     */
    @SuppressWarnings("unchecked")
    private static void alignConverted(Property<?> controlProperty, Property<?> attribute, Converter converter,
                                       ViewModel owner, String sourceName, boolean towardView, boolean towardModel) {
        Property<Object> control = (Property<Object>) controlProperty;
        Property<Object> source = (Property<Object>) attribute;
        Link link = new Link();
        pushControl(control, source, converter, owner, sourceName, link);
        if (towardView) {
            source.addListener((observable, oldValue, newValue) -> {
                if (!link.applying) {
                    pushControl(control, source, converter, owner, sourceName, link);
                }
            });
        }
        if (towardModel) {
            control.addListener((observable, oldValue, newValue) -> {
                if (!link.applying) {
                    pushAttribute(control, source, converter, owner, sourceName, link);
                }
            });
        }
    }

    private static void pushControl(Property<Object> control, Property<Object> attribute, Converter converter,
                                    ViewModel owner, String source, Link link) {
        try {
            Object shown = converter.toControlValue(attribute.getValue());
            link.applying = true;
            try {
                control.setValue(shown);
                link.committedControl = shown;
            } finally {
                link.applying = false;
            }
            owner.clearBindingFailure(source);
        } catch (RuntimeException failure) {
            owner.reportBindingFailure(source, failureText(failure));
        }
    }

    private static void pushAttribute(Property<Object> control, Property<Object> attribute, Converter converter,
                                      ViewModel owner, String source, Link link) {
        try {
            Object converted = converter.toAttributeValue(control.getValue());
            link.applying = true;
            try {
                attribute.setValue(converted);
                link.committedControl = control.getValue();
            } finally {
                link.applying = false;
            }
            owner.clearBindingFailure(source);
        } catch (RuntimeException failure) {
            link.applying = true;
            try {
                control.setValue(link.committedControl);
            } finally {
                link.applying = false;
            }
            owner.reportBindingFailure(source, failureText(failure));
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
