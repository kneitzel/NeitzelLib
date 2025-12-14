package de.neitzel.fx.component;

import lombok.Getter;

/**
 * Generic controller used by the ComponentLoader to bind FXML views
 * to automatically generated ViewModels based on POJO models.
 * <p>
 * This controller provides access to the {@link AutoViewModel}
 * which contains JavaFX properties derived from the model.
 */
public class ComponentController {

    /**
     * The automatically generated ViewModel that holds JavaFX properties
     * for all model fields. It is used to create bindings between
     * the view and the model.
     */
    @Getter
    private final AutoViewModel<?> viewModel;

    /**
     * Constructs a new ComponentController instance with the specified AutoViewModel.
     *
     * @param viewModel the AutoViewModel containing JavaFX properties derived from the model,
     *                  used for binding the view to the model.
     */
    public ComponentController(AutoViewModel<?> viewModel) {
        this.viewModel = viewModel;
    }
}
