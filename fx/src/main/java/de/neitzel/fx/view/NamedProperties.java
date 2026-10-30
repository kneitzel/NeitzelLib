package de.neitzel.fx.view;

import javafx.beans.property.Property;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Reads a JavaFX property declared on an object by name.
 * <p>
 * A name {@code badge} is the no-argument method {@code badgeProperty}.
 */
final class NamedProperties {

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
        String methodName = name + "Property";
        try {
            Method method = bean.getClass().getMethod(methodName);
            if (!Property.class.isAssignableFrom(method.getReturnType())) {
                return null;
            }
            Object value = method.invoke(bean);
            if (value instanceof Property<?> property) {
                return property;
            }
            return null;
        } catch (NoSuchMethodException notDeclared) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Could not read property " + name, failure);
        }
    }
}
