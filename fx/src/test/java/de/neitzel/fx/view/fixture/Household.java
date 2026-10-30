package de.neitzel.fx.view.fixture;

/**
 * Ordinary model that holds a person, so a test can read a message two levels up.
 */
public class Household {

    private Person person;

    /**
     * Returns the person.
     *
     * @return the person, or {@code null} when the household has none
     */
    public Person getPerson() {
        return person;
    }

    /**
     * Sets the person.
     *
     * @param person the person
     */
    public void setPerson(Person person) {
        this.person = person;
    }
}
