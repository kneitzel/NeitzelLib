package de.neitzel.fx.example.grid;

/** Classpath launcher separate from Application for JavaFX startup. */
public final class ProductGridMain {
    private ProductGridMain() {}

    public static void main(String[] args) {
        javafx.application.Application.launch(ProductGridApp.class, args);
    }
}
