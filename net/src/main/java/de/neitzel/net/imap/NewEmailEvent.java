package de.neitzel.net.imap;

import lombok.Getter;
import lombok.Setter;

import javax.mail.Message;
import java.util.EventObject;

/**
 * Event class representing the occurrence of a new email.
 * It is typically used to encapsulate information about the received email
 * and provide a means for event listeners to handle the event.
 */
public class NewEmailEvent extends EventObject {

    /**
     * Represents the email message associated with the NewEmailEvent.
     * This field encapsulates the content and metadata of the email
     * received, allowing event listeners to process the message details.
     */
    @Getter
    @Setter
    protected Message message;

    /**
     * Indicates whether the event has already been handled or processed.
     * When true, it signifies that the event has been handled and no further
     * processing is required. It is used to prevent multiple handling
     * of the same event.
     */
    @Getter
    @Setter
    protected boolean handled = false;

    /**
     * Constructs a new NewEmailEvent instance.
     * This event encapsulates information about a newly received email,
     * including its associated message and the source of the event.
     *
     * @param source  the object on which the event initially occurred; typically represents
     *                the source of the email event, such as an email client or server.
     * @param message the Message object representing the email associated with this event.
     */
    public NewEmailEvent(Object source, Message message) {
        super(source);
        this.message = message;
    }
}
