package de.neitzel.fx.example.view;

import jakarta.inject.Singleton;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.VBox;

/**
 * Component that edits one visit address in the repeated list.
 * <p>
 * The class is the root and the controller of {@code VisitForm.fxml}. Repeat creates a new
 * instance for each visit. The home address uses {@link AddressForm}, which nests a note.
 * This form does not, so a visit added after the desk has loaded does not reuse the note
 * component already showing on the home address.
 */
@Singleton
public class VisitForm extends VBox {

    /**
     * The visit this form edits. Repeat sets it once.
     */
    private final ObjectProperty<Object> model = new SimpleObjectProperty<>(this, "model");

    /**
     * Creates an empty form. The container calls this constructor.
     */
    public VisitForm() {
    }

    /**
     * Returns the model property Repeat sets.
     *
     * @return the model property
     */
    public ObjectProperty<Object> modelProperty() {
        return model;
    }

    /**
     * Returns the visit this form edits.
     *
     * @return the visit, or {@code null} when none is set
     */
    public Object getModel() {
        return model.get();
    }

    /**
     * Sets the visit this form edits.
     *
     * @param model the visit
     */
    public void setModel(Object model) {
        this.model.set(model);
    }
}
