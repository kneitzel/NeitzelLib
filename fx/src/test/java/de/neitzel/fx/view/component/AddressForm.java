package de.neitzel.fx.view.component;

import de.neitzel.fx.view.HasViewModel;
import de.neitzel.fx.view.ViewModel;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Component that shows one address.
 * <p>
 * The container injects the caption service. The street label shows that service's prefix.
 */
@Singleton
public class AddressForm extends VBox implements HasViewModel {

    private final StreetCaption captions;
    private final ObjectProperty<Object> model = new SimpleObjectProperty<>(this, "model");

    @FXML
    private Label streetLabel;

    @FXML
    private TextField streetField;

    private ViewModel receivedViewModel;
    private String streetTextAtHandOff;

    /**
     * Creates a form that reads its street label from the injected caption.
     *
     * @param captions the service that supplies the street prefix
     */
    @Inject
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
     * Returns the model property a parent View sets with a Direct binding.
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
     * @param viewModel the ViewModel selected for the address
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
