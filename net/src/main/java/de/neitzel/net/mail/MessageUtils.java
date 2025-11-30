package de.neitzel.net.mail;

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.ContentType;
import javax.mail.internet.MimeMultipart;
import java.io.IOException;

/**
 * Utility methods for extracting textual content from {@link javax.mail.Message} instances.
 *
 * <p>This class provides helpers to obtain a best-effort plain-text representation of an email's
 * content. It supports the common MIME types:
 * <ul>
 *   <li>{@code text/plain} — returned as-is</li>
 *   <li>{@code text/html} — the HTML body is returned as a string (not converted to plain text)</li>
 *   <li>{@code multipart/*} — parts are inspected recursively; for {@code multipart/alternative}
 *       the last (most faithful) alternative is preferred; for other multipart types the textual
 *       content of all parts is concatenated in order.</li>
 * </ul>
 *
 * <p>All methods return an empty string when no textual content can be found. The methods may throw
 * {@link IOException} or {@link MessagingException} when the underlying message parts cannot be read.
 */
@SuppressWarnings("unused")
public class MessageUtils {

    /**
     * Private constructor for utility class.
     * Prevents instantiation of this utility class.
     */
    private MessageUtils() {
        // Utility class - private constructor
    }

    /**
     * Extracts a best-effort textual representation from the provided {@link Message}.
     *
     * <p>Behavior:
     * <ul>
     *   <li>If the message mime-type is {@code text/plain} the plain text content is returned.</li>
     *   <li>If the message mime-type is {@code text/html} the HTML content is returned as a string.
     *       (This method does not perform HTML-to-plain-text conversion.)</li>
     *   <li>If the message mime-type is {@code multipart/*} the method inspects all parts recursively.
     *       For {@code multipart/alternative} the most faithful alternative (the last part) is chosen.
     *       For other multipart types the textual contents of all parts are concatenated in order.</li>
     * </ul>
     *
     * @param message the email message from which to extract text; must not be {@code null}
     * @return a <strong>non-null</strong> string containing the extracted text (possibly empty)
     * @throws IOException        if an I/O error occurs while reading message content
     * @throws MessagingException if the message content cannot be processed due to MIME/format errors
     */
    public static String getTextFromMessage(Message message) throws IOException, MessagingException {
        String result = "";
        if (message.isMimeType("text/plain")) {
            result = message.getContent().toString();
        } else if (message.isMimeType("multipart/*")) {
            MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
            result = getTextFromMimeMultipart(mimeMultipart);
        }
        return result;
    }

    /**
     * Extracts the textual content from a {@link MimeMultipart} instance.
     *
     * <p>Algorithm:
     * <ul>
     *   <li>If the multipart has zero parts a {@link MessagingException} is thrown.</li>
     *   <li>If the multipart's content type matches {@code multipart/alternative} the last part
     *       (the most faithful alternative) is returned.</li>
     *   <li>Otherwise the method concatenates the result of {@link #getTextFromBodyPart(BodyPart)}
     *       for each part in document order and returns the combined text.</li>
     * </ul>
     *
     * <p>The method handles nested multiparts by recursive calls.
     *
     * @param mimeMultipart the multipart object to inspect
     * @return a non-null string containing the concatenated textual content of the multipart
     * @throws IOException        if an I/O error occurs while reading parts
     * @throws MessagingException if the multipart is malformed or cannot be processed
     */
    private static String getTextFromMimeMultipart(
            MimeMultipart mimeMultipart) throws IOException, MessagingException {

        int count = mimeMultipart.getCount();
        if (count == 0)
            throw new MessagingException("Multipart with no body parts not supported.");
        boolean multipartAlt = new ContentType(mimeMultipart.getContentType()).match("multipart/alternative");
        if (multipartAlt)
            // alternatives appear in an order of increasing
            // faithfulness to the original content. Customize as req'd.
            return getTextFromBodyPart(mimeMultipart.getBodyPart(count - 1));
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            result.append(getTextFromBodyPart(bodyPart));
        }
        return result.toString();
    }

    /**
     * Extracts textual content from a single {@link BodyPart}.
     *
     * <p>Behavior:
     * <ul>
     *   <li>{@code text/plain}: returns the plain text content.</li>
     *   <li>{@code text/html}: returns the HTML content as a string (no conversion performed).</li>
     *   <li>If the body part itself contains a {@link MimeMultipart} this method recurses into it.</li>
     *   <li>For unknown or non-textual content types an empty string is returned.</li>
     * </ul>
     *
     * @param bodyPart the body part to extract text from
     * @return the textual content of the body part, or an empty string if none is available
     * @throws IOException        if an I/O error occurs while reading the body part content
     * @throws MessagingException if the body part cannot be processed
     */
    private static String getTextFromBodyPart(
            BodyPart bodyPart) throws IOException, MessagingException {

        String result = "";
        if (bodyPart.isMimeType("text/plain")) {
            result = (String) bodyPart.getContent();
        } else if (bodyPart.isMimeType("text/html")) {
            result = (String) bodyPart.getContent();
            //String html = (String) bodyPart.getContent();
            // Not parsing the html right now!
            // result = org.jsoup.Jsoup.parse(html).text();
        } else if (bodyPart.getContent() instanceof MimeMultipart) {
            result = getTextFromMimeMultipart((MimeMultipart) bodyPart.getContent());
        }
        return result;
    }

}
