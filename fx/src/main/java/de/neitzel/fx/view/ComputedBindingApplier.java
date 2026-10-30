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

    private static void watchControls(Parent root, Property<?> target, Runnable refresh) {
        watchNode(root, target, refresh);
    }

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

    private static Object readProperty(Object bean, Method method) {
        try {
            return method.invoke(bean);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Could not read property " + method.getName(), failure);
        }
    }

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

    private static String methodName(String methodValue) {
        if (methodValue != null && methodValue.startsWith("#")) {
            return methodValue.substring(1);
        }
        return methodValue;
    }
}
