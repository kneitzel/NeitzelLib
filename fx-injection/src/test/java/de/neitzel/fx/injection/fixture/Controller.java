package de.neitzel.fx.injection.fixture;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.scene.control.Label;
import javafx.fxml.FXML;
import lombok.Getter;

@Singleton
public final class Controller implements AutoCloseable {
    @Getter
    private final Lifecycle lifecycle;

    @FXML
    private Label caption;

    @Inject
    public Controller(Lifecycle lifecycle) {
        this.lifecycle = lifecycle;
        lifecycle.opened(this);
    }

    @FXML
    private void initialize() {
        caption.setUserData(this);
    }

    @Override
    public void close() {
        lifecycle.closed(this);
    }
}
