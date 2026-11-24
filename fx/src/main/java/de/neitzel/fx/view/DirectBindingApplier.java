package de.neitzel.fx.view;

import de.neitzel.injection.ApplicationContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import javafx.beans.InvalidationListener;
import javafx.beans.property.Property;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * Connects one Direct binding to a control property and a ViewModel Attribute.
 */
final class DirectBindingApplier {

    /**
     * Prevents instantiation.
     */
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
    static void apply(Map<String, Object> namespace, Object viewModel, DirectBinding binding,
                      ApplicationContext context, ViewLifetime lifetime) {
        Object control = namespace.get(binding.getControl());
        if (control == null) {
            throw new IllegalArgumentException("No control \"" + binding.getControl() + "\"");
        }
        selectComponentViewModel(control, viewModel, binding, lifetime);
        Resolved resolved = resolve(viewModel, binding.getPath());
        if (resolved == null) {
            return;
        }
        Property<?> controlProperty = controlProperty(control, binding.getProperty());
        if (!resolved.writable) {
            alignReadOnly(namespace, controlProperty, resolved, binding, context, lifetime);
            return;
        }
        Property<?> attribute = (Property<?>) resolved.attribute;
        if (resolved.complex) {
            switch (binding.getDirection()) {
                case "both" -> alignBothComplex(controlProperty, attribute, lifetime);
                case "view" -> alignTowardViewComplex(controlProperty, attribute, lifetime);
                case "model" -> alignTowardModelComplex(controlProperty, attribute, lifetime);
                default -> throw new IllegalArgumentException("Not a direction: " + binding.getDirection());
            }
            return;
        }
        if (attribute.getValue() instanceof ObservableList<?> values) {
            if ("view".equals(binding.getDirection())) alignTowardView(controlProperty, attribute, lifetime);
            else assignList(controlProperty, values, lifetime);
            return;
        }
        String converterName = binding.getConverter();
        if (converterName == null || converterName.isEmpty()) {
            alignPlain(controlProperty, attribute, binding.getDirection(), lifetime);
            return;
        }
        Converter converter = converter(context, converterName);
        switch (binding.getDirection()) {
            case "both" -> alignConverted(
                    controlProperty, attribute, converter, resolved.owner, resolved.name, true, true, lifetime);
            case "view" -> alignConverted(
                    controlProperty, attribute, converter, resolved.owner, resolved.name, true, false, lifetime);
            case "model" -> alignConverted(
                    controlProperty, attribute, converter, resolved.owner, resolved.name, false, true, lifetime);
            default -> throw new IllegalArgumentException("Not a direction: " + binding.getDirection());
        }
    }

    /** Connects a named observable to the View when its accessor exposes no writable property. */
    private static void alignReadOnly(Map<String, Object> namespace, Property<?> control, Resolved resolved,
                                      DirectBinding binding, ApplicationContext context, ViewLifetime lifetime) {
        if (!"view".equals(binding.getDirection())) {
            throw new IllegalArgumentException("Read-only source for path \"" + binding.getPath()
                    + "\" requires direction=\"view\" on " + binding.getControl() + "." + binding.getProperty()
                    + " in " + namespace.get(FXMLLoader.LOCATION_KEY));
        }
        String converterName = binding.getConverter();
        if (converterName == null || converterName.isEmpty()) {
            alignTowardView(control, resolved.attribute, lifetime);
        } else {
            alignConverted(control, resolved.attribute, converter(context, converterName),
                    resolved.owner, resolved.name, true, false, lifetime);
        }
    }

    /**
     * Aligns a plain Attribute in the named direction.
     *
     * @param controlProperty the control property
     * @param attribute       the Attribute
     * @param direction       {@code view}, {@code model}, or {@code both}
     */
    private static void alignPlain(Property<?> controlProperty, Property<?> attribute, String direction, ViewLifetime lifetime) {
        switch (direction) {
            case "both" -> alignBoth(controlProperty, attribute, lifetime);
            case "view" -> alignTowardView(controlProperty, attribute, lifetime);
            case "model" -> alignTowardModel(controlProperty, attribute, lifetime);
            default -> throw new IllegalArgumentException("Not a direction: " + direction);
        }
    }

    /**
     * The Attribute a Path names, the ViewModel that owns it, and whether that Attribute is complex.
     * <p>
     * {@code name} is the last segment. A conversion failure is recorded on {@code owner} with that
     * name as the source, so a parent only carries the extended copy.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Resolved {

        /**
         * The Attribute the Path names.
         */
        private final ObservableValue<?> attribute;

        /** Whether the Attribute resolves to a writable endpoint. */
        private final boolean writable;

        /**
         * Whether that Attribute is complex.
         */
        private final boolean complex;

        /**
         * The ViewModel that owns the Attribute. A conversion failure is recorded here.
         */
        private final ViewModel owner;

        /**
         * The last segment of the Path, used as the failure source.
         */
        private final String name;

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
    private static Resolved resolve(Object viewModel, String path) {
        String[] names = path.split("\\.", -1);
        Object current = viewModel;
        for (int index = 0; index < names.length - 1; index++) {
            if (!(current instanceof ViewModel stepModel)) {
                throw missing(path);
            }
            String name = names[index];
            Property<?> step = stepModel.property(name);
            if (step == null || !stepModel.isComplex(name)) {
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
        Property<?> property = propertyOf(current, last);
        ViewModel owner = current instanceof ViewModel model ? model : null;
        if (property != null) {
            return new Resolved(property, true, owner != null && owner.isComplex(last), owner, last);
        }
        ObservableValue<?> source = NamedProperties.getReadOnly(current, last);
        if (source == null) {
            throw missing(path);
        }
        return new Resolved(source, false, false, owner, last);
    }

    /**
     * Builds the ViewModel named on a Component before the Path is read.
     * <p>
     * The binding sets the Component's model property. Reading the Path would otherwise build a
     * generic ViewModel first and fix that class.
     *
     * @param control   the control named by the binding
     * @param viewModel the ViewModel for this View
     * @param binding   the declaration
     */
    private static void selectComponentViewModel(Object control, Object viewModel, DirectBinding binding, ViewLifetime lifetime) {
        if (!"model".equals(binding.getProperty())) {
            return;
        }
        Class<?> requested = ViewModels.namedOn(control.getClass());
        if (requested == null) {
            return;
        }
        Object instance = instanceAt(viewModel, binding.getPath());
        if (instance == null) {
            return;
        }
        ViewModels.of(lifetime.models(), instance, requested);
    }

    /**
     * Follows a Path through the Model instances behind the ViewModels.
     *
     * @param viewModel the ViewModel for this View
     * @param path      the Path
     * @return the Model instance at the end of the Path, or {@code null} when a step is null
     */
    private static Object instanceAt(Object viewModel, String path) {
        if (!(viewModel instanceof ViewModel root)) {
            return null;
        }
        Object current = root.model();
        for (String name : path.split("\\.", -1)) {
            if (current == null) {
                return null;
            }
            current = readAttribute(current, name);
        }
        return current;
    }

    /**
     * Reads one JavaBean attribute through {@code get} or {@code is}.
     *
     * @param bean the Model instance
     * @param name the attribute name
     * @return the current value
     */
    private static Object readAttribute(Object bean, String name) {
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String prefix : List.of("get", "is")) {
            try {
                Method method = bean.getClass().getMethod(prefix + suffix);
                return method.invoke(bean);
            } catch (NoSuchMethodException notThisPrefix) {
                continue;
            } catch (IllegalAccessException | InvocationTargetException failure) {
                throw new IllegalStateException("Could not read attribute " + name, failure);
            }
        }
        throw new IllegalArgumentException("No read-write attribute for path \"" + name + "\"");
    }

    /**
     * Returns the JavaFX property named on a ViewModel or a replacement.
     *
     * @param target the ViewModel or replacement
     * @param name   the property name
     * @return the property, or {@code null} when that name is not declared
     */
    private static Property<?> propertyOf(Object target, String name) {
        if (target instanceof ViewModel viewModel) {
            return viewModel.property(name);
        }
        return NamedProperties.get(target, name);
    }

    /**
     * Reports a Path that is not a read-write Attribute.
     *
     * @param path the Path
     * @return the failure
     */
    private static IllegalArgumentException missing(String path) {
        return new IllegalArgumentException("No read-write attribute for path \"" + path + "\"");
    }

    /**
     * Asks the container for the named Converter.
     *
     * @param context   the container
     * @param className the Converter class name
     * @return the Converter
     */
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
    private static void alignConverted(Property<?> controlProperty, ObservableValue<?> source, Converter converter,
                                       ViewModel owner, String sourceName, boolean towardView, boolean towardModel,
                                       ViewLifetime lifetime) {
        Property<Object> control = (Property<Object>) controlProperty;
        Link link = new Link();
        pushControl(control, source, converter, owner, sourceName, link);
        if (towardView) {
            lifetime.listen(source, (observable, oldValue, newValue) -> {
                if (!link.applying) {
                    pushControl(control, source, converter, owner, sourceName, link);
                }
            });
        }
        if (towardModel) {
            Property<Object> target = (Property<Object>) source;
            lifetime.listen(control, (observable, oldValue, newValue) -> {
                if (!link.applying) {
                    pushAttribute(control, target, converter, owner, sourceName, link);
                }
            });
        }
    }

    /**
     * Writes the converted Attribute onto the control. A failure keeps the previous control value.
     *
     * @param control   the control property
     * @param attribute the Attribute
     * @param converter the Converter
     * @param owner     the ViewModel that records a failure
     * @param source    the Attribute name used as the failure source
     * @param link      the guard against hearing this write
     */
    private static void pushControl(Property<Object> control, ObservableValue<?> attribute, Converter converter,
                                    ViewModel owner, String source, Link link) {
        try {
            Object shown = converter.toControlValue(attribute.getValue());
            link.applying = true;
            try {
                control.setValue(shown);
            } finally {
                link.applying = false;
            }
            if (owner != null) {
                owner.clearBindingFailure(source);
            }
        } catch (RuntimeException failure) {
            if (owner == null) {
                throw failure;
            }
            owner.reportBindingFailure(source, failureText(failure));
        }
    }

    /**
     * Writes the converted control value onto the Attribute. A failure keeps the previous Attribute.
     *
     * @param control   the control property
     * @param attribute the Attribute
     * @param converter the Converter
     * @param owner     the ViewModel that records a failure
     * @param source    the Attribute name used as the failure source
     * @param link      the guard against hearing this write
     */
    private static void pushAttribute(Property<Object> control, Property<Object> attribute, Converter converter,
                                      ViewModel owner, String source, Link link) {
        try {
            Object converted = converter.toAttributeValue(control.getValue());
            link.applying = true;
            try {
                attribute.setValue(converted);
            } finally {
                link.applying = false;
            }
            owner.clearBindingFailure(source);
        } catch (RuntimeException failure) {
            owner.reportBindingFailure(source, failureText(failure));
        }
    }

    /**
     * Returns the exception message, or a fallback when it is missing.
     *
     * @param failure the conversion failure
     * @return the text recorded on the ViewModel
     */
    private static String failureText(RuntimeException failure) {
        String text = failure.getMessage();
        if (text == null || text.isEmpty()) {
            return "Conversion failed";
        }
        return text;
    }

    /**
     * Guards one converted binding against hearing its own writes.
     */
    private static final class Link {

        /**
         * Whether this binding is currently writing one side, so the other listener stays quiet.
         */
        private boolean applying;
    }

    /**
     * Returns the writable JavaFX property named on the control.
     *
     * @param control      the control
     * @param propertyName the property name, such as {@code text}
     * @return the property
     */
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

    /**
     * Keeps a complex Attribute and the control aligned in both directions.
     * <p>
     * The control receives the Model instance. A later control value is stored as that instance's
     * ViewModel.
     *
     * @param controlProperty the control property
     * @param attribute       the complex Attribute
     */
    @SuppressWarnings("unchecked")
    private static void alignBothComplex(Property<?> controlProperty, Property<?> attribute, ViewLifetime lifetime) {
        Property<Object> control = (Property<Object>) controlProperty;
        Property<Object> source = (Property<Object>) attribute;
        control.setValue(modelBehind(source.getValue()));
        lifetime.listen(source, (observable, oldValue, newValue) -> {
            Object model = modelBehind(newValue);
            if (control.getValue() != model) {
                control.setValue(model);
            }
        });
        lifetime.listen(control, (observable, oldValue, newValue) -> {
            if (modelBehind(source.getValue()) == newValue) {
                return;
            }
            source.setValue(newValue == null ? null : ViewModels.of(lifetime.models(), newValue, null));
        });
    }

    /**
     * Shows a complex Attribute on the control and follows later changes. The control does not write back.
     *
     * @param controlProperty the control property
     * @param attribute       the complex Attribute
     */
    @SuppressWarnings("unchecked")
    private static void alignTowardViewComplex(Property<?> controlProperty, Property<?> attribute, ViewLifetime lifetime) {
        Property<Object> control = (Property<Object>) controlProperty;
        Property<Object> source = (Property<Object>) attribute;
        control.setValue(modelBehind(source.getValue()));
        lifetime.listen(source, (observable, oldValue, newValue) -> control.setValue(modelBehind(newValue)));
    }

    /**
     * Shows a complex Attribute once, then writes the Attribute when the control changes.
     *
     * @param controlProperty the control property
     * @param attribute       the complex Attribute
     */
    @SuppressWarnings("unchecked")
    private static void alignTowardModelComplex(Property<?> controlProperty, Property<?> attribute, ViewLifetime lifetime) {
        Property<Object> control = (Property<Object>) controlProperty;
        Property<Object> source = (Property<Object>) attribute;
        control.setValue(modelBehind(source.getValue()));
        lifetime.listen(control, (observable, oldValue, newValue) -> {
            if (modelBehind(source.getValue()) == newValue) {
                return;
            }
            source.setValue(newValue == null ? null : ViewModels.of(lifetime.models(), newValue, null));
        });
    }

    /**
     * Returns the Model instance stored in a complex Attribute.
     *
     * @param value the Attribute value, a ViewModel or {@code null}
     * @return the Model instance, or {@code null}
     */
    private static Object modelBehind(Object value) {
        if (value instanceof ViewModel viewModel) {
            return viewModel.model();
        }
        return null;
    }

    /**
     * Sets the control property to the shared list. Adds and removes are then visible on both sides.
     *
     * @param controlProperty the control property, such as {@code items}
     * @param values          the list the ViewModel and the Model share
     */
    @SuppressWarnings("unchecked")
    private static void assignList(Property<?> controlProperty, ObservableList<?> values, ViewLifetime lifetime) {
        Property<Object> target = (Property<Object>) controlProperty;
        Object previous = target.getValue();
        target.setValue(values);
        lifetime.own(() -> {
            if (target.getValue() == values) target.setValue(previous);
        });
    }

    /**
     * Binds the control and the Attribute in both directions.
     *
     * @param controlProperty the control property
     * @param attribute       the Attribute
     */
    @SuppressWarnings("unchecked")
    private static void alignBoth(Property<?> controlProperty, Property<?> attribute, ViewLifetime lifetime) {
        Property<Object> control = (Property<Object>) controlProperty;
        Property<Object> source = (Property<Object>) attribute;
        control.bindBidirectional(source);
        lifetime.own(() -> control.unbindBidirectional(source));
    }

    /**
     * Shows the Attribute and follows later changes. Edits to the control stay on the control.
     *
     * @param controlProperty the control property
     * @param attribute       the Attribute
     */
    @SuppressWarnings("unchecked")
    private static void alignTowardView(Property<?> controlProperty, ObservableValue<?> attribute,
                                        ViewLifetime lifetime) {
        Property<Object> control = (Property<Object>) controlProperty;
        Object previous = control.getValue();
        control.setValue(attribute.getValue());
        // ChangeListeners may suppress a new list whose elements equal the previous list.
        InvalidationListener follow = ignored -> control.setValue(attribute.getValue());
        attribute.addListener(follow);
        lifetime.own(() -> {
            attribute.removeListener(follow);
            if ((previous instanceof ObservableList<?> || control.getValue() instanceof ObservableList<?>)
                    && control.getValue() == attribute.getValue())
                control.setValue(previous);
        });
    }

    /**
     * Shows the current value, then writes the Attribute when the control changes.
     *
     * @param controlProperty the control property
     * @param attribute       the Attribute
     */
    @SuppressWarnings("unchecked")
    private static void alignTowardModel(Property<?> controlProperty, Property<?> attribute, ViewLifetime lifetime) {
        Property<Object> control = (Property<Object>) controlProperty;
        Property<Object> target = (Property<Object>) attribute;
        control.setValue(target.getValue());
        lifetime.listen(control, (observable, oldValue, newValue) -> target.setValue(newValue));
    }
}
