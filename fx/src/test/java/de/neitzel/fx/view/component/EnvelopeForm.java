package de.neitzel.fx.view.component;

import jakarta.inject.Singleton;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.VBox;

/**
 * Component that shows one envelope and the address component inside it.
 */
@Singleton
public class EnvelopeForm extends VBox {

    /**
     * The model this component edits. A parent view or a repeat sets it once.
     */
    private final ObjectProperty<Object> model = new SimpleObjectProperty<>(this, "model");

    /**
     * Returns the model property a parent view sets with a direct binding.
     *
     * @return the model property
     */
    public ObjectProperty<Object> modelProperty() {
        return model;
    }

    /**
     * Returns the envelope this component shows.
     *
     * @return the envelope, or {@code null} when none is set
     */
    public Object getModel() {
        return model.get();
    }

    /**
     * Sets the envelope this component shows.
     *
     * @param model the envelope
     */
    public void setModel(Object model) {
        this.model.set(model);
    }
}
