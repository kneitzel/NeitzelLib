package de.neitzel.fx.view.component;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.HBox;

/**
 * A Row that lays city out before street.
 */
@Singleton
public class CityFirstRow extends HBox {

    /**
     * The visit this row shows.
     */
    private final ObjectProperty<Object> model = new SimpleObjectProperty<>(this, "model");

    /**
     * Creates a row.
     */
    @Inject
    public CityFirstRow() {
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
}
