package de.neitzel.fx.view.chosen;

import de.neitzel.fx.view.ViewModel;
import de.neitzel.fx.view.fixture.Address;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * ViewModel for an address that adds a caption taken from the street.
 */
public class StreetTitleViewModel extends ViewModel {

    private final StringProperty caption = new SimpleStringProperty();

    /**
     * Builds a ViewModel and sets the caption from the address street.
     *
     * @param model the address
     */
    public StreetTitleViewModel(Object model) {
        super(model);
        Address address = (Address) model;
        caption.set("caption-" + address.getStreet());
    }

    /**
     * Returns the caption property a Binding element addresses by name.
     *
     * @return the caption property
     */
    public StringProperty captionProperty() {
        return caption;
    }
}
