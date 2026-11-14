package de.neitzel.fx.example.model;

import lombok.Getter;
import lombok.Setter;

/**
 * A short note attached to an address.
 * <p>
 * The class is a complex attribute of {@link Address}: it has a read-write property of its own.
 * The home address form nests a component that edits this note.
 */
@Getter
@Setter
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

}
