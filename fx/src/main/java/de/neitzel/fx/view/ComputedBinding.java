package de.neitzel.fx.view;

import lombok.Getter;
import lombok.Setter;

/**
 * A Computed binding declared in FXML.
 * <p>
 * The element names the control by {@code fx:id}, the control property, and a no-argument
 * method on the Controller. The method value uses a {@code #method} prefix, such as
 * {@code #workingAge}. The method may read the Controller's fields. Its result is written to
 * the control property. The method does not write the Model.
 */
@Getter
@Setter
public class ComputedBinding {

    /**
     * The {@code fx:id} of the control that receives the method result.
     */
    private String control;

    /**
     * The control property that receives the method result, such as {@code text}.
     */
    private String property;

    /**
     * The Controller method, written as {@code #method}.
     */
    private String method;

}
