package de.neitzel.fx.view.component;

import de.neitzel.fx.view.fixture.Address;

/**
 * Ordinary model that holds one address, used to nest an address component.
 */
public class Envelope {

    /**
     * The address this model holds.
     */
    private Address address;

    /**
     * Creates an envelope for the given address.
     *
     * @param address the address inside the envelope
     */
    public Envelope(Address address) {
        this.address = address;
    }

    /**
     * Returns the address.
     *
     * @return the address
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
}
