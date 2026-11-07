package de.neitzel.fx.example.model;

import java.time.LocalDate;

/**
 * An ordinary address the desk edits.
 */
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
     * Returns the day of the visit.
     *
     * @return the date, or {@code null} when none is set
     */
    public LocalDate getVisitedOn() {
        return visitedOn;
    }

    /**
     * Sets the day of the visit.
     *
     * @param visitedOn the date, or {@code null} to clear it
     */
    public void setVisitedOn(LocalDate visitedOn) {
        this.visitedOn = visitedOn;
    }

    /**
     * Returns the note attached to this address.
     *
     * @return the note, or {@code null} when none is set
     */
    public Note getNote() {
        return note;
    }

    /**
     * Sets the note attached to this address.
     *
     * @param note the note, or {@code null} when there is none
     */
    public void setNote(Note note) {
        this.note = note;
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
