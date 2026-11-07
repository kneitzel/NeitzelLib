package de.neitzel.fx.view.chosen;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Controller that shows a greeting supplied by an injected service.
 */
@Singleton
public class DeskController {

    /**
     * The service the greeting method reads.
     */
    private final DeskGreeting greeting;

    /**
     * Creates a controller that reads its label from the injected greeting.
     *
     * @param greeting the service that supplies the greeting
     */
    @Inject
    public DeskController(DeskGreeting greeting) {
        this.greeting = greeting;
    }

    /**
     * Returns the greeting from the injected service.
     *
     * @return the label text
     */
    public String deskGreeting() {
        return greeting.text();
    }
}
