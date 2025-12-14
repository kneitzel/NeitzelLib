package de.neitzel.fx.mvvm;

import javafx.fxml.Initializable;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * A generic JavaFX controller that holds a GenericViewModel for the given model type.
 * Intended for use with a custom FXMLLoader that performs automatic property bindings.
 *
 * @param <T> the type of the model
 */
public class GenericViewController<T> implements Initializable {

    /**
     * The GenericViewModel instance wrapping the underlying model.
     */
    private GenericViewModel<T> viewModel;

    /**
     * Creates a new controller instance in an uninitialized state.
     *
     * <p>The constructor does not set a model. Call {@link #setModel(Object)} to
     * attach a model instance before performing view bindings or accessing the view model via
     * {@link #getViewModel()}.</p>
     */
    public GenericViewController() {
        // default constructor only ...
    }

    /**
     * Retrieves the original model instance from the ViewModel.
     *
     * @return the model instance
     */
    public T getModel() {
        return viewModel.getModel();
    }

    /**
     * Sets the model for this controller by wrapping it in a GenericViewModel.
     *
     * @param model the model to be used by the ViewModel
     */
    public void setModel(T model) {
        this.viewModel = new GenericViewModel<>(model);
    }

    /**
     * Standard JavaFX initialize method. No-op, as actual binding is done externally via FXMLLoader extension.
     *
     * @param location  the location used to resolve relative paths for the root object, or null if unknown
     * @param resources the resources used to localize the root object, or null if not localized
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Real binding happens in FXMLLoader extension
    }

    /**
     * Returns the GenericViewModel used by this controller.
     *
     * @return the GenericViewModel instance
     */
    public GenericViewModel<T> getViewModel() {
        return viewModel;
    }
}
