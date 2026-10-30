package de.neitzel.fx.view;

import de.neitzel.injection.ApplicationContext;
import javafx.beans.property.Property;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.util.Builder;

import java.io.IOException;
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

    private Components() {
    }

    /**
     * Returns a builder for a type the container knows.
     * <p>
     * An unknown type, such as a JavaFX control, makes {@link ApplicationContext#getComponent}
     * throw {@link IllegalArgumentException}. This method returns null so FXML constructs that
     * type itself. A Component that extends a JavaFX class is also left to FXML when the document
     * names that JavaFX class: the container would otherwise hand back the Component. A failure
     * while creating a known Component is not caught.
     *
     * @param type    the type FXML is about to construct
     * @param context the container that creates Components
     * @return a builder for a Component, or {@code null} when the type is not a Component
     */
    static Builder<?> builder(Class<?> type, ApplicationContext context) {
        Object component;
        try {
            component = context.getComponent(type);
        } catch (IllegalArgumentException notAComponent) {
            return null;
        }
        if (component.getClass() != type) {
            return null;
        }
        return () -> load(component, context);
    }

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
