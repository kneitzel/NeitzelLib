package de.neitzel.fx.view;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.beans.property.Property;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.util.Builder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

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

    /** Returns a Component builder or leaves the requested type to JavaFX. */
    static Builder<?> builder(Class<?> type, ViewInstances context, ViewLifetime owner) {
        return context.isComponent(type) ? new ComponentBuilder(type, context, owner.child()) : null;
    }

    /**
     * Collects instance properties while FXML constructs an injected Component.
     * Properties are applied after its own document and model listener are ready.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class ComponentBuilder extends AbstractMap<String, Object> implements Builder<Object> {
        /** The exact Component class declared by FXML. */
        private final Class<?> type;
        /** The context used to load its document. */
        private final ViewInstances context;
        private final ViewLifetime lifetime;
        /** Values already resolved by the containing FXMLLoader. */
        private final Map<String, Object> values = new LinkedHashMap<>();

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
            Object result = load(context.createComponent(type, lifetime), context, lifetime);
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

    /** Creates a fresh Component and loads its document before setting its Model. */
    static Object fresh(Class<?> type, ViewInstances context, ViewLifetime lifetime) {
        return load(context.component(type, lifetime), context, lifetime);
    }

    /**
     * Sets the Component's model property.
     * <p>
     * A non-null value selects the ViewModel and then runs the Component's bindings.
     *
     * @param component the Component
     * @param model     the Model instance
     */
    static void setModel(Object component, Object model, ViewLifetime lifetime) {
        LoadSession session = LoadSession.nest();
        try {
            modelProperty(component).setValue(model);
            session.checkFailure();
        } finally {
            LoadSession.unnest();
        }
    }

    /**
     * Loads {@code ClassName.fxml} with the Component as root and Controller.
     *
     * @param component the Component
     * @param context   the container that creates nested Components
     * @return the loaded Component
     */
    private static Object load(Object component, ViewInstances context, ViewLifetime lifetime) {
        Class<?> type = component.getClass();
        String document = type.getSimpleName() + ".fxml";
        URL fxml = type.getResource(document);
        if (fxml == null) {
            throw new IllegalArgumentException("No component document " + document + " for " + type.getName());
        }
        LoadSession nested = LoadSession.nest();
        try {
            FXMLLoader loader = new FXMLLoader(fxml, ViewLoader.resources(context, fxml, type));
            FxmlHandlers.observe(loader, lifetime);
            loader.setRoot(component);
            loader.setController(component);
            loader.setControllerFactory(nestedType -> context.controller(nestedType, lifetime));
            loader.setBuilderFactory(nestedType -> builder(nestedType, context, lifetime));
            loader.load();
            nested.checkFailure();
            if (component instanceof Node node) {
                node.getProperties().put(Components.class, Boolean.TRUE);
                lifetime.own(() -> node.getProperties().remove(Components.class));
            }
            watchModel(component, List.copyOf(nested.bindingLists()), loader.getNamespace(), context, lifetime);
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
                                   ViewInstances context, ViewLifetime lifetime) {
        Property<Object> model = modelProperty(component);
        boolean[] applied = {false};
        lifetime.listen(model, (observable, oldValue, newValue) -> {
            if (newValue == null || applied[0]) {
                return;
            }
            applied[0] = true;
            try {
                Object viewModel = ViewModels.of(lifetime.models(), newValue, ViewModels.namedOn(component.getClass()));
                if (component instanceof HasViewModel hasViewModel && viewModel instanceof ViewModel bound) {
                    hasViewModel.setViewModel(bound);
                }
                Parent root = (Parent) component;
                CommandBindingApplier.validate(lists, namespace);
                for (BindingList list : lists) {
                    list.apply(namespace, viewModel, component, root, context, lifetime);
                }
                Table.openAll(root, viewModel, context, lifetime);
            } catch (RuntimeException | Error failure) {
                lifetime.rollback(failure);
                if (!LoadSession.recordFailure(failure)) {
                    throw failure;
                }
            }
        });
    }

    static boolean ownsBindings(Node node) {
        return node.hasProperties() && node.getProperties().containsKey(Components.class);
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
