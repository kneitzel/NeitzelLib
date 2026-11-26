package de.neitzel.fx.injection.fixture;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.VBox;

@Singleton
public final class Card extends VBox implements AutoCloseable {
    private final Lifecycle lifecycle;
    private final ObjectProperty<Entry> model = new SimpleObjectProperty<>(this, "model");

    @Inject
    public Card(Lifecycle lifecycle) {
        this.lifecycle = lifecycle;
        lifecycle.opened(this);
    }

    public ObjectProperty<Entry> modelProperty() { return model; }

    public Entry getModel() { return model.get(); }

    public void setModel(Entry entry) { model.set(entry); }

    @Override
    public void close() { lifecycle.closed(this); }
}
