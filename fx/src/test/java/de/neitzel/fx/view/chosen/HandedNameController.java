package de.neitzel.fx.view.chosen;

import de.neitzel.fx.view.HasViewModel;
import de.neitzel.fx.view.ViewModel;
import jakarta.inject.Singleton;

/**
 * Controller whose computed value is taken from the ViewModel handed over before bindings.
 */
@Singleton
public class HandedNameController implements HasViewModel {

    /**
     * The name handed to this controller before the bindings run.
     */
    private String handed;

    /**
     * Records a label value that is available only from the handed ViewModel.
     *
     * @param viewModel the view model
     */
    @Override
    public void setViewModel(ViewModel viewModel) {
        handed = "handed-" + viewModel.property("name").getValue();
    }

    /**
     * Returns the value recorded when the ViewModel was handed over.
     *
     * @return the label text
     */
    public String handedName() {
        return handed;
    }
}
