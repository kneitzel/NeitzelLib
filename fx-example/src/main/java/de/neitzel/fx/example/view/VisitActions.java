package de.neitzel.fx.example.view;

import de.neitzel.fx.example.model.Address;
import de.neitzel.fx.example.model.Person;
import jakarta.inject.Singleton;
import lombok.Setter;

/** Removes a visit from the Model that owns the repeated forms and table rows. */
@Singleton
public final class VisitActions {
    @Setter
    private Person person;

    public void remove(Address visit) {
        person.getVisits().remove(visit);
    }
}
