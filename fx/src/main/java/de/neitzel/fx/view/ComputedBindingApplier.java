package de.neitzel.fx.view;

import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Writes a Controller method result into one control property and keeps it current.
 */
final class ComputedBindingApplier {

    /**
     * Prevents instantiation.
     */
    private ComputedBindingApplier() {
    }

    /**
     * Applies one Computed binding.
     * <p>
     * Calls the Controller method and writes the result to the control property. The method
     * runs again when a JavaFX property changes on a control in this View or on this View's
     * ViewModel, other than the target property. Writing the target does not call the method
     * again, so the write does not loop. The Model is not written.
     *
     * @param namespace  objects addressed by {@code fx:id}
     * @param viewModel  the ViewModel for this View
     * @param controller the Controller for this View
     * @param root       the scene-graph root of this View
     * @param binding    the declaration
     */
    static void apply(
            Map<String, Object> namespace,
            Object viewModel,
            Object controller,
            Parent root,
            ComputedBinding binding) {
        Object control = namespace.get(binding.getControl());
        if (control == null) {
            throw new IllegalArgumentException("No control \"" + binding.getControl() + "\"");
        }
        Property<Object> target = controlProperty(control, binding.getProperty());
        Runnable refresh = refresh(controller, binding.getMethod(), target);
        refresh.run();
        watchControls(root, target, refresh);
        if (viewModel instanceof ViewModel model) {
            model.addAttributeListener((observable, oldValue, newValue) -> refresh.run());
        }
    }

    /**
     * Returns a refresh that calls the Controller method and writes the result.
     * <p>
     * A refresh that is already running does not start again, so writing the target does not loop.
     *
     * @param controller  the Controller
     * @param methodValue the method, written as {@code #method}
     * @param target      the control property
     * @return the refresh
     */
    private static Runnable refresh(Object controller, String methodValue, Property<Object> target) {
        boolean[] running = {false};
        return () -> {
            if (running[0]) {
                return;
            }
            running[0] = true;
            try {
                target.setValue(invoke(controller, methodValue));
            } finally {
                running[0] = false;
            }
        };
    }

    /**
     * Watches JavaFX properties on controls under {@code root}, other than {@code target}.
     *
     * @param root    the scene-graph root
     * @param target  the property the method writes, which is not watched
     * @param refresh the refresh to run when a watched property changes
     */
    private static void watchControls(Parent root, Property<?> target, Runnable refresh) {
        watchNode(root, target, refresh);
    }

    /**
     * Watches one control and walks its children.
     *
     * @param node    the node
     * @param target  the property the method writes, which is not watched
     * @param refresh the refresh to run when a watched property changes
     */
    private static void watchNode(Node node, Property<?> target, Runnable refresh) {
        if (node instanceof Control control) {
            watchProperties(control, target, refresh);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                watchNode(child, target, refresh);
            }
        }
    }

    /**
     * Listens to every JavaFX property on {@code bean} except {@code target}.
     *
     * @param bean    the control
     * @param target  the property the method writes
     * @param refresh the refresh to run when a watched property changes
     */
    @SuppressWarnings("unchecked")
    private static void watchProperties(Object bean, Property<?> target, Runnable refresh) {
        ChangeListener<Object> listener = (observable, oldValue, newValue) -> refresh.run();
        for (Method method : bean.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || !method.getName().endsWith("Property")) {
                continue;
            }
            if (!ObservableValue.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            Object value = readProperty(bean, method);
            if (value instanceof ObservableValue<?> observable && observable != target) {
                ((ObservableValue<Object>) observable).addListener(listener);
            }
        }
    }

    /**
     * Invokes a no-argument property method.
     *
     * @param bean   the object that declares the property
     * @param method the property method
     * @return the property
     */
    private static Object readProperty(Object bean, Method method) {
        try {
            return method.invoke(bean);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Could not read property " + method.getName(), failure);
        }
    }

    /**
     * Returns the writable JavaFX property named on the control.
     *
     * @param control      the control
     * @param propertyName the property name, such as {@code text}
     * @return the property
     */
    @SuppressWarnings("unchecked")
    private static Property<Object> controlProperty(Object control, String propertyName) {
        String methodName = propertyName + "Property";
        try {
            Method method = control.getClass().getMethod(methodName);
            Object value = method.invoke(control);
            if (!(value instanceof Property<?> property)) {
                throw new IllegalArgumentException(methodName + " did not return a property");
            }
            return (Property<Object>) property;
        } catch (NoSuchMethodException failure) {
            throw new IllegalArgumentException(
                    "No property " + propertyName + " on " + control.getClass().getName(), failure);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Could not read property " + propertyName, failure);
        }
    }

    /**
     * Calls the Controller method named by {@code methodValue}.
     *
     * @param controller  the Controller
     * @param methodValue the method, written as {@code #method}
     * @return the method result
     */
    private static Object invoke(Object controller, String methodValue) {
        if (controller == null) {
            throw new IllegalArgumentException("No controller for " + methodValue);
        }
        String name = methodName(methodValue);
        try {
            Method method = controller.getClass().getMethod(name);
            return method.invoke(controller);
        } catch (NoSuchMethodException failure) {
            throw new IllegalArgumentException("No computed method \"" + methodValue + "\"", failure);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Could not call " + methodValue, failure);
        }
    }

    /**
     * Drops a leading {@code #} from an FXML method value.
     *
     * @param methodValue the method value
     * @return the method name
     */
    private static String methodName(String methodValue) {
        if (methodValue != null && methodValue.startsWith("#")) {
            return methodValue.substring(1);
        }
        return methodValue;
    }
}
