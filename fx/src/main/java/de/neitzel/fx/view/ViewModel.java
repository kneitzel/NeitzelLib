package de.neitzel.fx.view;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The object a View binds to, built at runtime for one Model instance.
 * <p>
 * Each read-write Attribute that is not declared on {@link Object} is stored as a JavaFX
 * property under the Attribute name. The property class follows the Attribute's declared type,
 * including when the current value is null. String, integer, long, float, double, and boolean
 * attributes use the matching JavaFX property. Any other declared type is an object property.
 * A Complex attribute, a read-write Attribute whose declared type itself has a read-write
 * Attribute, stores the ViewModel for the nested Model instance, or null when the Attribute
 * is null. Strings, numbers, booleans, enums, {@link Date}, {@link Calendar}, and {@code java.time}
 * values stay simple even when that type has setters. A collection is not a Complex attribute.
 * A cycle, or another Attribute that reaches the same instance, reuses that ViewModel.
 * The property is set from the Attribute when the ViewModel is created. A null number or
 * boolean leaves the primitive property at its default. After that, a change of the property
 * writes the Attribute. A Complex attribute writes the Model instance behind the ViewModel.
 * A call to the entity's setter does not update the property.
 */
public class ViewModel {

    private final Object model;
    private final Map<String, Property<?>> properties = new HashMap<>();
    private final Set<String> complexAttributes = new HashSet<>();

    /**
     * Builds a ViewModel for one Model instance.
     *
     * @param model the Model instance
     */
    ViewModel(Object model) {
        this.model = model;
        ViewModels.register(model, this);
        for (PropertyDescriptor descriptor : descriptors(model.getClass())) {
            Method getter = descriptor.getReadMethod();
            Method setter = descriptor.getWriteMethod();
            if (getter == null || setter == null || descriptor.getPropertyType() == null) {
                continue;
            }
            String name = descriptor.getName();
            Class<?> type = descriptor.getPropertyType();
            Object current = read(getter, name);
            if (isComplex(type)) {
                Property<?> property = new SimpleObjectProperty<>(
                        current == null ? null : ViewModels.of(current));
                watchComplex(property, setter, name);
                properties.put(name, property);
                complexAttributes.add(name);
            } else {
                Property<?> property = createProperty(type, current);
                watch(property, setter, name);
                properties.put(name, property);
            }
        }
    }

    /**
     * Returns the JavaFX property for an Attribute name.
     *
     * @param name the Attribute name
     * @return the property, or {@code null} when that name is not a read-write Attribute
     */
    public Property<?> property(String name) {
        return properties.get(name);
    }

    /**
     * Returns the Model instance this ViewModel was built for.
     *
     * @return the Model instance
     */
    Object model() {
        return model;
    }

    /**
     * Reports whether an Attribute is complex.
     *
     * @param name the Attribute name
     * @return {@code true} when that Attribute holds a nested ViewModel
     */
    boolean isComplex(String name) {
        return complexAttributes.contains(name);
    }

    /**
     * Calls {@code listener} when any Attribute property changes.
     *
     * @param listener the listener
     */
    @SuppressWarnings("unchecked")
    void addAttributeListener(ChangeListener<Object> listener) {
        for (Property<?> property : properties.values()) {
            ((Property<Object>) property).addListener(listener);
        }
    }

    private static List<PropertyDescriptor> descriptors(Class<?> type) {
        try {
            BeanInfo info = Introspector.getBeanInfo(type, Object.class);
            return List.of(info.getPropertyDescriptors());
        } catch (IntrospectionException failure) {
            throw new IllegalStateException("Could not introspect " + type.getName(), failure);
        }
    }

    private static boolean isComplex(Class<?> type) {
        if (simpleValue(type)) {
            return false;
        }
        for (PropertyDescriptor descriptor : descriptors(type)) {
            if (descriptor.getReadMethod() != null
                    && descriptor.getWriteMethod() != null
                    && descriptor.getPropertyType() != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean simpleValue(Class<?> type) {
        if (type.isPrimitive() || type.isEnum() || type.isArray()
                || type == String.class || type == Boolean.class) {
            return true;
        }
        if (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
            return true;
        }
        if (Number.class.isAssignableFrom(type)) {
            return true;
        }
        if (Date.class.isAssignableFrom(type) || Calendar.class.isAssignableFrom(type)) {
            return true;
        }
        Package packaged = type.getPackage();
        return packaged != null && packaged.getName().startsWith("java.time");
    }

    private static Property<?> createProperty(Class<?> type, Object value) {
        if (type == String.class) {
            return new SimpleStringProperty((String) value);
        }
        if (type == Integer.class || type == int.class) {
            return integerProperty(value);
        }
        if (type == Long.class || type == long.class) {
            return longProperty(value);
        }
        if (type == Float.class || type == float.class) {
            return floatProperty(value);
        }
        if (type == Double.class || type == double.class) {
            return doubleProperty(value);
        }
        if (type == Boolean.class || type == boolean.class) {
            return booleanProperty(value);
        }
        return new SimpleObjectProperty<>(value);
    }

    private static IntegerProperty integerProperty(Object value) {
        SimpleIntegerProperty property = new SimpleIntegerProperty();
        if (value != null) {
            property.setValue((Integer) value);
        }
        return property;
    }

    private static LongProperty longProperty(Object value) {
        SimpleLongProperty property = new SimpleLongProperty();
        if (value != null) {
            property.setValue((Long) value);
        }
        return property;
    }

    private static FloatProperty floatProperty(Object value) {
        SimpleFloatProperty property = new SimpleFloatProperty();
        if (value != null) {
            property.setValue((Float) value);
        }
        return property;
    }

    private static DoubleProperty doubleProperty(Object value) {
        SimpleDoubleProperty property = new SimpleDoubleProperty();
        if (value != null) {
            property.setValue((Double) value);
        }
        return property;
    }

    private static BooleanProperty booleanProperty(Object value) {
        SimpleBooleanProperty property = new SimpleBooleanProperty();
        if (value != null) {
            property.setValue((Boolean) value);
        }
        return property;
    }

    private Object read(Method getter, String name) {
        try {
            return getter.invoke(model);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Could not read attribute " + name, failure);
        }
    }

    private void watch(Property<?> property, Method setter, String name) {
        property.addListener((observable, oldValue, newValue) -> write(setter, name, newValue));
    }

    private void watchComplex(Property<?> property, Method setter, String name) {
        property.addListener((observable, oldValue, newValue) -> {
            Object written = newValue instanceof ViewModel viewModel ? viewModel.model() : newValue;
            write(setter, name, written);
        });
    }

    private void write(Method setter, String name, Object value) {
        try {
            setter.invoke(model, value);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Could not write attribute " + name, failure);
        }
    }
}
