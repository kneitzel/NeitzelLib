package de.neitzel.fx.control;

import javafx.beans.NamedArg;
import javafx.beans.property.ObjectProperty;
import javafx.scene.control.Spinner;

/** Bounded integer input available directly in FXML, including immediate valid typing. */
public final class NonNegativeIntegerSpinner extends Spinner<Integer> {
    public NonNegativeIntegerSpinner(
            @NamedArg(value = "minimum", defaultValue = "0") int minimum,
            @NamedArg(value = "maximum", defaultValue = "2147483647") int maximum,
            @NamedArg(value = "initialValue", defaultValue = "0") int initialValue) {
        super(minimum, maximum, initialValue);
        IntegerSpinners.configure(this, minimum, maximum, initialValue);
    }

    /** Writable property for DirectBinding; Spinner.valueProperty itself is read-only. */
    public ObjectProperty<Integer> numberProperty() {
        return getValueFactory().valueProperty();
    }

    public Integer getNumber() {
        return numberProperty().get();
    }

    public void setNumber(Integer value) {
        numberProperty().set(value);
    }
}
