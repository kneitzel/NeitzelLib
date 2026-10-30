package de.neitzel.fx.view.chosen;

import de.neitzel.fx.view.ViewModel;
import de.neitzel.fx.view.fixture.Person;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * ViewModel subclass that adds a title property taken from the Model's name.
 */
public class TitledViewModel extends ViewModel {

    private final StringProperty title = new SimpleStringProperty();

    /**
     * Builds a ViewModel and sets the title from the person's name.
     *
     * @param model the Model instance
     */
    public TitledViewModel(Object model) {
        super(model);
        Person person = (Person) model;
        title.set("titled-" + person.getName());
    }

    /**
     * Returns the title property a Binding element addresses by name.
     *
     * @return the title property
     */
    public StringProperty titleProperty() {
        return title;
    }
}
