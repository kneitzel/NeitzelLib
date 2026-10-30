package de.neitzel.fx.view;

/**
 * A Computed binding declared in FXML.
 * <p>
 * The element names the control by {@code fx:id}, the control property, and a no-argument
 * method on the Controller. The method value uses a {@code #method} prefix, such as
 * {@code #workingAge}. The method may read the Controller's fields. Its result is written to
 * the control property. The method does not write the Model.
 */
public class ComputedBinding {

    private String control;
    private String property;
    private String method;

    /**
     * Sets the {@code fx:id} of the control.
     *
     * @param control the control id
     */
    public void setControl(String control) {
        this.control = control;
    }

    /**
     * Sets the control property name, such as {@code text}.
     *
     * @param property the property name
     */
    public void setProperty(String property) {
        this.property = property;
    }

    /**
     * Sets the Controller method. A {@code #method} value names a no-argument method.
     *
     * @param method the method value, such as {@code #workingAge}
     */
    public void setMethod(String method) {
        this.method = method;
    }

    /**
     * Returns the {@code fx:id} of the control.
     *
     * @return the control id
     */
    public String getControl() {
        return control;
    }

    /**
     * Returns the control property name.
     *
     * @return the property name
     */
    public String getProperty() {
        return property;
    }

    /**
     * Returns the Controller method value.
     *
     * @return the method value, such as {@code #workingAge}
     */
    public String getMethod() {
        return method;
    }
}
