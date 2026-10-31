package de.neitzel.fx.view;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Reads an Attribute's declared type from a Model.
 */
final class Attributes {

    /**
     * Prevents instantiation.
     */
    private Attributes() {
    }

    /**
     * Returns the element type of a collection Attribute.
     *
     * @param model the Model that holds the collection
     * @param name  the collection Attribute
     * @return the element class
     */
    static Class<?> elementType(Object model, String name) {
        Method reader = reader(model.getClass(), name);
        Class<?> element = classArgument(reader.getGenericReturnType());
        if (element == null) {
            throw new IllegalArgumentException("No element type for \"" + name + "\"");
        }
        return element;
    }

    /**
     * Returns the declared type of one Attribute.
     *
     * @param type the Model class
     * @param name the Attribute
     * @return the declared type
     */
    static Class<?> type(Class<?> type, String name) {
        return reader(type, name).getReturnType();
    }

    /**
     * Returns whether a Column can use this declared type.
     * <p>
     * A string, number, boolean, enum, or date or time is simple. A type with its own
     * Attributes is not.
     *
     * @param type the declared type
     * @return {@code true} when the type is a simple value
     */
    static boolean simple(Class<?> type) {
        if (type == String.class || type == Boolean.class || type == boolean.class || type.isEnum()) {
            return true;
        }
        if (type.isPrimitive()) {
            return type != char.class && type != void.class;
        }
        if (Number.class.isAssignableFrom(type)) {
            return true;
        }
        if (Date.class.isAssignableFrom(type) || Calendar.class.isAssignableFrom(type)) {
            return true;
        }
        return TemporalAccessor.class.isAssignableFrom(type) || TemporalAmount.class.isAssignableFrom(type);
    }

    /**
     * Returns the read method of a read-write Attribute.
     *
     * @param type the Model class
     * @param name the Attribute
     * @return the getter
     */
    private static Method reader(Class<?> type, String name) {
        for (PropertyDescriptor descriptor : descriptors(type)) {
            if (descriptor.getName().equals(name)
                    && descriptor.getReadMethod() != null
                    && descriptor.getWriteMethod() != null) {
                return descriptor.getReadMethod();
            }
        }
        throw new IllegalArgumentException("No attribute \"" + name + "\"");
    }

    /**
     * Returns the bean attributes declared on {@code type}.
     *
     * @param type the Model class
     * @return the attributes
     */
    private static List<PropertyDescriptor> descriptors(Class<?> type) {
        try {
            BeanInfo info = Introspector.getBeanInfo(type, Object.class);
            return List.of(info.getPropertyDescriptors());
        } catch (IntrospectionException failure) {
            throw new IllegalStateException("Could not introspect " + type.getName(), failure);
        }
    }

    /**
     * Returns the single type argument of a collection type.
     *
     * @param type the generic return type
     * @return the element class, or {@code null} when it cannot be read
     */
    private static Class<?> classArgument(Type type) {
        if (!(type instanceof ParameterizedType parameterized)
                || parameterized.getActualTypeArguments().length != 1) {
            return null;
        }
        Type argument = parameterized.getActualTypeArguments()[0];
        if (argument instanceof Class<?> raw) {
            return raw;
        }
        if (argument instanceof WildcardType wildcard && wildcard.getUpperBounds().length == 1) {
            Type bound = wildcard.getUpperBounds()[0];
            if (bound instanceof Class<?> raw) {
                return raw;
            }
        }
        return null;
    }
}
