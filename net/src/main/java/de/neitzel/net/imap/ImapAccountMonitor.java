package de.neitzel.net.imap;

import lombok.extern.slf4j.Slf4j;

import javax.mail.*;
import java.util.*;

/**
 * The ImapAccountMonitor class is responsible for monitoring an IMAP email account
 * by maintaining a connection to the server, tracking specific folders, and
 * listening for new emails. This class allows managing the set of monitored folders,
 * processing unseen and undeleted messages, and notifying registered listeners when
 * new messages are received.
 */
@Slf4j
public class ImapAccountMonitor {
    /**
     * Represents an instance of an IMAP account, encapsulating details about
     * server configuration, user credentials, and protocol settings.
     * This variable is used to connect and authenticate the application's interaction
     * with an IMAP email server.
     */
    protected ImapAccount account;

    /**
     * Represents the current session for interaction with an IMAP server.
     * The session encapsulates the configuration and context required
     * to establish a connection and perform operations on the IMAP server.
     * It is initialized and utilized internally within the application.
     */
    protected Session session;

    /**
     * Represents the mail store associated with the IMAP account.
     * The store is used to interact with the IMAP server for email retrieval and management.
     */
    protected Store store;

    /**
     * Represents a collection of folders associated with an IMAP account.
     * The map's keys are folder names (String), and the values are Folder objects.
     * This variable is used to store and manage the hierarchy or collection
     * of email folders for an IMAP account.
     */
    protected Map<String, Folder> folders = new HashMap<String, Folder>();

    /**
     * Monitors an IMAP account by establishing a connection to the specified mail server
     * using the provided account credentials and protocol.
     *
     * @param account the IMAP account containing details such as server, user, password, and protocol
     * @throws NoSuchProviderException if the specified mail store protocol is not available
     * @throws MessagingException if the connection to the mail server fails
     */
    public ImapAccountMonitor(ImapAccount account) throws NoSuchProviderException, MessagingException {
        log.trace("Constructor ({})", account.toString());
        this.account = account;

        Properties props = System.getProperties();
        props.setProperty("mail.store.protocol", account.getProtocol());
        session = Session.getDefaultInstance(props);
        try {
            store = session.getStore("imaps");
            store.connect(account.server, account.user, account.password);
        } catch (NoSuchProviderException ex) {
            log.error("Unable to get imaps Store.", ex);
            throw ex;
        } catch (MessagingException ex) {
            log.error("Unable to connect.", ex);
            throw ex;
        }

        log.trace("Constructor done.");
    }

    /**
     * Closes the resources associated with the current instance, including
     * IMAP folders and the store. The method ensures that all folders are
     * closed properly, followed by the closure of the store.
     *
     * Any exceptions encountered during the closure of folders or the store
     * are caught and logged without interrupting the overall closing process.
     * This is to ensure that all resources are attempted to be closed even if
     * an error occurs with one of them.
     *
     * The method logs the start and end of the closing process for traceability.
     */
    public void close() {
        log.trace("close() called.");

        // Close the folders
        for (Folder folder: folders.values()) {
            try {
                folder.close(false);
            } catch (MessagingException ex) {
                // Only log the exception.
                log.warn("Exception when closing folder.", ex);
            }
        }

        // Close the store
        try {
            store.close();
        } catch (MessagingException ex) {
            // Only log the error.
            log.warn("Exception when closing the store.", ex);
        }

        log.trace("close() call ended.");
    }

    /**
     * Adds a new folder to the IMAP account and opens it in read-write mode.
     * The folder is added to an internal collection for future access.
     *
     * @param name the name of the folder to be added and opened
     * @throws MessagingException if any error occurs while accessing or opening the folder
     */
    public void addFolder(String name) throws MessagingException {
        log.trace("addFolder(%s) called.", name);
        Folder folder = store.getFolder(name);
        folder.open(Folder.READ_WRITE);
        folders.put(name, folder);
        log.trace("addFolder({}) call ended.", name);
    }

    /**
     * Removes the folder with the specified name from the account's folder list.
     * If the folder is not found, the method does nothing.
     * After removing the folder, it attempts to close any associated resources.
     *
     * @param name the name of the folder to be removed
     */
    public void removeFolder(String name) {
        log.trace("removeFolder({}) called.", name);

        // Validate folder is known.
        if (!folders.containsKey(name)) return;

        Folder folder = folders.get(name);
        folders.remove(name);
        try {
            folder.close(false);
        } catch (MessagingException ex) {
            // TODO: Handle exception ...
        }

        log.trace("removeFolder({}) call ended.", name);
    }

    /**
     * Checks all monitored folders for new email messages, processes unseen and undeleted messages,
     * and raises a NewMailEvent for each new email discovered. The method ensures processed messages
     * are marked as seen.
     *
     * The method iterates through all folders currently being monitored, fetching their messages and
     * evaluating the state of each. If a message is neither marked as seen nor deleted, it is marked
     * as seen and a NewMailEvent is raised for it.
     *
     * Proper error handling is implemented to log any MessagingException that occurs while processing
     * the messages of a folder without interrupting the processing of other folders.
     *
     * Note: This method relies on external logging (via `log`) and appropriate event handling
     * via `raiseNewEmailEvent`. The `folders` collection holds the monitored folders required.
     *
     * The folder processing stops only in case of irrecoverable exceptions outside this method's scope.
     */
    public void check() {
        log.trace("check() called.");

        // Loop through all folders that are monitored
        for (Folder folder: folders.values()) {
            try {
                // Loop through all messages.
                Message[] messages = folder.getMessages();
                for (Message message: messages) {
                    // Check if message wasn't seen and wasn't deleted
                    if (!message.isSet(Flags.Flag.SEEN) && !message.isSet(Flags.Flag.DELETED)) {
                        // Mark message as seen.
                        message.setFlag(Flags.Flag.SEEN, true);

                        // Raise NewMailEvent
                        raiseNewEmailEvent(message);
                    }
                }
            } catch (MessagingException ex) {
                log.error("Exception when reading messages of folder {}.", folder.getName(), ex);
                // So far no handling of this error except logging it.
            }
        }

        log.trace("check() call ended.");
    }

    /**
     * A collection of listeners that respond to IMAP-related events.
     * Each listener in this collection can perform specific actions when triggered
     * by events such as receiving new email notifications.
     */
    private Collection<ImapListener> imapListeners = new ArrayList<ImapListener>();

    /**
     * Adds an IMAP listener to receive notifications about IMAP-related events such as new email arrivals.
     *
     * @param listener the ImapListener instance to be added to the list of listeners
     */
    public void addImapListener(ImapListener listener) {
        log.trace("addImapListener() called!");
        imapListeners.add(listener);
    }

    /**
     * Removes the specified IMAP listener from the list of active listeners.
     * If the listener is present, it will be removed; otherwise, no action is taken.
     *
     * @param listener The IMAP listener to be removed.
     */
    public void removeImapListener(ImapListener listener) {
        log.trace("removeImapListener() called!");
        if (imapListeners.contains(listener)) {
            log.info("Removing the IMAP listener.");
            imapListeners.remove(listener);
        }
    }

    /**
     * Raises a new email event and notifies all registered listeners.
     * The method loops through the listeners and invokes their {@code NewEmailReceived}
     * method with a newly created {@link NewEmailEvent}.
     * If a listener marks the event as handled, the remaining listeners will
     * not be notified.
     *
     * @param message The email message that triggered the event. This message
     *                is included in the {@link NewEmailEvent} passed to the listeners.
     */
    protected void raiseNewEmailEvent(Message message) {
        log.trace("raiseNewEmailEvent() called!");

        // Create the event.
        NewEmailEvent event = new NewEmailEvent(this, message);

        // Call all listeners.
        for (ImapListener listener: imapListeners) {
            log.trace("Calling listener in {} ....", listener.getClass().getName());
            listener.newEmailReceived(event);

            // Check if the event was handled so no further ImaListeners will be called.
            if (event.isHandled()) {
                log.trace("raiseNewEmailEvent(): Breaking out of listener loop because event was handled.");
                break;
            }
        }

        log.trace("raiseNewEmailEvent() call ended!");
    }
}
