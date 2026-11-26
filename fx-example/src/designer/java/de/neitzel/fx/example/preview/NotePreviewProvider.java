package de.neitzel.fx.example.preview;

import de.neitzel.fx.example.DeskApp;
import de.neitzel.fx.example.model.Note;
import de.neitzel.fxdesigner.preview.PreviewProvider;
import de.neitzel.fxdesigner.preview.PreviewSession;
import de.neitzel.fx.view.ViewDependencies;

/** Independent sample for the nested note editor. */
public final class NotePreviewProvider implements PreviewProvider<Note, ViewDependencies> {
    @Override
    public Class<Note> modelType() {
        return Note.class;
    }

    @Override
    public PreviewSession<Note, ViewDependencies> open(String id) {
        if (!"note".equals(id)) throw new IllegalArgumentException("Unknown note example: " + id);
        var person = DeskApp.sample();
        return new SampleSession<>(person.getHome().getNote(), DeskApp.dependencies(person));
    }
}
