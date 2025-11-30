package de.neitzel.fx.component.example;

import lombok.Getter;
import lombok.Setter;

/**
 * Simple address model used in the FX example component.
 */
@Getter
@Setter
public class Address {

    /**
     * Street name and number.
     */
    private String street;

    /**
     * City name.
     */
    private String city;

    /**
     * Default constructor only
     */
    public Address() {
        // default constructor only
    }
}