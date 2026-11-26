package de.neitzel.fx.view.component;

import de.neitzel.fx.view.HasViewModel;
import de.neitzel.fx.view.ViewModel;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Component that shows one address.
 * <p>
 * The container injects the caption service. the street label shows that service's prefix.
 */

public class AddressForm extends VBox implements HasViewModel {

    /**
     * The service that supplies the street caption.
     */
    private final StreetCaption captions;
    /**
     * The model this component edits. A parent view or a repeat sets it once.
     */
    private final ObjectProperty<Object> model = new SimpleObjectProperty<>(this, "model");

    /**
     * The label filled by the component's own binding.
     */
    @FXML
    private Label streetLabel;

    /**
     * The street field filled by the component's own binding.
     */
    @FXML
    private TextField streetField;

    /**
     * The view model handed over before the component's bindings run.
     */
    private ViewModel receivedViewModel;
    /**
     * The street text visible when the view model is handed over.
     */
    private String streetTextAtHandOff;

    /**
     * Creates a form that reads its street label from the injected caption.
     *
     * @param captions the service that supplies the street prefix
     */

    public AddressForm(StreetCaption captions) {
        this.captions = captions;
    }

    /**
     * Writes the injected prefix onto the street label.
     */
    @FXML
    public void initialize() {
        streetLabel.setText(captions.prefix());
    }

    /**
     * Returns the custom caption after initialization.
     * @return the caption
     */
    public String getCaption() {
        return streetLabel.getText();
    }

    /**
     * Sets a custom FXML attribute; the label must already be injected.
     * @param caption the caption
     */
    public void setCaption(String caption) {
        streetLabel.setText(caption);
    }

    /**
     * Returns the model property a parent view sets with a direct binding.
     *
     * @return the model property
     */
    public ObjectProperty<Object> modelProperty() {
        return model;
    }

    /**
     * Returns the address this component shows.
     *
     * @return the address, or {@code null} when none is set
     */
    public Object getModel() {
        return model.get();
    }

    /**
     * Sets the address this component shows.
     *
     * @param model the address
     */
    public void setModel(Object model) {
        this.model.set(model);
    }

    /**
     * Records the ViewModel handed to this component, and the street text at that moment.
     *
     * @param viewModel the view model
     */
    @Override
    public void setViewModel(ViewModel viewModel) {
        this.receivedViewModel = viewModel;
        this.streetTextAtHandOff = streetField.getText();
    }

    /**
     * Returns the ViewModel this component was handed.
     *
     * @return the ViewModel, or {@code null} when none was handed over
     */
    public ViewModel receivedViewModel() {
        return receivedViewModel;
    }

    /**
     * Returns the street text observed when the ViewModel was handed over.
     *
     * @return the street text at hand-off
     */
    public String streetTextAtHandOff() {
        return streetTextAtHandOff;
    }
}
