package de.neitzel.fx.view;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * The ViewModels already built, one for each Model instance.
 * <p>
 * Instances are compared by identity, so two Models that are equal still get two ViewModels.
 */
final class ViewModels {

    /**
     * ViewModels already built, keyed by Model identity.
     */
    static final ViewModelContext LEGACY = new ViewModelContext();

    // Preserve the single-argument constructor used by custom and generated ViewModels.
    private static final ThreadLocal<ViewModelContext> CONSTRUCTING = new ThreadLocal<>();

    /**
     * Prevents instantiation.
     */
    private ViewModels() {
    }

    static Object of(Object model) {
        return of(model, null);
    }

    static Object of(Object model, Class<?> viewModelClass) {
        return of(LEGACY, model, viewModelClass);
    }

    static ViewModelContext constructingContext() {
        ViewModelContext context = CONSTRUCTING.get();
        return context == null ? LEGACY : context;
    }

    /**
     * Gets or builds the ViewModel for this Model identity in the caller's context.
     */
    static Object of(ViewModelContext context, Object model, Class<?> viewModelClass) {
        Map<Object, Object> instances = context.viewModels();
        Class<?> selected = ViewModelDefaults.select(model.getClass(), viewModelClass, model.getClass().getClassLoader());
        Object existing = instances.get(model);
        if (existing != null) {
            if (viewModelClass != null && viewModelClass != ViewModel.class
                    && !selected.isInstance(existing)) {
                throw new IllegalArgumentException(
                        "ViewModel is " + existing.getClass().getName()
                                + " but the View names " + viewModelClass.getName());
            }
            return existing;
        }
        ViewModelContext previous = CONSTRUCTING.get();
        CONSTRUCTING.set(context);
        try {
            Object created = selected == ViewModel.class ? new ViewModel(model) : construct(model, selected);
            instances.put(model, created);
            return created;
        } catch (RuntimeException | Error failure) {
            instances.remove(model);
            throw failure;
        } finally {
            if (previous == null) {
                CONSTRUCTING.remove();
            } else {
                CONSTRUCTING.set(previous);
            }
        }
    }

    /**
     * Returns the ViewModel class named on a Controller or Component, or {@code null} when the
     * type names none.
     *
     * @param type the Controller or Component class
     * @return the named class, or {@code null}
     */
    static Class<?> namedOn(Class<?> type) {
        if (type == null) {
            return null;
        }
        ViewModelClass annotation = type.getAnnotation(ViewModelClass.class);
        if (annotation == null) {
            return null;
        }
        return annotation.value();
    }

    /**
     * Constructs a ViewModel or a replacement with the Model instance.
     * <p>
     * A {@link ViewModel} registers itself from its constructor, before its Attributes are walked.
     *
     * @param model          the Model instance
     * @param viewModelClass the class to construct
     * @return the constructed object
     */
    private static Object construct(Object model, Class<?> viewModelClass) {
        Constructor<?> constructor = constructor(model, viewModelClass);
        constructor.setAccessible(true);
        try {
            return constructor.newInstance(model);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Could not construct " + viewModelClass.getName(), failure);
        } catch (IllegalAccessException | InstantiationException failure) {
            throw new IllegalStateException("Could not construct " + viewModelClass.getName(), failure);
        }
    }

    /**
     * Returns the constructor that takes the Model, preferring the most specific parameter type.
     *
     * @param model          the Model instance
     * @param viewModelClass the class to construct
     * @return the constructor
     */
    private static Constructor<?> constructor(Object model, Class<?> viewModelClass) {
        Constructor<?> chosen = null;
        for (Constructor<?> candidate : viewModelClass.getDeclaredConstructors()) {
            if (candidate.getParameterCount() != 1) {
                continue;
            }
            Class<?> parameter = candidate.getParameterTypes()[0];
            if (!parameter.isAssignableFrom(model.getClass())) {
                continue;
            }
            if (chosen == null || chosen.getParameterTypes()[0].isAssignableFrom(parameter)) {
                chosen = candidate;
            }
        }
        if (chosen == null) {
            throw new IllegalArgumentException(
                    "No constructor taking the Model on " + viewModelClass.getName());
        }
        return chosen;
    }

}
