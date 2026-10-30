package de.neitzel.fx.view;

/**
 * The developer-written translation between a Direct binding's Attribute value and its control value.
 * <p>
 * The container constructs the class and injects its services. {@link #toControlValue(Object)} turns
 * the Attribute value into the control value. {@link #toAttributeValue(Object)} turns the control
 * value into the Attribute value and runs only when the Direct binding runs toward the Model.
 * A toward-view binding does not call the reverse method, so a converter used only toward the View
 * can leave it unimplemented. When a method cannot produce a value, it throws a runtime exception.
 * The binding then keeps the previous value.
 */
public interface Converter {

    /**
     * Turns the Attribute value into the control value.
     *
     * @param attributeValue the current Attribute value, which may be null
     * @return the value written to the control
     */
    Object toControlValue(Object attributeValue);

    /**
     * Turns the control value into the Attribute value.
     * <p>
     * Called only when the Direct binding runs toward the Model ({@code model} or {@code both}).
     * A toward-view binding does not require this method.
     *
     * @param controlValue the current control value
     * @return the value written to the Attribute
     */
    default Object toAttributeValue(Object controlValue) {
        throw new UnsupportedOperationException("no reverse conversion for " + controlValue);
    }
}
