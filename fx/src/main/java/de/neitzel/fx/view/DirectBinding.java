package de.neitzel.fx.view;

/**
 * A Direct binding declared in FXML.
 * <p>
 * The element names the control by {@code fx:id}, the control property, the Path, and the
 * direction. A Path of one name is that Attribute of this View's ViewModel. Further names step
 * into the ViewModel of a Complex attribute, and the direction applies to the last name.
 * When that last name is complex, the control receives the Model instance behind the ViewModel.
 * Direction values are {@code view}, {@code model}, and {@code both}.
 */
public class DirectBinding {

    private String control;
    private String property;
    private String path;
    private String direction;

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
     * Sets the Path. One name is an Attribute of this View's ViewModel.
     *
     * @param path the path
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Sets the direction. Accepted values are {@code view}, {@code model}, and {@code both}.
     *
     * @param direction the direction value
     */
    public void setDirection(String direction) {
        this.direction = direction;
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
     * Returns the Path.
     *
     * @return the path
     */
    public String getPath() {
        return path;
    }

    /**
     * Returns the direction value.
     *
     * @return {@code view}, {@code model}, or {@code both}
     */
    public String getDirection() {
        return direction;
    }
}
