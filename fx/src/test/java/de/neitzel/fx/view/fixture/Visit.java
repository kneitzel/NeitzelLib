package de.neitzel.fx.view.fixture;

/**
 * One visit a table can show.
 */
public class Visit {

    /**
     * The street.
     */
    private String street;

    /**
     * The city.
     */
    private String city;

    /**
     * A whole number, used when a Column sorts or filters by magnitude.
     */
    private Integer rank;

    /**
     * Whether the visit is open.
     */
    private boolean open;

    /**
     * The visitor's role.
     */
    private Role role;

    /**
     * The day of the visit.
     */
    private java.time.LocalDate day;

    /**
     * A nested address. A Column cannot use this Attribute.
     */
    private Address place;

    /**
     * Creates a visit.
     *
     * @param street the street
     * @param city   the city
     */
    public Visit(String street, String city) {
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
     * Returns the rank.
     *
     * @return the rank, or {@code null}
     */
    public Integer getRank() {
        return rank;
    }

    /**
     * Sets the rank.
     *
     * @param rank the rank
     */
    public void setRank(Integer rank) {
        this.rank = rank;
    }

    /**
     * Returns whether the visit is open.
     *
     * @return {@code true} when the visit is open
     */
    public boolean isOpen() {
        return open;
    }

    /**
     * Sets whether the visit is open.
     *
     * @param open whether the visit is open
     */
    public void setOpen(boolean open) {
        this.open = open;
    }

    /**
     * Returns the role.
     *
     * @return the role, or {@code null}
     */
    public Role getRole() {
        return role;
    }

    /**
     * Sets the role.
     *
     * @param role the role
     */
    public void setRole(Role role) {
        this.role = role;
    }

    /**
     * Returns the day.
     *
     * @return the day, or {@code null}
     */
    public java.time.LocalDate getDay() {
        return day;
    }

    /**
     * Sets the day.
     *
     * @param day the day
     */
    public void setDay(java.time.LocalDate day) {
        this.day = day;
    }

    /**
     * Returns the nested address.
     *
     * @return the nested address, or {@code null}
     */
    public Address getPlace() {
        return place;
    }

    /**
     * Sets the nested address.
     *
     * @param place the nested address
     */
    public void setPlace(Address place) {
        this.place = place;
    }
}
