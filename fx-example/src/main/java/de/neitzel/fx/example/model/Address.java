package de.neitzel.fx.example.model;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * An ordinary address the desk edits.
 */
@Getter
@Setter
public class Address {

    /**
     * The street.
     */
    private String street = "";

    /**
     * The city.
     */
    private String city = "";

    /**
     * The day of the visit, or {@code null} when the address is not a dated visit.
     */
    private LocalDate visitedOn;

    /**
     * The note attached to this address, or {@code null} when there is none.
     */
    private Note note;

    /**
     * Creates an empty address.
     */
    public Address() {
    }

    /**
     * Creates an address with the given street and city.
     *
     * @param street the street
     * @param city   the city
     */
    public Address(String street, String city) {
        this.street = street;
        this.city = city;
    }

    /**
     * Returns the street and city, for a combo box row.
     *
     * @return the street and city
     */
    @Override
    public String toString() {
        return street + ", " + city;
    }
}
