package de.neitzel.fx.view.fixture;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Ordinary model used by view-loading tests.
 */
public class Person {

    private String name;
    private Integer age;
    private boolean active;
    private Address address;
    private Address home;
    private Address work;
    private Person friend;
    private Role role;
    private LocalDate born;
    private Date updated;
    private Calendar hired;
    private List<String> tags;
    private List<Address> addresses;
    private Address selectedAddress;

    /**
     * Creates a person with the given name.
     *
     * @param name the current name
     */
    public Person(String name) {
        this.name = name;
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
     * Returns a title that cannot be written.
     *
     * @return the title
     */
    public String getTitle() {
        return "Doctor";
    }

    /**
     * Returns the age. The value may be null.
     *
     * @return the age
     */
    public Integer getAge() {
        return age;
    }

    /**
     * Sets the age.
     *
     * @param age the age
     */
    public void setAge(Integer age) {
        this.age = age;
    }

    /**
     * Returns the address.
     *
     * @return the address, or {@code null} when the person has none
     */
    public Address getAddress() {
        return address;
    }

    /**
     * Sets the address.
     *
     * @param address the address
     */
    public void setAddress(Address address) {
        this.address = address;
    }

    /**
     * Returns the home address.
     *
     * @return the home address
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
     * Returns the work address.
     *
     * @return the work address
     */
    public Address getWork() {
        return work;
    }

    /**
     * Sets the work address.
     *
     * @param work the work address
     */
    public void setWork(Address work) {
        this.work = work;
    }

    /**
     * Returns the friend.
     *
     * @return the friend
     */
    public Person getFriend() {
        return friend;
    }

    /**
     * Sets the friend.
     *
     * @param friend the friend
     */
    public void setFriend(Person friend) {
        this.friend = friend;
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
     * @return the role
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
     * Returns the birth date.
     *
     * @return the birth date
     */
    public LocalDate getBorn() {
        return born;
    }

    /**
     * Sets the birth date.
     *
     * @param born the birth date
     */
    public void setBorn(LocalDate born) {
        this.born = born;
    }

    /**
     * Returns the last update.
     *
     * @return the last update
     */
    public Date getUpdated() {
        return updated;
    }

    /**
     * Sets the last update.
     *
     * @param updated the last update
     */
    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    /**
     * Returns the hire date.
     *
     * @return the hire date
     */
    public Calendar getHired() {
        return hired;
    }

    /**
     * Sets the hire date.
     *
     * @param hired the hire date
     */
    public void setHired(Calendar hired) {
        this.hired = hired;
    }

    /**
     * Returns the tags. The value may be null.
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
     * Returns the addresses. The value may be null.
     *
     * @return the addresses
     */
    public List<Address> getAddresses() {
        return addresses;
    }

    /**
     * Sets the addresses.
     *
     * @param addresses the addresses
     */
    public void setAddresses(List<Address> addresses) {
        this.addresses = addresses;
    }

    /**
     * Returns the selected address. Null means nothing is selected.
     *
     * @return the selected address, or {@code null}
     */
    public Address getSelectedAddress() {
        return selectedAddress;
    }

    /**
     * Sets the selected address.
     *
     * @param selectedAddress the selected address, or {@code null} when nothing is selected
     */
    public void setSelectedAddress(Address selectedAddress) {
        this.selectedAddress = selectedAddress;
    }
}
