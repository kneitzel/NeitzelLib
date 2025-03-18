package de.neitzel.injectfx.example;

/**
 * Another Main class as workaround when the JavaFX Application ist started without
 * taking care os Classloader Requirements of JavaFX. (Important when starting from inside NetBeans!)
 */
public class Main {
    /**
     * Additional main methode to start Application.
     * @param args Commandline Arguments.
     */
    public static void main(String[] args) {
        JavaFXApp.main(args);
    }
}
