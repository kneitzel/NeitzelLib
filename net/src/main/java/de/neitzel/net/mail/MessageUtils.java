package de.neitzel.net.mail;

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.ContentType;
import javax.mail.internet.MimeMultipart;
import java.io.IOException;

/**
 * Utility functions to use with javax.mail.Message instances.
 */
public class MessageUtils {
    /**
     * Gets the text of the email message.
     * @param message Message to get the text from.
     * @return Text of the email message.
     * @throws IOException
     * @throws MessagingException
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
     * Gets the text of a MimeMultipart.
     * @param mimeMultipart MimeMultipart to get the text from.
     * @return Text of the MimeMultipart instance.
     * @throws IOException
     * @throws MessagingException
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
        String result = "";
        for (int i = 0; i < count; i++) {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            result += getTextFromBodyPart(bodyPart);
        }
        return result;
    }

    /**
     * Gets the text of a BodyPart
     * @param bodyPart BodyPart to get text from.
     * @return Text of body part or empty string if not a known type.
     * @throws IOException
     * @throws MessagingException
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
        } else if (bodyPart.getContent() instanceof MimeMultipart){
            result = getTextFromMimeMultipart((MimeMultipart)bodyPart.getContent());
        }
        return result;
    }

}
