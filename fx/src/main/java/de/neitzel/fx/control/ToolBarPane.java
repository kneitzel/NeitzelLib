package de.neitzel.fx.control;

import javafx.beans.DefaultProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;

/** A horizontal JavaFX toolbar with an optional dismissal button at its leading edge. */
@DefaultProperty("items")
public final class ToolBarPane extends BorderPane {
    private final ToolBar toolbar = new ToolBar();
    private final Button dismiss = new Button("×");
    private final BooleanProperty dismissible = new SimpleBooleanProperty(this, "dismissible");
    private final ObjectProperty<EventHandler<ActionEvent>> onDismiss =
            new SimpleObjectProperty<>(this, "onDismiss");

    public ToolBarPane() {
        getStyleClass().add("tool-bar-pane");
        setCenter(toolbar);
        dismiss.getStyleClass().add("tool-bar-dismiss");
        dismiss.setAccessibleText("Close toolbar");
        BorderPane.setAlignment(dismiss, Pos.CENTER_LEFT);
        BorderPane.setMargin(dismiss, new Insets(0, 3, 0, 3));
        dismiss.visibleProperty().bind(dismissible);
        dismiss.managedProperty().bind(dismissible);
        dismissible.addListener((property, old, enabled) -> setLeft(enabled ? dismiss : null));
        dismiss.setOnAction(event -> {
            setVisible(false);
            var handler = getOnDismiss();
            if (handler != null) handler.handle(event);
        });
    }

    public ObservableList<Node> getItems() {
        return toolbar.getItems();
    }

    public BooleanProperty dismissibleProperty() { return dismissible; }
    public boolean isDismissible() { return dismissible.get(); }
    public void setDismissible(boolean value) { dismissible.set(value); }

    public ObjectProperty<EventHandler<ActionEvent>> onDismissProperty() { return onDismiss; }
    public EventHandler<ActionEvent> getOnDismiss() { return onDismiss.get(); }
    public void setOnDismiss(EventHandler<ActionEvent> value) { onDismiss.set(value); }

    public String getDismissText() { return dismiss.getAccessibleText(); }
    public void setDismissText(String value) { dismiss.setAccessibleText(value); }
}
