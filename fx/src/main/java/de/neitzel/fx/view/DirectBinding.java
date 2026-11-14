package de.neitzel.fx.view;

import lombok.Getter;
import lombok.Setter;

/**
 * A Direct binding declared in FXML.
 * <p>
 * The element names the control by {@code fx:id}, the control property, the Path, and the
 * direction, and an optional Converter class. A Path of one name is that Attribute of this
 * View's ViewModel. Further names step into the ViewModel of a Complex attribute, and the
 * direction applies to the last name. When that last name is complex, the control receives the
 * Model instance behind the ViewModel. Direction values are {@code view}, {@code model}, and
 * {@code both}. The Converter value is the class name. The container creates that class.
 */
@Getter
@Setter
public class DirectBinding {

    /**
     * The {@code fx:id} of the control.
     */
    private String control;

    /**
     * The control property, such as {@code text}.
     */
    private String property;

    /**
     * The Path. One name is an Attribute of this View's ViewModel.
     */
    private String path;

    /**
     * The direction: {@code view}, {@code model}, or {@code both}.
     */
    private String direction;

    /**
     * The Converter class name, or {@code null} when values pass through.
     */
    private String converter;

}
