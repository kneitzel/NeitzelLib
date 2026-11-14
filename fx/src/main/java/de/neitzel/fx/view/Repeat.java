package de.neitzel.fx.view;

import lombok.Getter;
import lombok.Setter;

/**
 * A repeat element declared in a {@link BindingList}.
 * <p>
 * The element names the pane by {@code fx:id}, the Component class, and the collection
 * Attribute. The library keeps one Component child in that pane for each Model instance, in
 * list order.
 */
@Getter
@Setter
public class Repeat {

    /**
     * The {@code fx:id} of the pane that holds the Component children.
     */
    private String pane;

    /**
     * The Component class name. Each element gets a new instance of that class.
     */
    private String component;

    /**
     * The collection Attribute. The list holds the Model instances.
     */
    private String collection;

}
