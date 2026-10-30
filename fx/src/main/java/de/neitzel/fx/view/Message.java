package de.neitzel.fx.view;

/**
 * A notice on a ViewModel with a severity, a text, and a source.
 */
public final class Message {

    /**
     * How serious a notice is.
     */
    public enum Severity {
        /** Informational notice. Not written to the log. */
        INFO,
        /** Warning. Not written to the log. */
        WARN,
        /** Error. Written to the log. */
        ERROR,
        /** Fatal error. Written to the log. */
        FATAL
    }

    private final Severity severity;
    private String text;
    private final String source;

    /**
     * Creates a notice.
     *
     * @param severity how serious the notice is
     * @param text     the notice text
     * @param source   where the notice came from, such as a binding Path
     */
    public Message(Severity severity, String text, String source) {
        this.severity = severity;
        this.text = text;
        this.source = source;
    }

    /**
     * Returns how serious the notice is.
     *
     * @return the severity
     */
    public Severity getSeverity() {
        return severity;
    }

    /**
     * Returns the notice text.
     *
     * @return the text
     */
    public String getText() {
        return text;
    }

    /**
     * Returns where the notice came from.
     *
     * @return the source
     */
    public String getSource() {
        return source;
    }

    /**
     * Replaces the text of a binding failure that is already in the list.
     *
     * @param text the new text
     */
    void setText(String text) {
        this.text = text;
    }
}
