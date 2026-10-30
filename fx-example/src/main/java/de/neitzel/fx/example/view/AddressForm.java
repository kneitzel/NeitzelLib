package de.neitzel.fx.example.view;

import jakarta.inject.Singleton;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.VBox;

/**
 * Component that edits one address.
 * <p>
 * The class is the root and the Controller of {@code AddressForm.fxml}. A parent View sets
 * {@link #modelProperty()} from a Direct binding. A repeated row gets its own instance.
 */
@Singleton
public class AddressForm extends VBox {

    private final ObjectProperty<Object> model = new SimpleObjectProperty<>(this, "model");

    /**
     * Creates an empty form. The container calls this constructor.
     */
    public AddressForm() {
    }

    /**
     * Returns the model property a parent View sets.
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
