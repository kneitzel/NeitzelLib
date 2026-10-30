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

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The object a View binds to, built at runtime for one Model instance.
 * <p>
 * Each read-write Attribute that is not declared on {@link Object} is stored as a JavaFX
 * property under the Attribute name. The property class follows the Attribute's declared type,
 * including when the current value is null. String, integer, long, float, double, and boolean
 * attributes use the matching JavaFX property. Any other declared type is an object property.
 * The property is set from the Attribute when the ViewModel is created. A null number or
 * boolean leaves the primitive property at its default. After that, a change of the property
 * writes the Attribute. A call to the entity's setter does not update the property.
 */
public class ViewModel {

    private final Object model;
    private final Map<String, Property<?>> properties = new HashMap<>();

    /**
     * Builds a ViewModel for one Model instance.
     *
     * @param model the Model instance
     */
    ViewModel(Object model) {
        this.model = model;
        for (PropertyDescriptor descriptor : descriptors(model.getClass())) {
            Method getter = descriptor.getReadMethod();
            Method setter = descriptor.getWriteMethod();
            if (getter == null || setter == null || descriptor.getPropertyType() == null) {
                continue;
            }
            String name = descriptor.getName();
            Property<?> property = createProperty(descriptor.getPropertyType(), read(getter, name));
            watch(property, setter, name);
            properties.put(name, property);
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

    private static List<PropertyDescriptor> descriptors(Class<?> type) {
        try {
            BeanInfo info = Introspector.getBeanInfo(type, Object.class);
            return List.of(info.getPropertyDescriptors());
        } catch (IntrospectionException failure) {
            throw new IllegalStateException("Could not introspect " + type.getName(), failure);
        }
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

    private void write(Method setter, String name, Object value) {
        try {
            setter.invoke(model, value);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Could not write attribute " + name, failure);
        }
    }
}
