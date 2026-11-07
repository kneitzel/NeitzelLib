package de.neitzel.fx.view.fixture;

import java.util.Objects;

/**
 * Ordinary nested model used by view-loading tests.
 * <p>
 * Equality follows the street, so two addresses with the same street are equal while remaining
 * different instances.
 */
public class Address {

    /**
     * The street.
     */
    private String street;

    /**
     * Creates an address with the given street.
     *
     * @param street the current street
     */
    public Address(String street) {
        this.street = street;
    }

    /**
     * Returns the street.
     *
     * @return the street
     */
    public String getStreet() {
        return street;
    }

    /**
     * Sets the street.
     *
     * @param street the street
     */
    public void setStreet(String street) {
        this.street = street;
    }

    /**
     * Compares addresses by street.
     *
     * @param other the other address
     * @return {@code true} when the streets are equal
     */
    @Override
    public boolean equals(Object other) {
        if (other instanceof Address address) {
            return Objects.equals(street, address.street);
        }
        return false;
    }

    /**
     * Returns a hash code for the street.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(street);
    }
}
