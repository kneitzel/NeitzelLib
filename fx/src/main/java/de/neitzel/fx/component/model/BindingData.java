package de.neitzel.fx.component.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * The Binding class represents a connection between a source and a target,
 * with an associated direction. It is typically used to define a binding
 * relationship that determines how data flows between these two entities.
 * <p>
 * The class provides three properties:
 * - direction: Represents the type of the binding. Defaults to "unidirectional".
 * - source: Represents the source of the binding.
 * - target: Represents the target of the binding.
 * <p>
 * It uses JavaFX property types, allowing these properties to be observed
 * for changes.
 */
public class BindingData {

    /**
     * Represents the direction of the binding in the {@code Binding} class.
     * It determines whether the binding is unidirectional or bidirectional.
     * The default value is "unidirectional".
     * <p>
     * This property is observed for changes, enabling dynamic updates
     * within the JavaFX property system.
     */
    private StringProperty direction = new SimpleStringProperty("unidirectional");

    /**
     * Represents the source of the binding. This property holds a string value
     * that specifies the originating object or identifier in the binding connection.
     * It can be observed for changes, allowing updates to the binding relationship
     * when the source value is modified.
     */
    private StringProperty source = new SimpleStringProperty();

    /**
     * Represents the target of the binding in the Binding class.
     * This property holds the target value, which can be observed for changes.
     */
    private StringProperty target = new SimpleStringProperty();

    /**
     * Default constructor only
     */
    public BindingData() {
        // default constructor only
    }

    /**
     * Gets the current direction of the binding.
     * The direction determines how data flows between
     * the source and the target, and typically defaults to "unidirectional".
     *
     * @return the current direction of the binding
     */
    public String getDirection() {
        return direction.get();
    }

    /**
     * Sets the direction of the binding.
     *
     * @param dir the new direction to set for the binding
     */
    public void setDirection(String dir) {
        direction.set(dir);
    }

    /**
     * Gets the current source value of the binding.
     *
     * @return the source value as a String.
     */
    public String getSource() {
        return source.get();
    }

    /**
     * Sets the value of the source property for this binding.
     *
     * @param source the new source value to be set
     */
    public void setSource(String source) {
        this.source.set(source);
    }

    /**
     * Retrieves the current value of the target property.
     *
     * @return the value of the target property as a String.
     */
    public String getTarget() {
        return target.get();
    }

    /**
     * Sets the target property of the binding.
     *
     * @param target the new value to set for the target property
     */
    public void setTarget(String target) {
        this.target.set(target);
    }
}