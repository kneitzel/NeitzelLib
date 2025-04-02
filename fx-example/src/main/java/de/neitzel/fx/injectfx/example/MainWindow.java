package de.neitzel.fx.injectfx.example;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.ResourceBundle;

public class MainWindow implements Initializable {

    private int counter = 0;

    @FXML
    private TextField textField;

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
