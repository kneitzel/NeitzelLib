package de.neitzel.fx.view;

/**
 * Controller used when a document names none.
 * <p>
 * A View with only Direct bindings still loads. The library hands this controller the ViewModel
 * after construction and before bindings, the same way it hands a Controller the developer wrote.
 */
final class SuppliedController implements HasViewModel {

    private ViewModel viewModel;

    /**
     * Receives the ViewModel for this View.
     *
     * @param viewModel the ViewModel built for the Model instance
     */
    @Override
    public void setViewModel(ViewModel viewModel) {
        this.viewModel = viewModel;
    }

    /**
     * Returns the ViewModel handed to this controller.
     *
     * @return the ViewModel, or {@code null} when none has been handed over
     */
    ViewModel viewModel() {
        return viewModel;
    }
}
