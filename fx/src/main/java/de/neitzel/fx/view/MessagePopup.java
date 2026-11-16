package de.neitzel.fx.view;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;

/**
 * Shows each newly added {@link Message} in a dialog when the developer asks.
 *
 * <p>The library shows nothing until {@link #showNewMessages(ViewModel)} is called. An in-place
 * update of an existing binding failure is not a new Message, so it does not open another dialog.
 */
public final class MessagePopup {

    /** Prevents instantiation. */
    private MessagePopup() {}

    /**
     * Shows each Message added to {@code viewModel} after this call in a dialog.
     *
     * <p>The dialog is shown with {@link Dialog#show()}, so the caller is not blocked. Messages
     * already on the ViewModel stay where they are. A later in-place update of a binding failure
     * does not open another dialog.
     *
     * @param viewModel the ViewModel whose new Messages should be shown
     */
    public static void showNewMessages(ViewModel viewModel) {
        viewModel.whenMessageAdded(message -> show(message, null));
    }

    /**
     * Shows new messages in owned dialogs, allowing theme inheritance from the owner scene.
     *
     * @param viewModel the ViewModel whose new Messages should be shown
     * @param owner the application window that owns the dialogs
     */
    public static void showNewMessages(ViewModel viewModel, javafx.stage.Window owner) {
        java.util.Objects.requireNonNull(owner);
        viewModel.whenMessageAdded(message -> show(message, owner));
    }

    private static void show(Message message, javafx.stage.Window owner) {
        Dialog<Void> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setHeaderText(null);
        dialog.getDialogPane().setContent(new Label(message.getText()));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.show();
    }
}
