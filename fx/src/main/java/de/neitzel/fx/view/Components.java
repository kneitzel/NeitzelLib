package de.neitzel.fx.view;

import de.neitzel.injection.ApplicationContext;
import javafx.beans.property.Property;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.util.Builder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Loads a Component from {@code ClassName.fxml} in the Component's package.
 * <p>
 * The container creates the instance. The document uses {@code fx:root} with that instance as
 * the root and as the Controller. Bindings declared in the document wait until the model
 * property holds a non-null Model. {@link ViewModelClass} on the Component names the ViewModel
 * class constructed for that Model. A Component that implements {@link HasViewModel} is handed
 * that ViewModel after the property is set and before those bindings run.
 */
final class Components {

    /**
     * Prevents instantiation.
     */
    private Components() {
    }

    /**
     * Returns a builder for a type the container knows.
     * <p>
     * An unknown type, such as a JavaFX control, makes {@link ApplicationContext#getComponent}
     * throw {@link IllegalArgumentException}. This method returns null so FXML constructs that
     * type itself. A Component that extends a JavaFX class is also left to FXML when the document
     * names that JavaFX class: the container would otherwise hand back the Component. A failure
     * while creating a known Component is not caught. The first Component of a class in one load
     * is the cached singleton. A second Component of that class is a new injected instance, so the
     * document can show two of them.
     *
     * @param type    the type FXML is about to construct
     * @param context the container that creates Components
     * @return a builder for a Component, or {@code null} when the type is not a Component
     */
    static Builder<?> builder(Class<?> type, ApplicationContext context) {
        Object component;
        try {
            component = context.getComponent(type.asSubclass(Object.class));
        } catch (IllegalArgumentException notAComponent) {
            return null;
        }
        if (component.getClass() != type) {
            return null;
        }
        if (!LoadSession.claimFirst(type)) {
            component = context.createComponent(type.asSubclass(Object.class));
        }
        return new ComponentBuilder(component, context);
    }

    /**
     * Collects instance properties while FXML constructs an injected Component.
     * Properties are applied after its own document and model listener are ready.
     */
    private static final class ComponentBuilder extends AbstractMap<String, Object> implements Builder<Object> {
        /** The injected instance. */
        private final Object component;
        /** The context used to load its document. */
        private final ApplicationContext context;
        /** Values already resolved by the containing FXMLLoader. */
        private final Map<String, Object> values = new LinkedHashMap<>();

        /**
         * Creates a builder for an injected instance.
         * @param component the instance
         * @param context its dependency context
         */
        private ComponentBuilder(Object component, ApplicationContext context) {
            this.component = component;
            this.context = context;
        }

        /**
         * Records a property for application after Component initialization.
         * @param key the property name
         * @param value the resolved value
         * @return the previous value
         */
        @Override
        public Object put(String key, Object value) {
            return values.put(key, value);
        }

        /**
         * Returns the recorded properties.
         * @return the property entries
         */
        @Override
        public Set<Entry<String, Object>> entrySet() {
            return values.entrySet();
        }

        /**
         * Loads the Component and applies properties with standard FXML coercion.
         * @return the initialized Component
         */
        @Override
        public Object build() {
            Object result = load(component, context);
            if (!values.isEmpty()) {
                // A second fx:root load uses the public FXMLLoader API for bean setters,
                // enum/numeric coercion and diagnostics, without depending on BeanAdapter
                // internals or serializing values (which may be Model object references).
                FXMLLoader properties = new FXMLLoader();
                properties.setRoot(result);
                properties.setClassLoader(result.getClass().getClassLoader());
                StringBuilder document = new StringBuilder("<fx:root xmlns:fx=\"http://javafx.com/fxml/1\" type=\"")
                        .append(result.getClass().getName()).append('"');
                int index = 0;
                for (Entry<String, Object> entry : values.entrySet()) {
                    String variable = "componentValue" + index++;
                    properties.getNamespace().put(variable, entry.getValue());
                    document.append(' ').append(entry.getKey()).append("=\"$")
                            .append(variable).append('"');
                }
                document.append("/>");
                try {
                    properties.load(new ByteArrayInputStream(document.toString().getBytes(StandardCharsets.UTF_8)));
                } catch (IOException failure) {
                    throw new IllegalStateException("Could not apply Component properties to "
                            + result.getClass().getName(), failure);
                }
            }
            return result;
        }
    }

    /**
     * Creates a new Component and loads its document.
     * <p>
     * The instance is not the cached singleton. Its dependencies still come from
     * {@link ApplicationContext#getComponent}. Bindings wait until {@link #setModel} runs.
     *
     * @param type    the Component class
     * @param context the container that creates the instance and its dependencies
     * @return the loaded Component
     */
    static Object fresh(Class<?> type, ApplicationContext context) {
        Object component = context.createComponent(type.asSubclass(Object.class));
        return load(component, context);
    }

    /**
     * Sets the Component's model property.
     * <p>
     * A non-null value selects the ViewModel and then runs the Component's bindings.
     *
     * @param component the Component
     * @param model     the Model instance
     */
    static void setModel(Object component, Object model) {
        modelProperty(component).setValue(model);
    }

    /**
     * Loads {@code ClassName.fxml} with the Component as root and Controller.
     *
     * @param component the Component
     * @param context   the container that creates nested Components
     * @return the loaded Component
     */
    private static Object load(Object component, ApplicationContext context) {
        Class<?> type = component.getClass();
        String document = type.getSimpleName() + ".fxml";
        URL fxml = type.getResource(document);
        if (fxml == null) {
            throw new IllegalArgumentException("No component document " + document + " for " + type.getName());
        }
        LoadSession nested = LoadSession.nest();
        try {
            FXMLLoader loader = new FXMLLoader(fxml);
            loader.setRoot(component);
            loader.setController(component);
            loader.setBuilderFactory(nestedType -> builder(nestedType, context));
            loader.load();
            watchModel(component, List.copyOf(nested.bindingLists()), loader.getNamespace(), context);
            return component;
        } catch (IOException failure) {
            throw new IllegalStateException("Could not load component " + fxml, failure);
        } finally {
            LoadSession.unnest();
        }
    }

    /**
     * Runs the Component's bindings once, when {@code model} first becomes non-null.
     *
     * @param component the Component
     * @param lists     the binding lists captured from its document
     * @param namespace objects addressed by {@code fx:id}
     * @param context   the container that creates a named Converter
     */
    private static void watchModel(Object component, List<BindingList> lists, Map<String, Object> namespace,
                                   ApplicationContext context) {
        Property<Object> model = modelProperty(component);
        boolean[] applied = {false};
        model.addListener((observable, oldValue, newValue) -> {
            if (newValue == null || applied[0]) {
                return;
            }
            applied[0] = true;
            Object viewModel = ViewModels.of(newValue, ViewModels.namedOn(component.getClass()));
            if (component instanceof HasViewModel hasViewModel && viewModel instanceof ViewModel bound) {
                hasViewModel.setViewModel(bound);
            }
            Parent root = (Parent) component;
            for (BindingList list : lists) {
                list.apply(namespace, viewModel, component, root, context);
            }
        });
    }

    /**
     * Returns the Component's {@code model} property.
     *
     * @param component the Component
     * @return the property
     */
    @SuppressWarnings("unchecked")
    private static Property<Object> modelProperty(Object component) {
        try {
            Method method = component.getClass().getMethod("modelProperty");
            Object value = method.invoke(component);
            if (!(value instanceof Property<?> property)) {
                throw new IllegalArgumentException("modelProperty did not return a property");
            }
            return (Property<Object>) property;
        } catch (NoSuchMethodException failure) {
            throw new IllegalArgumentException("No model property on " + component.getClass().getName(), failure);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Could not read model property", failure);
        }
    }
}
