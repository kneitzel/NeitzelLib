package de.neitzel.fx.example.view;

import de.neitzel.fx.example.model.Person;
import de.neitzel.fx.view.ViewModel;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * ViewModel for a {@link Person} that adds a title beside the person's attributes.
 * <p>
 * The library constructs this class. The container does not. {@link DeskController} names it
 * with {@link de.neitzel.fx.view.ViewModelClass}.
 */
public class PersonViewModel extends ViewModel {

    /**
     * The title this view model adds. A binding addresses it as {@code title}.
     */
    private final StringProperty title = new SimpleStringProperty(this, "title");

    /**
     * Builds the view model and keeps the title aligned with the person's name.
     *
     * @param model the person
     */
    public PersonViewModel(Object model) {
        super(model);
        title.set("titled-" + ((Person) model).getName());
        property("name").addListener((observable, oldValue, newValue) -> title.set("titled-" + newValue));
    }

    /**
     * Returns the title property a binding element addresses by name.
     *
     * @return the title property
     */
    public StringProperty titleProperty() {
        return title;
    }
}
