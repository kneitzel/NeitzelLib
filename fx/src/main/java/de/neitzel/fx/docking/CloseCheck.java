package de.neitzel.fx.docking;

import java.util.concurrent.CompletionStage;

/** Application consent callback, invoked on the JavaFX Application Thread. */
@FunctionalInterface
public interface CloseCheck {
    /**
     * Asks whether the content can close, without closing it.
     *
     * @param request affected scope and owning window
     * @return eventual consent; background saves may complete it from another thread
     */
    CompletionStage<CloseConsent> check(CloseRequest request);
}
