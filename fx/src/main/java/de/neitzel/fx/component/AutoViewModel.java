package de.neitzel.fx.component;

import javafx.beans.property.*;
import java.lang.reflect.*;
import java.util.*;

public class AutoViewModel<T> {

    private final T model;
    private final Map<String, Property<?>> properties = new HashMap<>();

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
                prop.addListener((obs, oldVal, newVal) -> {
                    Method setter = findSetterFor(model.getClass(), fieldName, newVal != null ? newVal.getClass() : null);
                    if (setter != null) {
                        try {
                            setter.invoke(model, newVal);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }
    }

    public Property<?> getProperty(String name) {
        return properties.get(name);
    }

    public T getModel() {
        return model;
    }

    // ========== Hilfsmethoden ==========

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

    private String decapitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toLowerCase() + str.substring(1);
    }

    private Object invokeGetter(Method method) {
        try {
            return method.invoke(model);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
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
}
