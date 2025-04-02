package de.neitzel.net.imap;

/**
 * Listener interface for receiving notifications about new emails.
 * Classes interested in processing new emails should implement this interface and
 * register themselves to receive events via supported mechanisms.
 */
public interface ImapListener {
    /**
     * This method gets invoked when a new email is received. Implementers of this method
     * handle the processing of new email events triggered by the associated source.
     *
     * @param event the NewEmailEvent object that contains information about the received email,
     *              including the email message and its source.
     */
    void newEmailReceived(NewEmailEvent event);
}
