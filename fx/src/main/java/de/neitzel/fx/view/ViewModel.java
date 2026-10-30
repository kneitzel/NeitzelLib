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
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

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
 * values stay simple even when that type has setters. A collection of Model instances is not a
 * Complex attribute. It is an object property whose value is an {@link ObservableList} of those
 * Model instances, not of ViewModels. A list, set, or collection of simple values is an object
 * property whose value is an {@link ObservableList} of those values. When either attribute's
 * current value is a list, the observable list is backed by it and stored on the entity when the
 * setter accepts it, so both sides hold one list. A cycle, or another Attribute that reaches the
 * same instance, reuses that ViewModel.
 * The property is set from the Attribute when the ViewModel is created. A null number or
 * boolean leaves the primitive property at its default. After that, a change of the property
 * writes the Attribute. A Complex attribute writes the Model instance behind the ViewModel.
 * A call to the entity's setter does not update the property.
 * <p>
 * Beside those attributes, the ViewModel keeps a list of {@link Message}s and the latest one.
 * The developer can add a message of any severity. Error and fatal messages are written to the log.
 * Info and warn are not. A parent carries each current child message, with the source extended by
 * the Attribute that leads to that child. Each further parent prepends its own Attribute. The
 * carried copy is not logged again. When the child drops the message, the parent drops that copy.
 * The name {@code latestMessage} is a string property holding the text of the latest Message, so a
 * Direct binding can show that text on a control. The library does not open a popup or fill a
 * status bar unless the developer asks. {@link MessagePopup#showNewMessages} shows each newly added
 * Message in a dialog. An in-place update does not.
 */
public class ViewModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(ViewModel.class);

    private final Object model;
    private final Map<String, Property<?>> properties = new HashMap<>();
    private final Set<String> complexAttributes = new HashSet<>();
    private final List<Message> messages = new ArrayList<>();
    private final Map<String, Message> bindingFailures = new HashMap<>();
    private final List<MessageListener> messageListeners = new ArrayList<>();
    private final Map<String, ChildMessageMirror> childMirrors = new HashMap<>();
    private final StringProperty latestMessageText = new SimpleStringProperty();
    private final List<Consumer<Message>> messageAdded = new ArrayList<>();
    private Message latestMessage;

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
                if (isSimpleValueCollection(getter) || isModelCollection(getter)) {
                    current = shareSimpleList(setter, name, current);
                }
                Property<?> property = createProperty(type, current);
                watch(property, setter, name);
                properties.put(name, property);
            }
        }
    }

    /**
     * Returns the JavaFX property for an Attribute name.
     * <p>
     * The name {@code latestMessage} is the text of the latest {@link Message}, beside the
     * Model attributes. It is a string property. A Direct binding can read it. An in-place
     * update of that Message refreshes the text and does not replace the Message.
     *
     * @param name the Attribute name, or {@code latestMessage}
     * @return the property, or {@code null} when that name is not a read-write Attribute
     *         and is not {@code latestMessage}
     */
    public Property<?> property(String name) {
        if ("latestMessage".equals(name)) {
            return latestMessageText;
        }
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
        if (property.getValue() instanceof ViewModel child) {
            attachChild(name, child);
        }
        property.addListener((observable, oldValue, newValue) -> {
            if (oldValue != newValue) {
                detachChild(name);
            }
            Object written = newValue instanceof ViewModel viewModel ? viewModel.model() : newValue;
            write(setter, name, written);
            if (oldValue != newValue && newValue instanceof ViewModel child) {
                attachChild(name, child);
            }
        });
    }

    /**
     * Carries the child ViewModel's current notices, extending each source with this Attribute.
     *
     * @param name  the Attribute that leads to the child
     * @param child the child ViewModel
     */
    private void attachChild(String name, ViewModel child) {
        childMirrors.put(name, ChildMessageMirror.attach(this, name, child));
    }

    /**
     * Drops the copies carried for an Attribute and stops following that child.
     *
     * @param name the Attribute
     */
    private void detachChild(String name) {
        ChildMessageMirror mirror = childMirrors.remove(name);
        if (mirror != null) {
            mirror.detach();
        }
    }

    private void write(Method setter, String name, Object value) {
        try {
            setter.invoke(model, value);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Could not write attribute " + name, failure);
        }
    }

    /**
     * Returns the notices on this ViewModel, beside the Model attributes.
     *
     * @return the messages, oldest first
     */
    public List<Message> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    /**
     * Returns the notice most recently added.
     * <p>
     * An in-place update of a binding failure does not change which notice is latest.
     *
     * @return the latest notice, or {@code null} when there is none
     */
    public Message getLatestMessage() {
        return latestMessage;
    }

    /**
     * Adds a notice of any severity.
     *
     * @param message the notice
     */
    public void addMessage(Message message) {
        messages.add(message);
        latestMessage = message;
        publishLatestMessage();
        logSevere(message);
        notifyAdded(message);
        for (Consumer<Message> listener : List.copyOf(messageAdded)) {
            listener.accept(message);
        }
    }

    /**
     * Calls {@code listener} each time a Message is added.
     * <p>
     * An in-place update of a binding failure does not call the listener.
     *
     * @param listener the listener
     */
    void whenMessageAdded(Consumer<Message> listener) {
        messageAdded.add(listener);
    }

    /**
     * Adds a copy of a child notice. The copy is not written to the log.
     *
     * @param copy the carried notice
     */
    void addCarriedMessage(Message copy) {
        messages.add(copy);
        latestMessage = copy;
        publishLatestMessage();
        notifyAdded(copy);
        for (Consumer<Message> listener : List.copyOf(messageAdded)) {
            listener.accept(copy);
        }
    }

    /**
     * Drops a carried copy. When it was the latest notice, the previous one becomes latest.
     *
     * @param copy the carried notice
     */
    void removeCarriedMessage(Message copy) {
        if (!messages.remove(copy)) {
            return;
        }
        if (latestMessage == copy) {
            latestMessage = messages.isEmpty() ? null : messages.get(messages.size() - 1);
            publishLatestMessage();
        }
        notifyRemoved(copy);
    }

    /**
     * Hears notices added on this ViewModel, including carried copies, and hears when they are
     * dropped or their text changes.
     *
     * @param listener the listener
     */
    void addMessageListener(MessageListener listener) {
        messageListeners.add(listener);
    }

    /**
     * Stops hearing notices added on this ViewModel.
     *
     * @param listener the listener
     */
    void removeMessageListener(MessageListener listener) {
        messageListeners.remove(listener);
    }

    /**
     * Tells listeners that a notice was added.
     *
     * @param message the notice
     */
    private void notifyAdded(Message message) {
        for (MessageListener listener : List.copyOf(messageListeners)) {
            listener.added(message);
        }
    }

    /**
     * Tells listeners that a notice kept its place and changed its text.
     * <p>
     * An in-place change does not log again and does not become a new latest notice.
     *
     * @param message the notice
     */
    void noteMessageText(Message message) {
        if (message == latestMessage) {
            publishLatestMessage();
        }
        notifyTextChanged(message);
    }

    /**
     * Tells listeners that a notice was dropped.
     *
     * @param message the notice
     */
    private void notifyRemoved(Message message) {
        for (MessageListener listener : List.copyOf(messageListeners)) {
            listener.removed(message);
        }
    }

    /**
     * Tells listeners that a notice kept its place and changed its text.
     *
     * @param message the notice
     */
    private void notifyTextChanged(Message message) {
        for (MessageListener listener : List.copyOf(messageListeners)) {
            listener.textChanged(message);
        }
    }

    /**
     * Records one error whose source is an Attribute name. A further failure of that Attribute
     * replaces the text of the same notice. Parents that carry the notice hear the new text.
     *
     * @param path the Attribute name that owns the failure
     * @param text the error text
     */
    void reportBindingFailure(String path, String text) {
        Message existing = bindingFailures.get(path);
        if (existing != null) {
            existing.setText(text);
            if (existing == latestMessage) {
                publishLatestMessage();
            }
            logSevere(existing);
            notifyTextChanged(existing);
            return;
        }
        Message message = new Message(Message.Severity.ERROR, text, path);
        bindingFailures.put(path, message);
        addMessage(message);
    }

    /**
     * Drops the error recorded for an Attribute after a later conversion succeeds.
     *
     * @param path the Attribute name that owned the failure
     */
    void clearBindingFailure(String path) {
        Message existing = bindingFailures.remove(path);
        if (existing == null) {
            return;
        }
        messages.remove(existing);
        if (latestMessage == existing) {
            latestMessage = messages.isEmpty() ? null : messages.get(messages.size() - 1);
            publishLatestMessage();
        }
        notifyRemoved(existing);
    }

    /**
     * Makes the string property for {@code latestMessage} match {@link #getLatestMessage()}.
     */
    private void publishLatestMessage() {
        latestMessageText.set(latestMessage == null ? null : latestMessage.getText());
    }

    /**
     * Writes an error or fatal notice to the log. Info and warn are left out.
     *
     * @param message the notice
     */
    private static void logSevere(Message message) {
        Message.Severity severity = message.getSeverity();
        if (severity == Message.Severity.ERROR || severity == Message.Severity.FATAL) {
            LOGGER.error(message.getText());
        }
    }

    private static boolean isSimpleValueCollection(Method getter) {
        if (!Collection.class.isAssignableFrom(getter.getReturnType())) {
            return false;
        }
        Class<?> element = elementType(getter.getGenericReturnType());
        return element != null && isSimpleValue(element);
    }

    private static boolean isModelCollection(Method getter) {
        if (!Collection.class.isAssignableFrom(getter.getReturnType())) {
            return false;
        }
        Class<?> element = elementType(getter.getGenericReturnType());
        return element != null && isComplex(element);
    }

    private static Class<?> elementType(Type type) {
        if (!(type instanceof ParameterizedType parameterized) || parameterized.getActualTypeArguments().length != 1) {
            return null;
        }
        return classOf(parameterized.getActualTypeArguments()[0]);
    }

    private static Class<?> classOf(Type type) {
        if (type instanceof Class<?> raw) {
            return raw;
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> raw) {
            return raw;
        }
        if (type instanceof WildcardType wildcard && wildcard.getUpperBounds().length == 1) {
            return classOf(wildcard.getUpperBounds()[0]);
        }
        return null;
    }

    private static boolean isSimpleValue(Class<?> type) {
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

    @SuppressWarnings("unchecked")
    private Object shareSimpleList(Method setter, String name, Object current) {
        if (!(current instanceof Collection<?> collection)) {
            return current;
        }
        ObservableList<Object> observable = current instanceof List<?> list
                ? FXCollections.observableList((List<Object>) list)
                : FXCollections.observableArrayList((Collection<Object>) collection);
        if (setter.getParameterTypes()[0].isAssignableFrom(observable.getClass())) {
            write(setter, name, observable);
        }
        return observable;
    }
}
