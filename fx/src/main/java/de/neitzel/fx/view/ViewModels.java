package de.neitzel.fx.view;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The ViewModels already built, one for each Model instance.
 * <p>
 * Instances are compared by identity, so two Models that are equal still get two ViewModels.
 */
final class ViewModels {

    private static final Map<Object, ViewModel> BY_INSTANCE = new IdentityHashMap<>();

    private ViewModels() {
    }

    /**
     * Returns the ViewModel for a Model instance, building it when none exists yet.
     *
     * @param model the Model instance
     * @return the ViewModel for that instance
     */
    static ViewModel of(Object model) {
        ViewModel existing = BY_INSTANCE.get(model);
        if (existing != null) {
            return existing;
        }
        return new ViewModel(model);
    }

    /**
     * Records a ViewModel before its Attributes are walked, so a cycle reuses it.
     *
     * @param model     the Model instance
     * @param viewModel the ViewModel under construction
     */
    static void register(Object model, ViewModel viewModel) {
        BY_INSTANCE.put(model, viewModel);
    }
}
