package de.neitzel.fx.component.example;

/**
 * Another Main class as workaround when the JavaFX Application ist started without
 * taking care os Classloader Requirements of JavaFX. (Important when starting from inside NetBeans!)
 */
public class Main {

    /**
     * Default constructor only
     */
    public Main() {
        // default constructor only
    }

    /**
     * Additional main methode to start Application.
     *
     * @param args Commandline Arguments.
     */
    public static void main(String[] args) {
        ExampleApp.main(args);
    }
}
