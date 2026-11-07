package de.neitzel.fx.example.view;

import de.neitzel.fx.view.HasViewModel;
import de.neitzel.fx.view.ViewModel;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Component that edits the home address, including its nested note.
 * <p>
 * The class is the root and the controller of {@code AddressForm.fxml}. The desk sets
 * {@link #modelProperty()} once. {@link #setHeading} is setter injection. {@link #setViewModel}
 * runs after that model is set and before this form's own bindings.
 */
@Singleton
public class AddressForm extends VBox implements HasViewModel {

    /**
     * The address this form edits. The desk sets it once.
     */
    private final ObjectProperty<Object> model = new SimpleObjectProperty<>(this, "model");

    /**
     * The heading shown above the fields. The container sets it through {@link #setHeading}.
     */
    private FormHeading heading;

    /**
     * The heading label. {@link #initialize()} writes the injected heading onto it.
     */
    @FXML
    private Label headingLabel;

    /**
     * The label filled from the address before the field bindings run.
     */
    @FXML
    private Label openedAsLabel;

    /**
     * Creates an empty form. The container calls this constructor.
     */
    public AddressForm() {
    }

    /**
     * Receives the heading. The container calls this setter after construction.
     *
     * @param heading the heading
     */
    @Inject
    public void setHeading(FormHeading heading) {
        this.heading = heading;
    }

    /**
     * Writes the injected heading onto the label.
     * <p>
     * FXML calls this during load, before the model is set.
     */
    @FXML
    public void initialize() {
        headingLabel.setText(heading.text());
    }

    /**
     * Records the city as it stands when the model arrives, before the field bindings run.
     *
     * @param viewModel the address view model
     */
    @Override
    public void setViewModel(ViewModel viewModel) {
        openedAsLabel.setText("Opened as " + viewModel.property("city").getValue());
    }

    /**
     * Returns the model property the desk sets.
     *
     * @return the model property
     */
    public ObjectProperty<Object> modelProperty() {
        return model;
    }

    /**
     * Returns the address this form edits.
     *
     * @return the address, or {@code null} when none is set
     */
    public Object getModel() {
        return model.get();
    }

    /**
     * Sets the address this form edits.
     *
     * @param model the address
     */
    public void setModel(Object model) {
        this.model.set(model);
    }
}
