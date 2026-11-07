package de.neitzel.fx.view.fixture;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.Pane;

/**
 * A small control whose value is an object, used to bind a Complex attribute.
 */
public class ObjectHolder extends Pane {

    /**
     * The value a direct binding writes.
     */
    private final ObjectProperty<Object> value = new SimpleObjectProperty<>(this, "value");

    /**
     * Returns the value property.
     *
     * @return the value property
     */
    public ObjectProperty<Object> valueProperty() {
        return value;
    }

    /**
     * Returns the current value.
     *
     * @return the value
     */
    public Object getValue() {
        return value.get();
    }

    /**
     * Sets the value.
     *
     * @param value the value
     */
    public void setValue(Object value) {
        this.value.set(value);
    }
}
