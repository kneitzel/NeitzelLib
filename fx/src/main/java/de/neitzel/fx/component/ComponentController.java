package de.neitzel.fx.component;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Generic controller used by the ComponentLoader to bind FXML views
 * to automatically generated ViewModels based on POJO models.
 * <p>
 * This controller provides access to the {@link AutoViewModel}
 * which contains JavaFX properties derived from the model.
 */
@RequiredArgsConstructor
public class ComponentController {

    /**
     * The automatically generated ViewModel that holds JavaFX properties
     * for all model fields. It is used to create bindings between
     * the view and the model.
     */
    @Getter
    private final AutoViewModel<?> viewModel;
}
