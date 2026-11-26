package de.neitzel.fx.example.view;

import de.neitzel.fx.example.model.Address;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.layout.HBox;
import lombok.RequiredArgsConstructor;

/**
 * Row that edits one visit in the visit table.
 * <p>
 * The class is the root and the Controller of {@code VisitRow.fxml}. The table sets
 * {@link #modelProperty()} once, and this row binds its own fields. Those fields are in column
 * order: street, city, then the visit date. Remove sits after them and is not a column.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class VisitRow extends HBox {

    /**
     * The shared action that removes a visit from the person.
     */
    private final VisitActions visits;

    /**
     * The visit this row edits. The table sets it once.
     */
    private final ObjectProperty<Object> model = new SimpleObjectProperty<>(this, "model");

    /**
     * Returns the model property the table sets.
     *
     * @return the model property
     */
    public ObjectProperty<Object> modelProperty() {
        return model;
    }

    /**
     * Returns the visit this row edits.
     *
     * @return the visit, or {@code null} when none is set
     */
    public Object getModel() {
        return model.get();
    }

    /**
     * Sets the visit this row edits.
     *
     * @param model the visit
     */
    public void setModel(Object model) {
        this.model.set(model);
    }

    /**
     * Removes this visit from the person.
     */
    @FXML
    public void remove() {
        if (getModel() instanceof Address address) {
            visits.remove(address);
        }
    }
}
