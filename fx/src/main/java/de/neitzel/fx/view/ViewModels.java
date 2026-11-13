package de.neitzel.fx.view;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.IdentityHashMap;
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
    private static final Map<Object, Object> BY_INSTANCE = new IdentityHashMap<>();

    /**
     * Prevents instantiation.
     */
    private ViewModels() {
    }

    /**
     * Returns the ViewModel for a Model instance, building the generic one when none exists yet.
     *
     * @param model the Model instance
     * @return the ViewModel for that instance, or a replacement that exposes JavaFX properties by name
     */
    static Object of(Object model) {
        return of(model, null);
    }

    /**
     * Returns the ViewModel for a Model instance.
     * <p>
     * Builds {@code viewModelClass} with the Model when none exists yet. A null class, or
     * {@link ViewModel} itself, builds the generic ViewModel. The class may extend {@link ViewModel}
     * or be a replacement that exposes JavaFX properties by name. The container does not create it.
     * The first View fixes the class. A later View that names a different class fails. A later
     * View that names none reuses the ViewModel.
     *
     * @param model          the Model instance
     * @param viewModelClass the class named by {@link ViewModelClass}, or {@code null}
     * @return the ViewModel for that instance, or the replacement
     */
    static Object of(Object model, Class<?> viewModelClass) {
        Class<?> selected = ViewModelDefaults.select(model.getClass(), viewModelClass, model.getClass().getClassLoader());
        Object existing = BY_INSTANCE.get(model);
        if (existing != null) {
            if (viewModelClass != null && viewModelClass != ViewModel.class
                    && !selected.isInstance(existing)) {
                throw new IllegalArgumentException(
                        "ViewModel is " + existing.getClass().getName()
                                + " but the View names " + viewModelClass.getName());
            }
            return existing;
        }
        if (selected == ViewModel.class) return new ViewModel(model);
        return construct(model, selected);
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
     * A result that is not a {@link ViewModel} is registered here. A {@link ViewModel} registers
     * itself from its constructor, before its Attributes are walked.
     *
     * @param model          the Model instance
     * @param viewModelClass the class to construct
     * @return the constructed object
     */
    private static Object construct(Object model, Class<?> viewModelClass) {
        Constructor<?> constructor = constructor(model, viewModelClass);
        constructor.setAccessible(true);
        try {
            Object created = constructor.newInstance(model);
            if (!(created instanceof ViewModel)) {
                register(model, created);
            }
            return created;
        } catch (InvocationTargetException failure) {
            BY_INSTANCE.remove(model);
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

    /**
     * Records a ViewModel before its Attributes are walked, so a cycle reuses it.
     *
     * @param model     the Model instance
     * @param viewModel the ViewModel under construction
     */
    static void register(Object model, Object viewModel) {
        BY_INSTANCE.put(model, viewModel);
    }
}
