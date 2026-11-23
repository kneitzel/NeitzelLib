package de.neitzel.fx.view;

import java.util.Map;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.MenuItem;

/** Attaches reusable commands to JavaFX action controls. */
public final class CommandBindings {

    private static final Object REGISTRATION_KEY = new Object();

    private CommandBindings() {
    }

    /** A single control's attachment to a command. Close on the JavaFX application thread. */
    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        /** Removes this attachment. Subsequent calls have no effect. */
        @Override
        void close();
    }

    /**
     * Attaches the command to a button's action and disable properties.
     *
     * @param button the action control
     * @param command the command to execute
     * @return the attachment to close when the control is no longer in use
     */
    public static Registration bind(ButtonBase button, Command command) {
        return attach(button.getProperties(), button.disableProperty(), button.onActionProperty(), command);
    }

    /**
     * Attaches the command to a menu item's action and disable properties.
     *
     * @param item the menu action
     * @param command the command to execute
     * @return the attachment to close when the menu item is no longer in use
     */
    public static Registration bind(MenuItem item, Command command) {
        return attach(item.getProperties(), item.disableProperty(), item.onActionProperty(), command);
    }

    private static Registration attach(Map<Object, Object> properties, BooleanProperty disable,
                                       ObjectProperty<EventHandler<ActionEvent>> onAction, Command command) {
        Objects.requireNonNull(command);
        if (properties.containsKey(REGISTRATION_KEY) || disable.isBound()) {
            throw new IllegalArgumentException("disable is already bound");
        }
        if (onAction.isBound() || onAction.get() != null) {
            throw new IllegalArgumentException("onAction already has an owner");
        }
        var available = command.getCanExecute();
        ChangeListener<Boolean> update = (observable, before, after) -> disable.set(!after);
        EventHandler<ActionEvent> handler = event -> command.execute();
        disable.set(!available.get());
        onAction.set(handler);
        available.addListener(update);
        properties.put(REGISTRATION_KEY, handler);
        return () -> {
            if (properties.remove(REGISTRATION_KEY, handler)) {
                available.removeListener(update);
                if (onAction.get() == handler && !onAction.isBound()) {
                    onAction.set(null);
                }
            }
        };
    }
}
