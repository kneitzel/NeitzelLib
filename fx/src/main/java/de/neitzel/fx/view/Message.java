package de.neitzel.fx.view;

import lombok.Getter;

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

    /**
     * How serious the notice is.
     */
    @Getter
    private final Severity severity;

    /**
     * The notice text. A binding failure updates this in place.
     */
    @Getter
    private String text;

    /**
     * Where the notice came from, such as a binding Path.
     */
    @Getter
    private final String source;

    /**
     * The child notice this one copies, or {@code null} when this notice was added directly.
     */
    private Message origin;

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
     * Replaces the text of a binding failure that is already in the list.
     *
     * @param text the new text
     */
    void setText(String text) {
        this.text = text;
    }

    /**
     * Returns a copy whose source is extended for a parent ViewModel.
     * <p>
     * The copy remembers this notice, so a cycle does not carry it back onto the ViewModel
     * that already holds it.
     *
     * @param source the extended source
     * @return the copy
     */
    Message withExtendedSource(String source) {
        Message copy = new Message(severity, text, source);
        copy.origin = this;
        return copy;
    }

    /**
     * Returns the notice this copy mirrors.
     *
     * @return that notice, or {@code null} when this notice was added on its own ViewModel
     */
    Message origin() {
        return origin;
    }
}
