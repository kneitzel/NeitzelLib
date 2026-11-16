package de.neitzel.fx.docking;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import lombok.Getter;
import lombok.Setter;
import java.util.function.Supplier;

import java.util.Objects;

/** Existing live content with stable identity and bindable tab presentation. */
public final class Dockable {
    @Getter @Setter private boolean mainWindowOnly;
    @Getter private final DockableId id;
    @Getter private final Node content;
    @Getter @Setter private Supplier<? extends Node> focusTarget;
    @Getter @Setter private Runnable onDiscarded;
    @Getter @Setter private CloseCheck closeCheck;
    @Getter @Setter private Runnable onClosed;
    private final StringProperty title = new SimpleStringProperty(this, "title", "");
    private final StringProperty accessibleName = new SimpleStringProperty(this, "accessibleName", "");
    private final ObjectProperty<Node> icon = new SimpleObjectProperty<>(this, "icon");

    public Dockable(DockableId id, Node content) {
        this.id = Objects.requireNonNull(id, "id");
        this.content = Objects.requireNonNull(content, "content");
    }

    /** Name spoken for an icon-only tab; tabs with visible text use their title. */
    public StringProperty accessibleNameProperty() { return accessibleName; }
    public String getAccessibleName() { return accessibleName.get(); }
    public void setAccessibleName(String value) { accessibleName.set(value); }

    public StringProperty titleProperty() { return title; }
    public String getTitle() { return title.get(); }
    public void setTitle(String value) { title.set(value); }
    public ObjectProperty<Node> iconProperty() { return icon; }
    public Node getIcon() { return icon.get(); }
    public void setIcon(Node value) { icon.set(value); }
}
