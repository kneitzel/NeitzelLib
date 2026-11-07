package de.neitzel.fx.control;

import javafx.scene.control.Spinner;
import javafx.scene.control.TextFormatter;

/** Factories for bounded integer inputs that commit valid typing immediately. */
public final class IntegerSpinners {
    private IntegerSpinners() {
    }

    /**
     * Creates a nonnegative bounded input. Valid typing immediately updates its value;
     * incomplete values below the minimum revert to the committed value on focus loss.
     *
     * @param min inclusive nonnegative minimum
     * @param max inclusive maximum
     * @param value initial value
     * @return an editable spinner
     */
    public static Spinner<Integer> nonNegative(int min, int max, int value) {
        if (min < 0 || max < min || value < min || value > max) {
            throw new IllegalArgumentException("Invalid nonnegative spinner bounds or value");
        }
        Spinner<Integer> spinner = new Spinner<>(min, max, value);
        spinner.setEditable(true);
        spinner.getEditor().setTextFormatter(new TextFormatter<String>(change -> {
            String text = change.getControlNewText();
            if (!text.matches("\\d{0,10}")) {
                return null;
            }
            if (!text.isEmpty() && Long.parseLong(text) > max) {
                return null;
            }
            return change;
        }));
        spinner.getEditor().textProperty().addListener((observable, old, text) -> {
            if (!text.isEmpty()) {
                int parsed = Integer.parseInt(text);
                if (parsed >= min) {
                    spinner.getValueFactory().setValue(parsed);
                }
            }
        });
        spinner.getEditor().focusedProperty().addListener((observable, old, focused) -> {
            if (!focused) {
                spinner.getEditor().setText(spinner.getValue().toString());
            }
        });
        return spinner;
    }
}
