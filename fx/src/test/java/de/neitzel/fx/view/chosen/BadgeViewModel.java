package de.neitzel.fx.view.chosen;

import de.neitzel.fx.view.fixture.Person;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Replacement for the library viewmodel. it exposes a badge property by name and does not
 * extend {@link de.neitzel.fx.view.ViewModel}.
 */
public class BadgeViewModel {

    /**
     * The badge text this view model adds beside the model attributes.
     */
    private final StringProperty badge = new SimpleStringProperty();

    /**
     * Builds the replacement and sets the badge from the person's name.
     *
     * @param model the Model instance
     */
    public BadgeViewModel(Object model) {
        Person person = (Person) model;
        badge.set("badge-" + person.getName());
    }

    /**
     * Returns the badge property a binding element addresses by name.
     *
     * @return the badge property
     */
    public StringProperty badgeProperty() {
        return badge;
    }
}
