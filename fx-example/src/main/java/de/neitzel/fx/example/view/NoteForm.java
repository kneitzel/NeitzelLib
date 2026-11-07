package de.neitzel.fx.example.view;

import jakarta.inject.Singleton;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.VBox;

/**
 * Component that edits one {@link de.neitzel.fx.example.model.Note}.
 * <p>
 * The class is the root and the controller of {@code NoteForm.fxml}. The home address form
 * nests this component and sets {@link #modelProperty()} once.
 */
@Singleton
public class NoteForm extends VBox {

    /**
     * The note this form edits. The parent component sets it once.
     */
    private final ObjectProperty<Object> model = new SimpleObjectProperty<>(this, "model");

    /**
     * Creates an empty form. The container calls this constructor.
     */
    public NoteForm() {
    }

    /**
     * Returns the model property the parent sets.
     *
     * @return the model property
     */
    public ObjectProperty<Object> modelProperty() {
        return model;
    }

    /**
     * Returns the note this form edits.
     *
     * @return the note, or {@code null} when none is set
     */
    public Object getModel() {
        return model.get();
    }

    /**
     * Sets the note this form edits.
     *
     * @param model the note
     */
    public void setModel(Object model) {
        this.model.set(model);
    }
}
