package de.neitzel.fx.example.preview;

import de.neitzel.fx.example.DeskApp;
import de.neitzel.fx.example.model.Person;
import de.neitzel.fxdesigner.preview.PreviewProvider;
import de.neitzel.fxdesigner.preview.PreviewSession;
import de.neitzel.fx.view.ViewDependencies;

/** Optional test-source preview adapter; it never enters the application distribution. */
public final class DeskPreviewProvider implements PreviewProvider<Person, ViewDependencies> {
    @Override
    public Class<Person> modelType() {
        return Person.class;
    }

    @Override
    public PreviewSession<Person, ViewDependencies> open(String id) {
        if (!"desk".equals(id)) throw new IllegalArgumentException("Unknown desk example: " + id);
        var person = DeskApp.sample();
        return new SampleSession<>(person, DeskApp.dependencies(person));
    }
}
