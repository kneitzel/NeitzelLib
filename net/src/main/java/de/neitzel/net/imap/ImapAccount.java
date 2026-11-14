package de.neitzel.net.imap;

import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents an IMAP account with server configuration, user credentials, and protocol details.
 * This class provides methods to set or get the account details and overrides methods for string
 * representation, equality checks, and hashing.
 */
@SuppressWarnings("unused")
@Getter
@Setter
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
