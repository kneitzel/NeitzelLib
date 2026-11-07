package de.neitzel.fx.example.model;

import java.util.ArrayList;
import java.util.List;

/**
 * An ordinary person the desk edits.
 * <p>
 * Read-write attributes are what the View binds to. The class carries no JavaFX types.
 */
public class Person {

    /**
     * The person's name.
     */
    private String name = "";

    /**
     * The person's age in years, or {@code null} when it has not been set.
     */
    private Integer age;

    /**
     * The home address.
     */
    private Address home;

    /**
     * The visit addresses. After the View loads, this is the list the visit table and Repeat watch.
     */
    private List<Address> visits = new ArrayList<>();

    /**
     * The visit selected in the table and the combo box, or {@code null} when none is selected.
     */
    private Address selectedVisit;

    /**
     * The tags. After the View loads, this is the list the list view watches.
     */
    private List<String> tags = new ArrayList<>();

    /**
     * Whether the person is active. A primitive boolean cannot be null.
     */
    private boolean active;

    /**
     * The person's role, or {@code null} when none is set.
     */
    private Role role;

    /**
     * Creates an empty person.
     */
    public Person() {
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name.
     *
     * @param name the name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the age in years.
     *
     * @return the age, or {@code null} when none is set
     */
    public Integer getAge() {
        return age;
    }

    /**
     * Sets the age in years.
     *
     * @param age the age
     */
    public void setAge(Integer age) {
        this.age = age;
    }

    /**
     * Returns the home address.
     *
     * @return the home address, or {@code null} when none is set
     */
    public Address getHome() {
        return home;
    }

    /**
     * Sets the home address.
     *
     * @param home the home address
     */
    public void setHome(Address home) {
        this.home = home;
    }

    /**
     * Returns the visits.
     * <p>
     * After the View has loaded, this is the list the View is watching.
     *
     * @return the visits
     */
    public List<Address> getVisits() {
        return visits;
    }

    /**
     * Sets the visits.
     *
     * @param visits the visits
     */
    public void setVisits(List<Address> visits) {
        this.visits = visits;
    }

    /**
     * Returns the visit chosen in the table and the combo box.
     *
     * @return the selected visit, or {@code null} when none is selected
     */
    public Address getSelectedVisit() {
        return selectedVisit;
    }

    /**
     * Sets the visit chosen in the table and the combo box.
     *
     * @param selectedVisit the selected visit
     */
    public void setSelectedVisit(Address selectedVisit) {
        this.selectedVisit = selectedVisit;
    }

    /**
     * Returns the tags.
     * <p>
     * After the View has loaded, this is the list the View is watching.
     *
     * @return the tags
     */
    public List<String> getTags() {
        return tags;
    }

    /**
     * Sets the tags.
     *
     * @param tags the tags
     */
    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    /**
     * Returns whether the person is active.
     *
     * @return {@code true} when the person is active
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Sets whether the person is active.
     *
     * @param active {@code true} when the person is active
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Returns the role.
     *
     * @return the role, or {@code null} when none is set
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
}
