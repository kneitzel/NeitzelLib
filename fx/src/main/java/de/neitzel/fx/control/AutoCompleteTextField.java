package de.neitzel.fx.control;

import java.util.Locale;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.stage.Popup;

/** A free-form field offering case-insensitive prefix matches from an observable list. */
public final class AutoCompleteTextField extends TextField {
    private static final int ROW_HEIGHT = 26;
    private final ObservableList<String> suggestions = FXCollections.observableArrayList();
    private final ListView<String> choices = new ListView<>();
    private final Popup popup = new Popup();
    private boolean accepting;

    public AutoCompleteTextField() {
        popup.setAutoHide(true);
        popup.getContent().add(choices);
        choices.setFocusTraversable(false);
        choices.setFixedCellSize(ROW_HEIGHT);
        choices.setOnMouseClicked(event -> {
            var value = choices.getSelectionModel().getSelectedItem();
            if (event.getButton() == MouseButton.PRIMARY && value != null) accept(value);
        });
        suggestions.addListener((ListChangeListener<String>) change -> updateSuggestions());
        textProperty().addListener((property, before, after) -> updateSuggestions());
        sceneProperty().addListener((property, before, after) -> popup.hide());
        disabledProperty().addListener((property, before, disabled) -> {
            if (disabled) popup.hide();
        });
        addEventFilter(KeyEvent.KEY_PRESSED, this::handleKey);
    }

    public ObservableList<String> getSuggestions() {
        return suggestions;
    }

    private void handleKey(KeyEvent event) {
        var selection = choices.getSelectionModel();
        if (event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.UP && popup.isShowing()) {
            if (!popup.isShowing()) updateSuggestions();
            if (!popup.isShowing()) return;
            if (event.getCode() == KeyCode.DOWN) selection.selectNext();
            else selection.selectPrevious();
            choices.scrollTo(selection.getSelectedIndex());
            event.consume();
        } else if (popup.isShowing() && event.getCode() == KeyCode.ESCAPE) {
            popup.hide();
            event.consume();
        } else if (popup.isShowing() && event.getCode() == KeyCode.ENTER) {
            var value = selection.getSelectedItem();
            popup.hide();
            if (value != null) {
                accept(value);
                event.consume();
            }
        } else if (event.getCode() == KeyCode.TAB) {
            popup.hide();
        }
    }

    private void updateSuggestions() {
        if (accepting || !isFocused() || isDisabled() || getScene() == null
                || getScene().getWindow() == null || !getScene().getWindow().isShowing()) return;
        String prefix = getText() == null ? "" : getText().strip().toLowerCase(Locale.ROOT);
        var matches = prefix.isEmpty() ? java.util.List.<String>of()
                : suggestions.stream().filter(value -> value != null && !value.isBlank())
                        .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).distinct().limit(10).toList();
        choices.getItems().setAll(matches);
        choices.getSelectionModel().clearSelection();
        if (matches.isEmpty()) {
            popup.hide();
            return;
        }
        var bounds = localToScreen(getBoundsInLocal());
        if (bounds == null) return;
        choices.setPrefSize(Math.max(170, bounds.getWidth()), ROW_HEIGHT * matches.size() + 2);
        if (!popup.isShowing()) popup.show(this, bounds.getMinX(), bounds.getMaxY());
    }

    private void accept(String value) {
        accepting = true;
        try {
            popup.hide();
            setText(value);
            positionCaret(value.length());
            fireEvent(new ActionEvent());
        } finally {
            accepting = false;
        }
    }
}
