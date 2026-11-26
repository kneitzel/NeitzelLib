package de.neitzel.fx.injection;

import de.neitzel.fx.view.ViewDependencies;
import de.neitzel.fx.view.ViewObject;
import de.neitzel.injection.ApplicationContext;
import de.neitzel.injection.MissingComponentException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Connects managed FX Views to an application-owned Neitzel injection context. */
public record NeitzelViewDependencies(ApplicationContext context, Set<Class<?>> components)
        implements ViewDependencies {

    /** Creates an adapter with the concrete classes used as FXML Components and rows. */
    public NeitzelViewDependencies {
        Objects.requireNonNull(context, "Application context");
        components = Set.copyOf(components);
    }

    @Override
    public boolean isComponent(Class<?> type) {
        return components.contains(type);
    }

    @Override
    public <T> Optional<T> findService(Class<T> type) {
        try {
            return Optional.of(context.getComponent(type));
        } catch (MissingComponentException missing) {
            if (missing.getComponentType() == type) return Optional.empty();
            throw missing;
        }
    }

    @Override
    public <T> ViewObject<T> createViewObject(Class<T> type) {
        T instance = context.createComponent(type);
        return new ViewObject<>(instance, () -> release(instance));
    }

    private static void release(Object instance) {
        if (instance instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (RuntimeException failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException("Could not close " + instance.getClass().getName(), failure);
            }
        }
    }
}
