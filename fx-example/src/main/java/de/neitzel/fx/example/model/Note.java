package de.neitzel.fx.example.model;

/**
 * A short note attached to an address.
 * <p>
 * The class is a complex attribute of {@link Address}: it has a read-write property of its own.
 * The home address form nests a component that edits this note.
 */
public class Note {

    /**
     * The note text.
     */
    private String text = "";

    /**
     * Creates an empty note.
     */
    public Note() {
    }

    /**
     * Creates a note with the given text.
     *
     * @param text the note text
     */
    public Note(String text) {
        this.text = text;
    }

    /**
     * Returns the note text.
     *
     * @return the text
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the note text.
     *
     * @param text the text
     */
    public void setText(String text) {
        this.text = text;
    }
}
