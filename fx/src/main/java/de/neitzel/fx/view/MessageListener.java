package de.neitzel.fx.view;

/**
 * Hears a notice added, dropped, or edited on a ViewModel.
 */
interface MessageListener {

    /**
     * A notice was added.
     *
     * @param message the notice
     */
    void added(Message message);

    /**
     * A notice was dropped.
     *
     * @param message the notice
     */
    void removed(Message message);

    /**
     * A notice kept its place and changed its text.
     *
     * @param message the notice
     */
    void textChanged(Message message);
}
