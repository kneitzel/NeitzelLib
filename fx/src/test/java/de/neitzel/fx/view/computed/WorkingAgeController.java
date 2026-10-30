package de.neitzel.fx.view.computed;

import jakarta.inject.Singleton;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;

/**
 * Controller that derives a label from the name field and does not write the Model.
 */
@Singleton
public class WorkingAgeController {

    @FXML
    private TextField nameField;

    /**
     * Returns a literal chosen from the current name field.
     *
     * @return the value written to the label
     */
    public String workingAge() {
        if ("Ada".equals(nameField.getText())) {
            return "initial-age";
        }
        if ("Grace".equals(nameField.getText())) {
            return "typed-age";
        }
        return "model-age";
    }
}
