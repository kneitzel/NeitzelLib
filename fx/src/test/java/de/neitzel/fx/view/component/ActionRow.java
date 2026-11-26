package de.neitzel.fx.view.component;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.HBox;

/**
 * A Row with street and city fields and a control after them.
 */

public class ActionRow extends HBox {

    /**
     * The visit this row shows.
     */
    private final ObjectProperty<Object> model = new SimpleObjectProperty<>(this, "model");

    /**
     * Creates a row.
     */

    public ActionRow() {
    }

    /**
     * Returns the model property.
     *
     * @return the model property
     */
    public ObjectProperty<Object> modelProperty() {
        return model;
    }

    /**
     * Returns the visit this row shows.
     *
     * @return the visit, or {@code null}
     */
    public Object getModel() {
        return model.get();
    }

    /**
     * Sets the visit this row shows.
     *
     * @param model the visit
     */
    public void setModel(Object model) {
        this.model.set(model);
    }
}
