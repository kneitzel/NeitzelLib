package de.neitzel.fx.view;

/**
 * Receives the ViewModel selected for a Controller or a Component.
 * <p>
 * A Component is called after its model property is set to a non-null Model and before that
 * Component's bindings run.
 */
public interface HasViewModel {

    /**
     * Receives the ViewModel for the Model this object is bound to.
     *
     * @param viewModel the selected ViewModel
     */
    void setViewModel(ViewModel viewModel);
}
