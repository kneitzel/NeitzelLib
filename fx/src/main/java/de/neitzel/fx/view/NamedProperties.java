package de.neitzel.fx.view;

import javafx.beans.property.Property;
import javafx.beans.value.ObservableValue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Reads a JavaFX property declared on an object by name.
 * <p>
 * A name {@code badge} is the no-argument method {@code badgeProperty}.
 */
final class NamedProperties {

    /**
     * Prevents instantiation.
     */
    private NamedProperties() {
    }

    /**
     * Returns the JavaFX property named on {@code bean}.
     *
     * @param bean the object that declares the property
     * @param name the property name
     * @return the property, or {@code null} when {@code bean} declares none of that name
     */
    static Property<?> get(Object bean, String name) {
        Object value = read(bean, name, true);
        return value instanceof Property<?> property ? property : null;
    }

    /**
     * Returns a named observable whose accessor exposes a read-only source.
     *
     * @param bean the object that declares the source
     * @param name the source name
     * @return the source or {@code null} when there is no such accessor or it returns {@code null}
     */
    static ObservableValue<?> getReadOnly(Object bean, String name) {
        Object value = read(bean, name, false);
        return value instanceof ObservableValue<?> source ? source : null;
    }

    private static Object read(Object bean, String name, boolean writable) {
        String methodName = name + "Property";
        try {
            Method method = bean.getClass().getMethod(methodName);
            Class<?> type = method.getReturnType();
            if (!ObservableValue.class.isAssignableFrom(type) || writable != Property.class.isAssignableFrom(type)) {
                return null;
            }
            return method.invoke(bean);
        } catch (NoSuchMethodException notDeclared) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Could not read property " + name, failure);
        }
    }
}
