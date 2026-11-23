package de.neitzel.fx.view;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.MenuItem;

final class CommandBindingApplier {

    private CommandBindingApplier() {
    }

    static void validate(List<BindingList> lists, Map<String, Object> namespace) {
        var commands = new IdentityHashMap<Object, CommandBinding>();
        var propertyBindings = new ArrayList<Object>();
        for (BindingList list : lists) {
            for (Object element : list.getElements()) {
                if (element instanceof CommandBinding binding) {
                    Object control = target(namespace, binding);
                    if (commands.put(control, binding) != null) {
                        throw invalid(namespace, binding, "duplicate owner for disable and onAction");
                    }
                } else if (element instanceof DirectBinding binding) {
                    addConflict(propertyBindings, namespace, binding.getControl(), binding.getProperty());
                } else if (element instanceof ComputedBinding binding) {
                    addConflict(propertyBindings, namespace, binding.getControl(), binding.getProperty());
                }
            }
        }
        for (Object control : propertyBindings) {
            CommandBinding command = commands.get(control);
            if (command != null) {
                throw invalid(namespace, command, "another binding owns disable or onAction");
            }
        }
    }

    private static void addConflict(List<Object> targets, Map<String, Object> namespace,
                                    String control, String property) {
        if ("disable".equals(property) || "onAction".equals(property)) {
            targets.add(namespace.get(control));
        }
    }

    static void apply(Map<String, Object> namespace, Object viewModel, CommandBinding binding,
                      ViewLifetime lifetime) {
        if (!lifetime.managed()) {
            throw invalid(namespace, binding, "CommandBinding requires a ViewModelContext");
        }
        var target = target(namespace, binding);
        var command = command(namespace, binding, viewModel);
        try {
            var registration = target instanceof ButtonBase button
                    ? CommandBindings.bind(button, command) : CommandBindings.bind((MenuItem) target, command);
            lifetime.own(registration::close);
        } catch (IllegalArgumentException failure) {
            throw invalid(namespace, binding, failure.getMessage());
        }
    }

    private static Object target(Map<String, Object> namespace, CommandBinding binding) {
        var target = namespace.get(binding.getControl());
        if (target == null) {
            throw invalid(namespace, binding, "unknown target fx:id");
        }
        if (!(target instanceof ButtonBase) && !(target instanceof MenuItem)) {
            throw invalid(namespace, binding, "target must be a ButtonBase or MenuItem");
        }
        return target;
    }

    private static Command command(Map<String, Object> namespace, CommandBinding binding, Object viewModel) {
        var path = binding.getPath();
        if (path == null || path.isBlank() || path.contains(".")) {
            throw invalid(namespace, binding, "expected a simple command path");
        }
        var getter = "get" + Character.toUpperCase(path.charAt(0)) + path.substring(1);
        try {
            var value = viewModel.getClass().getMethod(getter).invoke(viewModel);
            if (value instanceof Command command) {
                return command;
            }
            throw invalid(namespace, binding, getter + "() must return a Command instance");
        } catch (NoSuchMethodException | IllegalAccessException failure) {
            throw invalid(namespace, binding, "missing accessible command getter " + getter + "()");
        } catch (InvocationTargetException failure) {
            throw new IllegalArgumentException(
                    invalid(namespace, binding, "command getter " + getter + "() failed").getMessage(),
                    failure.getCause());
        }
    }

    private static IllegalArgumentException invalid(Map<String, Object> namespace, CommandBinding binding,
                                                     String reason) {
        return new IllegalArgumentException("Invalid CommandBinding in " + namespace.get(FXMLLoader.LOCATION_KEY)
                + " for " + binding.getControl() + " (disable/onAction), path " + binding.getPath() + ": " + reason);
    }
}
