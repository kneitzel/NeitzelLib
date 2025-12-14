package de.neitzel.fx.injectfx.example;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller for the MainWindow FXML view.
 *
 * <p>This class acts as the backing controller for the {@code MainWindow.fxml} UI. It demonstrates a
 * minimal example of a JavaFX controller that:
 * <ul>
 *   <li>receives UI element injection via {@link FXML} (the {@link #textField} field),</li>
 *   <li>initializes the view in {@link #initialize(URL, ResourceBundle)}, and</li>
 *   <li>handles user actions via {@link #onButtonClick(ActionEvent)}.</li>
 * </ul>
 *
 * <p>Usage notes:
 * <ul>
 *   <li>Fields annotated with {@link FXML} are injected by the {@code FXMLLoader} before
 *       {@link #initialize(URL, ResourceBundle)} is called — do not expect them to be non-null
 *       in the constructor.</li>
 *   <li>All UI updates must run on the JavaFX Application Thread. Methods called by FXML
 *       (such as {@link #onButtonClick(ActionEvent)}) are executed on that thread by the
 *       JavaFX runtime.</li>
 *   <li>The controller keeps a simple click counter and updates {@link #textField} to reflect
 *       the current count using {@link #displayCounter()}.</li>
 * </ul>
 */
public class MainWindow implements Initializable {

    /**
     * Counter for button clicks shown in the UI.
     */
    private int counter = 0;

    /**
     * Text field UI control showing the click counter.
     */
    @FXML
    private TextField textField;

    /**
     * Default constructor only
     */
    public MainWindow() {
        // default constructor only
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        displayCounter();
    }

    private void displayCounter() {
        textField.setText("Click counter: " + counter);
    }

    @FXML
    private void onButtonClick(ActionEvent actionEvent) {
        counter++;
        displayCounter();
    }
}
