package de.neitzel.fx.control;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/** A rounded tag label with an optional remove action. */
public final class TagChip extends HBox {
    private final Label label = new Label();
    private final Button remove = new Button("×");
    private final BooleanProperty removable = new SimpleBooleanProperty(this, "removable");

    public TagChip() {
        getStyleClass().add("tag-chip");
        getStylesheets().add(TagChip.class.getResource("tag-chip.css").toExternalForm());
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(3);
        remove.getStyleClass().add("tag-chip-remove");
        remove.accessibleTextProperty().bind(Bindings.concat("Remove ", label.textProperty()));
        remove.visibleProperty().bind(removable);
        remove.managedProperty().bind(removable);
        getChildren().addAll(label, remove);
    }

    public TagChip(String text) {
        this();
        setText(text);
    }

    public StringProperty textProperty() { return label.textProperty(); }
    public String getText() { return label.getText(); }
    public void setText(String value) { label.setText(value); }
    public BooleanProperty removableProperty() { return removable; }
    public boolean isRemovable() { return removable.get(); }
    public void setRemovable(boolean value) { removable.set(value); }
    public EventHandler<ActionEvent> getOnRemove() { return remove.getOnAction(); }
    public void setOnRemove(EventHandler<ActionEvent> handler) { remove.setOnAction(handler); }
}
