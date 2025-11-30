package de.neitzel.fx.component.example;

import lombok.Getter;
import lombok.Setter;

/**
 * Simple person model used in the FX example component.
 */
@Getter
@Setter
public class Person {

    /**
     * The person's full name.
     */
    private String name;

    /**
     * The address of the person.
     */
    private Address address;

    /**
     * Default constructor only
     */
    public Person() {
        // default constructor only
    }
}
