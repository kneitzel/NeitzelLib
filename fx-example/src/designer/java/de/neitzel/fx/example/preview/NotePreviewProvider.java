package de.neitzel.fx.example.preview;

import de.neitzel.fx.example.DeskApp;
import de.neitzel.fx.example.model.Note;
import de.neitzel.fxdesigner.preview.PreviewProvider;
import de.neitzel.fxdesigner.preview.PreviewSession;
import de.neitzel.injection.ApplicationContext;

/** Independent sample for the nested note editor. */
public final class NotePreviewProvider implements PreviewProvider<Note, ApplicationContext> {
    @Override
    public Class<Note> modelType() {
        return Note.class;
    }

    @Override
    public PreviewSession<Note, ApplicationContext> open(String id) {
        if (!"note".equals(id)) throw new IllegalArgumentException("Unknown note example: " + id);
        return new SampleSession<>(
                DeskApp.sample().getHome().getNote(), new ApplicationContext(DeskApp.class));
    }
}
