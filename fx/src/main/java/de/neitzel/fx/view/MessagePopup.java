package de.neitzel.fx.view;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;

/**
 * Shows each newly added {@link Message} in a dialog when the developer asks.
 * <p>
 * The library shows nothing until {@link #showNewMessages(ViewModel)} is called. An in-place
 * update of an existing binding failure is not a new Message, so it does not open another dialog.
 */
public final class MessagePopup {

    private MessagePopup() {
    }

    /**
     * Shows each Message added to {@code viewModel} after this call in a dialog.
     * <p>
     * The dialog is shown with {@link Dialog#show()}, so the caller is not blocked. Messages
     * already on the ViewModel stay where they are. A later in-place update of a binding failure
     * does not open another dialog.
     *
     * @param viewModel the ViewModel whose new Messages should be shown
     */
    public static void showNewMessages(ViewModel viewModel) {
        viewModel.whenMessageAdded(MessagePopup::show);
    }

    /**
     * Opens a dialog whose content is the Message text.
     *
     * @param message the notice to show
     */
    private static void show(Message message) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setHeaderText(null);
        dialog.getDialogPane().setContent(new Label(message.getText()));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.show();
    }
}
