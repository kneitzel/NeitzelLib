package de.neitzel.fx.example.model;

/**
 * An ordinary address the desk edits.
 */
public class Address {

    private String street = "";
    private String city = "";

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
     * Returns the city.
     *
     * @return the city
     */
    public String getCity() {
        return city;
    }

    /**
     * Sets the city.
     *
     * @param city the city
     */
    public void setCity(String city) {
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
