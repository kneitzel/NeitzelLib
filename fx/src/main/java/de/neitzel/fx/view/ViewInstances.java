package de.neitzel.fx.view;

import de.neitzel.fx.i18n.Localizer;

import java.util.Objects;
import java.util.Optional;

/** Instance selection shared by the FXML factories and the binding appliers. */
record ViewInstances(ViewDependencies dependencies) {

    ViewInstances {
        Objects.requireNonNull(dependencies, "View dependencies");
    }

    Object controller(Class<?> type, ViewLifetime lifetime) {
        return create(type, lifetime);
    }

    boolean isComponent(Class<?> type) {
        return dependencies.isComponent(type);
    }

    Object component(Class<?> type, ViewLifetime lifetime) {
        if (!dependencies.isComponent(type)) {
            throw new IllegalArgumentException("No Component registered for " + type.getName());
        }
        return createComponent(type, lifetime);
    }

    Object createComponent(Class<?> type, ViewLifetime lifetime) {
        Object instance = create(type, lifetime);
        if (instance.getClass() != type) {
            throw new IllegalArgumentException("Expected exact Component type " + type.getName()
                    + ", got " + instance.getClass().getName());
        }
        return instance;
    }

    private Object create(Class<?> type, ViewLifetime lifetime) {
        try {
            ViewObject<?> view = dependencies.createViewObject(type);
            // Each instance owns a distinct cleanup entry even if actions are shared.
            lifetime.own(() -> view.release().run());
            return type.cast(view.instance());
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Could not create View object " + type.getName(), failure);
        }
    }

    Object requiredService(Class<?> type) {
        return service(type)
                .orElseThrow(() -> new IllegalArgumentException("No View service for " + type.getName()));
    }

    Localizer localizer() {
        return service(Localizer.class).orElse(null);
    }

    private <T> Optional<T> service(Class<T> type) {
        try {
            return dependencies.findService(type);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Could not resolve View service " + type.getName(), failure);
        }
    }
}
