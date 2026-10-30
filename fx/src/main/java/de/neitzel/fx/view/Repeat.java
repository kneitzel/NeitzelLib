package de.neitzel.fx.view;

/**
 * A repeat element declared in a {@link BindingList}.
 * <p>
 * The element names the pane by {@code fx:id}, the Component class, and the collection
 * Attribute. The library keeps one Component child in that pane for each Model instance, in
 * list order.
 */
public class Repeat {

    private String pane;
    private String component;
    private String collection;

    /**
     * Sets the {@code fx:id} of the pane that holds the Component children.
     *
     * @param pane the pane id
     */
    public void setPane(String pane) {
        this.pane = pane;
    }

    /**
     * Sets the Component class name. Each element gets a new instance of that class.
     *
     * @param component the class name
     */
    public void setComponent(String component) {
        this.component = component;
    }

    /**
     * Sets the collection Attribute name. The list holds the Model instances.
     *
     * @param collection the attribute name
     */
    public void setCollection(String collection) {
        this.collection = collection;
    }

    /**
     * Returns the {@code fx:id} of the pane.
     *
     * @return the pane id
     */
    public String getPane() {
        return pane;
    }

    /**
     * Returns the Component class name.
     *
     * @return the class name
     */
    public String getComponent() {
        return component;
    }

    /**
     * Returns the collection Attribute name.
     *
     * @return the attribute name
     */
    public String getCollection() {
        return collection;
    }
}
