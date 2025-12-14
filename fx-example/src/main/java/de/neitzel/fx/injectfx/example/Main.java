package de.neitzel.fx.injectfx.example;

/**
 * Another Main class as workaround when the JavaFX Application ist started without
 * taking care os Classloader Requirements of JavaFX. (Important when starting from inside NetBeans!)
 */
public class Main {

    /**
     * Creates a new helper instance of {@code Main}.
     *
     * <p>This constructor is intentionally empty. The {@code Main} class serves as an alternative
     * entry point to launch the JavaFX application via {@link JavaFXApp#main(String[])} in
     * environments (IDEs, build tools) where directly starting a class that extends
     * {@code javafx.application.Application} may fail due to classloader or JVM startup constraints.
     *
     * <p>Do not perform application initialization here; use the static {@code main} method to
     * start the JavaFX runtime. This constructor exists to allow tooling and frameworks to
     * instantiate the class reflectively if required.
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
        JavaFXApp.main(args);
    }
}
