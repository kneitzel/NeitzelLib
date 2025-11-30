package de.neitzel.fx.component;

import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AutoViewModel automatically exposes JavaFX properties for all readable/writable fields
 * of a given POJO model. It creates appropriate Property instances and keeps them in a map
 * for lookup by field name.
 *
 * @param <T> the type of the underlying model
 */
@SuppressWarnings("unused")
public class AutoViewModel<T> {

    private static final Logger LOGGER = Logger.getLogger(AutoViewModel.class.getName());

    /**
     * The wrapped model instance.
     */
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
                            LOGGER.log(Level.WARNING, "Failed to invoke setter for field " + fieldName, e);
                        }
                    }
                });
            }
        }
    }

    private boolean isGetter(Method method) {
        return Modifier.isPublic(method.getModifiers())
                && method.getParameterCount() == 0
                && !method.getReturnType().equals(void.class)
                && (method.getName().startsWith("get") || method.getName().startsWith("is"));
    }

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

    private Object invokeGetter(Method method) {
        try {
            return method.invoke(model);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to invoke getter: " + method.getName(), e);
            return null;
        }
    }

    private Property<?> toProperty(Object value) {
        if (value instanceof String s) return new SimpleStringProperty(s);
        if (value instanceof Integer i) return new SimpleIntegerProperty(i);
        if (value instanceof Boolean b) return new SimpleBooleanProperty(b);
        if (value instanceof Double d) return new SimpleDoubleProperty(d);
        if (value instanceof Float f) return new SimpleFloatProperty(f);
        if (value instanceof Long l) return new SimpleLongProperty(l);
        return new SimpleObjectProperty<>(value);
    }

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

    private String decapitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toLowerCase() + str.substring(1);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    public Property<?> getProperty(String name) {
        return properties.get(name);
    }

    public T getModel() {
        return model;
    }
}
