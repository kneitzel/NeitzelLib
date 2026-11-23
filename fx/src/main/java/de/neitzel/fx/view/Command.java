package de.neitzel.fx.view;

import java.util.Objects;
import javafx.beans.value.ObservableBooleanValue;
import lombok.Getter;

/**
 * One action with a live availability rule. Use the command and its availability on the
 * JavaFX application thread.
 */
public final class Command {

    /** The availability observed by each control displaying this command. */
    @Getter
    private final ObservableBooleanValue canExecute;

    private final Runnable action;

    /**
     * Creates a command for one action and execution context.
     *
     * @param canExecute live availability of the action
     * @param action the work to start while the command is available
     */
    public Command(ObservableBooleanValue canExecute, Runnable action) {
        this.canExecute = Objects.requireNonNull(canExecute);
        this.action = Objects.requireNonNull(action);
    }

    /**
     * Executes the action after checking the current availability, including for keyboard
     * shortcuts or callers without an attached control.
     */
    public void execute() {
        if (canExecute.get()) {
            action.run();
        }
    }
}
