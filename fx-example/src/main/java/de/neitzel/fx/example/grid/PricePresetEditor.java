package de.neitzel.fx.example.grid;

import de.neitzel.fx.grid.filter.*;
import de.neitzel.grid.*;

import javafx.beans.property.*;
import javafx.fxml.*;
import javafx.scene.Parent;
import javafx.scene.control.TextField;

import java.io.IOException;
import java.math.BigDecimal;

/** Custom FXML editor emitting the portable numeric condition, executable on both backends. */
public final class PricePresetEditor implements FilterEditor {
    @FXML private TextField maximum;
    private Parent root;
    private String column;
    private final ReadOnlyStringWrapper validation = new ReadOnlyStringWrapper("");

    public static PricePresetEditor load(FilterEditorContext context) {
        try {
            var loader =
                    new FXMLLoader(PricePresetEditor.class.getResource("PricePresetEditor.fxml"));
            Parent root = loader.load();
            PricePresetEditor editor = loader.getController();
            editor.root = root;
            editor.column = context.columnId();
            if (context.committed() instanceof NumericFilter n)
                editor.maximum.setText(n.operand().toPlainString());
            editor.maximum.textProperty().addListener((o, a, b) -> editor.validate());
            editor.validate();
            return editor;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load price preset", e);
        }
    }

    private void validate() {
        try {
            draft();
            validation.set("");
        } catch (RuntimeException e) {
            validation.set(
                    "Enter an exact price with at most two decimal places (0–9,999,999,999.99)");
        }
    }

    public FilterCondition draft() {
        var price =
                new BigDecimal(maximum.getText()).setScale(2, java.math.RoundingMode.UNNECESSARY);
        if (price.signum() < 0 || price.precision() > 12)
            throw new IllegalArgumentException("Price outside domain");
        return new NumericFilter(column, NumericFilter.Operator.LESS_OR_EQUAL, price, null);
    }

    public Parent view() {
        return root;
    }

    public ReadOnlyStringProperty validationMessageProperty() {
        return validation.getReadOnlyProperty();
    }

    public void close() {}
}
