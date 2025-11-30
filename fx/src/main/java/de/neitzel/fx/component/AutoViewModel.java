package de.neitzel.fx.component;

import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * AutoViewModel automatically exposes JavaFX properties for all readable/writable fields
 * of a given POJO model. It creates appropriate Property instances and keeps them in a map
 * for lookup by field name.
 *
 * @param <T> the type of the underlying model
 */
@Slf4j
public class AutoViewModel<T> {

    /**
     * The wrapped model instance.
     * -- GETTER --
     * Retrieves the model associated with this AutoViewModel.
     */
    @Getter
    private final T model;

    /**
     * Map of field name to JavaFX Property instance exposing that field's value.
     */
    private final Map<String, Property<?>> properties = new HashMap<>();

    /**
     * Constructs an AutoViewModel for the provided model and initializes properties via reflection.
     *
     * @param model the POJO model whose getters/setters are used to create JavaFX properties
     */
    public AutoViewModel(T model) {
        this.model = model;
        initProperties();
    }

    /**
     * Initializes a mapping of field names to JavaFX properties for the associated model object.
     * <p>
     * This method utilizes reflection to iterate through the public no-argument getters
     * of the model's class to create corresponding JavaFX properties. It performs the following:
     * <p>
     * 1. Identifies methods that adhere to the getter naming conventions (e.g., `getFieldName` or `isFieldName`).
     * 2. Maps the field name derived from the getter method to a JavaFX property, creating an appropriate
     * property type based on the getter's return type.
     * 3. Adds a listener to each property to bind updates from the property back to the model object
     * using the corresponding setter method, if available. This ensures bi-directional synchronization
     * between the ViewModel and the underlying model.
     * <p>
     * Exceptions that may occur during reflective operations (e.g., invoking methods) are caught
     * and logged to avoid runtime failures.
     */
    private void initProperties() {
        for (Method getter : model.getClass().getMethods()) {
            if (isGetter(getter)) {
                String fieldName = getFieldName(getter);
                Object value = invokeGetter(getter);

                Property<?> prop = toProperty(value);
                properties.put(fieldName, prop);

                // Bind ViewModel → Model
                prop.addListener((_obs, _oldVal, newVal) -> {
                    // use _obs and _oldVal to satisfy static analysis (they are intentionally retained)
                    if (_oldVal != null && _oldVal.equals(newVal)) {
                        // no-op: values unchanged
                    }
                    Method setter = findSetterFor(model.getClass(), fieldName, newVal != null ? newVal.getClass() : null);
                    if (setter != null) {
                        try {
                            setter.invoke(model, newVal);
                        } catch (Exception e) {
                            log.warn("Failed to invoke setter for field {}", fieldName, e);
                        }
                    }
                });
            }
        }
    }

    /**
     * Determines if a given method follows the JavaBean getter convention.
     * The method must be public, take no parameters, have a non-void return type,
     * and its name should start with either "get" or "is".
     *
     * @param method the method to be assessed
     * @return true if the method adheres to the JavaBean getter convention, false otherwise
     */
    private boolean isGetter(Method method) {
        return Modifier.isPublic(method.getModifiers())
                && method.getParameterCount() == 0
                && !method.getReturnType().equals(void.class)
                && (method.getName().startsWith("get") || method.getName().startsWith("is"));
    }

    /**
     * Derives the field name from a given Java method by following JavaBean naming conventions.
     * The method's name is analyzed to strip the "get" or "is" prefix (if present),
     * and the result is converted into a decapitalized field name.
     *
     * @param method the method whose name is to be processed to derive the field name
     * @return the derived field name, or the original method name if no "get" or "is" prefix is found
     */
    private String getFieldName(Method method) {
        String name = method.getName();
        if (name.startsWith("get")) {
            return decapitalize(name.substring(3));
        } else if (name.startsWith("is")) {
            return decapitalize(name.substring(2));
        }
        return name;
    }

    // ========== Hilfsmethoden ==========

    /**
     * Invokes the specified method on the model object and returns the result.
     * If the method invocation fails, logs a warning and returns null.
     *
     * @param method the method to be invoked, typically a JavaBean getter
     * @return the result of invoking the method, or null if an error occurs
     */
    private Object invokeGetter(Method method) {
        try {
            return method.invoke(model);
        } catch (Exception e) {
            log.warn("Failed to invoke getter: {}", method.getName(), e);
            return null;
        }
    }

    /**
     * Converts an object into an appropriate JavaFX {@link Property} based on its type.
     *
     * @param value the object to be converted into a JavaFX property; can be of type String, Integer,
     *              Boolean, Double, Float, Long, or any other object
     * @return a JavaFX {@link Property} corresponding to the type of the input object, such as
     * {@link SimpleStringProperty} for String, {@link SimpleIntegerProperty} for Integer,
     * {@link SimpleBooleanProperty} for Boolean, and so on. If the type is not explicitly
     * handled, a {@link SimpleObjectProperty} is returned wrapping the object
     */
    private Property<?> toProperty(Object value) {
        if (value instanceof String s) return new SimpleStringProperty(s);
        if (value instanceof Integer i) return new SimpleIntegerProperty(i);
        if (value instanceof Boolean b) return new SimpleBooleanProperty(b);
        if (value instanceof Double d) return new SimpleDoubleProperty(d);
        if (value instanceof Float f) return new SimpleFloatProperty(f);
        if (value instanceof Long l) return new SimpleLongProperty(l);
        return new SimpleObjectProperty<>(value);
    }

    /**
     * Finds a setter method in a specified class that corresponds to the given field name and value type.
     * <p>
     * The method searches for a public method in the class whose name matches the JavaBean-style setter
     * naming convention (e.g., "setFieldName"). The method must accept exactly one parameter, and if a
     * value type is provided, the parameter type must be assignable from it.
     *
     * @param clazz     the class to search for the method
     * @param fieldName the name of the field for which the setter method is being sought
     * @param valueType the expected type of the parameter for the setter method, or null if no specific
     *                  type constraint is required
     * @return the setter method if found, or null if no matching method is identified
     */
    private Method findSetterFor(Class<?> clazz, String fieldName, Class<?> valueType) {
        String setterName = "set" + capitalize(fieldName);
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(setterName) && m.getParameterCount() == 1) {
                if (valueType == null || m.getParameterTypes()[0].isAssignableFrom(valueType)) {
                    return m;
                }
            }
        }
        return null;
    }

    /**
     * Converts the first character of the given string to lowercase, leaving the rest of the string unchanged.
     * If the input string is null or empty, it is returned as-is.
     *
     * @param str the string to be decapitalized
     * @return the decapitalized string, or the original string if it is null or empty
     */
    private String decapitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toLowerCase() + str.substring(1);
    }

    /**
     * Capitalizes the first letter of the given string and leaves the rest of the string unchanged.
     * If the input string is null or empty, it is returned as is.
     *
     * @param str the string to be capitalized
     * @return the input string with its first character converted to uppercase, or the original string if it is null or empty
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Retrieves the JavaFX property associated with the given field name.
     *
     * @param name the name of the field whose associated JavaFX property is to be returned
     * @return the JavaFX {@link Property} associated with the specified field name,
     * or null if no property exists for the given name
     */
    public Property<?> getProperty(String name) {
        return properties.get(name);
    }
}
