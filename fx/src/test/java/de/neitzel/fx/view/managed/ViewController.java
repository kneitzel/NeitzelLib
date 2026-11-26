package de.neitzel.fx.view.managed;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import lombok.Getter;


public class ViewController implements AutoCloseable {


    public static class Registry {

        @Getter
        private ViewController lastCreated;


        public Registry() {
        }
    }

    private final Registry registry;

    @FXML
    @Getter
    private TextField input;

    @Getter
    private int closeCount;


    public ViewController(Registry registry) {
        this.registry = registry;
    }

    @FXML
    public void initialize() {
        registry.lastCreated = this;
        input.setUserData(this);
    }

    @FXML
    public void onAction() {
        input.setText("Handled");
    }

    @Override
    public void close() {
        closeCount++;
    }
}
