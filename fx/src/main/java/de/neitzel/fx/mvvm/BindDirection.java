package de.neitzel.fx.mvvm;

/**
 * Enum representing the direction of data binding between a JavaFX control and a ViewModel property.
 * - READ: one-way binding from ViewModel to Control.
 * - WRITE: one-way binding from Control to ViewModel.
 * - BIDIRECTIONAL: two-way binding between Control and ViewModel.
 */
public enum BindDirection {
    /**
     * One-way binding from the ViewModel to the JavaFX control.
     * The control reflects changes in the ViewModel but does not update it.
     */
    READ,

    /**
     * One-way binding from the JavaFX control to the ViewModel.
     * The ViewModel is updated when the control changes, but not vice versa.
     */
    WRITE,

    /**
     * Two-way binding between the JavaFX control and the ViewModel.
     * Changes in either are reflected in the other.
     */
    BIDIRECTIONAL;

    /**
     * Parses a string to determine the corresponding BindDirection.
     * Defaults to BIDIRECTIONAL if the input is null or empty.
     *
     * @param value the string representation of the direction
     * @return the corresponding BindDirection enum constant
     * @throws IllegalArgumentException if the string does not match any valid direction
     */
    public static BindDirection fromString(String value) {
        if (value == null || value.isEmpty()) {
            return BIDIRECTIONAL;
        }

        for (BindDirection direction : BindDirection.values()) {
            if (direction.name().equalsIgnoreCase(value)) {
                return direction;
            }
        }

        throw new IllegalArgumentException("Not a valid direction: " + value);
    }

}
