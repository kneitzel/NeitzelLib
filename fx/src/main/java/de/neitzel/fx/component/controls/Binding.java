package de.neitzel.fx.component.controls;

import de.neitzel.fx.component.model.BindingData;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.layout.Region;

/**
 * The BindingControl class represents a UI control that manages a list
 * of {@link BindingData} objects. It extends the {@link Region} class and
 * provides functionality to bind and monitor connections between source
 * and target properties.
 * <p>
 * The primary purpose of this control is to maintain an observable list
 * of bindings, allowing developers to track or adjust the linked properties
 * dynamically.
 * <p>
 * The internal list of bindings is implemented as an {@link ObservableList},
 * allowing property change notifications to be easily monitored for UI
 * updates or other reactive behaviors.
 * <p>
 * This class serves as an organizational component and does not provide
 * any user interaction by default.
 */
public class Binding extends Region {

    /**
     * Represents an observable list of {@link BindingData} objects contained within the
     * {@link Binding} instance. This list is utilized to manage and monitor
     * the bindings between source and target properties dynamically.
     * <p>
     * The list is implemented as an {@link ObservableList}, allowing changes in the
     * collection to be observed and reacted to, such as triggering UI updates or
     * responding to binding modifications.
     * <p>
     * This field is initialized as an empty list using {@link FXCollections#observableArrayList()}.
     * It is declared as final to ensure its reference cannot be changed, while the
     * contents of the list remain mutable.
     */
    private final ObservableList<BindingData> bindings = FXCollections.observableArrayList();

    /**
     * Constructs a new instance of the BindingControl class.
     * <p>
     * This default constructor initializes the BindingControl without
     * any pre-configured bindings. The instance will contain an empty
     * {@link ObservableList} of {@link BindingData} objects, which can later
     * be populated as needed.
     * <p>
     * The constructor does not perform additional setup or initialization,
     * allowing the class to be extended or customized as necessary.
     */
    public Binding() {
        // Empty, the ComponentLoader is used to work on the bindings.
    }

    /**
     * Retrieves the observable list of {@code Binding} objects associated with this control.
     * The returned list allows monitoring and management of the bindings maintained
     * by the {@code BindingControl}.
     *
     * @return an {@code ObservableList<Binding>} containing the bindings managed by this control
     */
    public ObservableList<BindingData> getBindings() {
        return bindings;
    }
}