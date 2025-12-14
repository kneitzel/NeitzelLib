package de.neitzel.net.imap;

import java.util.Objects;

/**
 * Represents an IMAP account with server configuration, user credentials, and protocol details.
 * This class provides methods to set or get the account details and overrides methods for string
 * representation, equality checks, and hashing.
 */
@SuppressWarnings("unused")
public class ImapAccount {

    /**
     * Specifies the server address for the IMAP account.
     * This typically represents the hostname or IP address
     * of the mail server that handles IMAP communication.
     */
    protected String server;

    /**
     * Represents the username or identifier used to authenticate the IMAP account.
     * This value is associated with the user's credentials required to log in
     * to the IMAP server.
     */
    protected String user;

    /**
     * The password associated with the IMAP account.
     * This is used to authenticate the user when connecting to the IMAP server.
     */
    protected String password;

    /**
     * Specifies the protocol used for the IMAP account, such as "IMAP" or "IMAPS".
     * This field determines the communication protocol for interacting with the server.
     */
    protected String protocol;

    /**
     * Default constructor only
     */
    public ImapAccount() {
        // default constructor only
    }

    /**
     * Retrieves the server address associated with this IMAP
     */
    public String getServer() {
        return server;
    }

    /**
     * Sets the server address for the IMAP account.
     *
     * @param server the server address to be configured for the IMAP account
     */
    public void setServer(String server) {
        this.server = server;
    }

    /**
     * Retrieves the username associated with this IMAP account.
     *
     * @return the username of the IMAP account as a String.
     */
    public String getUser() {
        return user;
    }

    /**
     * Sets the username associated with the IMAP account.
     *
     * @param user The username to be set for this IMAP account.
     */
    public void setUser(String user) {
        this.user = user;
    }

    /**
     * Retrieves the password associated with this IMAP account.
     *
     * @return the password of the IMAP account.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password for the IMAP account.
     *
     * @param password The password to be set for the account.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Retrieves the protocol used by this IMAP account.
     *
     * @return the protocol as a String, which defines the communication method for the IMAP account.
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * Sets the protocol used by the IMAP account.
     *
     * @param protocol the protocol to be used, e.g., "IMAP" or "IMAPS".
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * Computes the hash code for this instance of the ImapAccount class.
     * The hash code is based on the values of the server, user, password, and protocol fields.
     * This ensures that instances with the same field values produce the same hash code.
     *
     * @return An integer hash code representing this ImapAccount instance.
     */
    @Override
    public int hashCode() {
        return Objects.hash(server, user, password, protocol);
    }

    /**
     * Compares this ImapAccount instance with the specified object for equality.
     * The comparison is based on the equality of all relevant fields: server, user, password, and protocol.
     *
     * @param obj the object to compare with this instance
     * @return true if the specified object is equal to this ImapAccount instance; false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        // Check type of argument, includes check of null.
        if (!(obj instanceof ImapAccount)) return false;

        // Check Reference equals
        if (this == obj) return true;

        // Check the comparison of all field
        ImapAccount other = (ImapAccount) obj;
        return Objects.equals(server, other.server) &&
                Objects.equals(user, other.user) &&
                Objects.equals(password, other.password) &&
                Objects.equals(protocol, other.protocol);
    }

    /**
     * Provides a string representation of the ImapAccount object including its server, user,
     * a masked password, and protocol information. The password is represented as '########' if it is not empty.
     *
     * @return A formatted string that describes the ImapAccount object with its server, user, masked password, and protocol.
     */
    @Override
    public String toString() {
        return String.format("ImapAccount(%s, %s, %s, %s",
                server,
                user,
                (password == null || password.isEmpty()) ? "''" : "########",
                protocol);
    }
}
