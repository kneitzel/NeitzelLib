package de.neitzel.fx.view.managed;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import lombok.Getter;

@Singleton
public class ViewController implements AutoCloseable {

    @Singleton
    public static class Registry {

        @Getter
        private ViewController lastCreated;

        @Inject
        public Registry() {
        }
    }

    private final Registry registry;

    @FXML
    @Getter
    private TextField input;

    @Getter
    private int closeCount;

    @Inject
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
