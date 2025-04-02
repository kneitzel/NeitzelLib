package de.neitzel.fx.mvvm;

import javafx.beans.property.*;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.*;
import java.util.*;

/**
 * A generic ViewModel for JavaFX using reflection to automatically create properties for model attributes.
 * Supports binding between JavaFX controls and the model via JavaFX properties.
 *
 * @param <T> the type of the underlying model
 */
public class GenericViewModel<T> {
    /**
     * The original model instance.
     */
    private final T model;

    /**
     * A map holding JavaFX properties corresponding to the model's fields.
     */
    private final Map<String, Property<?>> properties = new HashMap<>();

    /**
     * Constructs a GenericViewModel for the given model instance.
     *
     * @param model the model to wrap
     */
    public GenericViewModel(T model) {
        this.model = model;
        initProperties();
    }

    /**
     * Initializes JavaFX properties for all readable and writable model fields using reflection.
     */
    private void initProperties() {
        Class<?> clazz = model.getClass();
        for (PropertyDescriptor pd : getPropertyDescriptors(clazz)) {
            Method getter = pd.getReadMethod();
            Method setter = pd.getWriteMethod();
            String name = pd.getName();

            if (getter != null && setter != null) {
                Class<?> propertyType = pd.getPropertyType();
                try {
                    Object value = getter.invoke(model);
                    Property<?> property = createJavaFXProperty(propertyType, value);
                    properties.put(name, property);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to initialize property: " + name, e);
                }
            }
        }
    }

    /**
     * Retrieves all readable/writable property descriptors of the given class.
     *
     * @param clazz the class to introspect
     * @return a list of property descriptors
     */
    private List<PropertyDescriptor> getPropertyDescriptors(Class<?> clazz) {
        try {
            BeanInfo info = Introspector.getBeanInfo(clazz, Object.class);
            return Arrays.asList(info.getPropertyDescriptors());
        } catch (IntrospectionException e) {
            throw new RuntimeException("Cannot introspect model class: " + clazz, e);
        }
    }

    /**
     * Creates an appropriate JavaFX Property instance for the given type and initial value.
     *
     * @param type the type of the property (e.g., String.class, int.class)
     * @param initialValue the initial value of the property
     * @return a new JavaFX Property instance
     */
    private Property<?> createJavaFXProperty(Class<?> type, Object initialValue) {
        if (type == String.class) return new SimpleStringProperty((String) initialValue);
        if (type == int.class || type == Integer.class) return new SimpleIntegerProperty((Integer) initialValue);
        if (type == double.class || type == Double.class) return new SimpleDoubleProperty((Double) initialValue);
        if (type == boolean.class || type == Boolean.class) return new SimpleBooleanProperty((Boolean) initialValue);
        if (type == long.class || type == Long.class) return new SimpleLongProperty((Long) initialValue);
        // Fallback for unsupported types:
        return new SimpleObjectProperty<>(initialValue);
    }

    /**
     * Retrieves the JavaFX property associated with the given name and type.
     *
     * @param propertyClass the expected property type (e.g., StringProperty.class)
     * @param name the name of the model field
     * @param <P> the type of the Property
     * @return the corresponding JavaFX property
     * @throws IllegalArgumentException if the property doesn't exist or the type mismatches
     */
    @SuppressWarnings("unchecked")
    public <P extends Property<?>> P property(Class<P> propertyClass, String name) {
        Property<?> prop = properties.get(name);
        if (prop == null) {
            throw new IllegalArgumentException("No property found for name: " + name);
        }
        if (!propertyClass.isInstance(prop)) {
            throw new IllegalArgumentException("Property type mismatch: expected " + propertyClass.getSimpleName());
        }
        return (P) prop;
    }

    /**
     * Writes the current values of all properties back into the model using their respective setters.
     */
    public void save() {
        for (PropertyDescriptor pd : getPropertyDescriptors(model.getClass())) {
            String name = pd.getName();
            Method setter = pd.getWriteMethod();
            if (setter != null && properties.containsKey(name)) {
                try {
                    Object value = properties.get(name).getValue();
                    setter.invoke(model, value);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to save property: " + name, e);
                }
            }
        }
    }

    /**
     * Returns the wrapped model instance.
     *
     * @return the model
     */
    public T getModel() {
        return model;
    }
}