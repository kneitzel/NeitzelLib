package de.neitzel.fx.view;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the binding lists instantiated while one View is loading.
 * <p>
 * FXML objects declared in {@code fx:define} are not scene-graph children and have no
 * {@code fx:id} in the accepted document shape, so each {@link BindingList} registers
 * itself here from its constructor.
 */
final class LoadSession {

    private static final ThreadLocal<LoadSession> CURRENT = new ThreadLocal<>();

    private final List<BindingList> bindingLists = new ArrayList<>();

    private LoadSession() {
    }

    /**
     * Starts a session on this thread.
     *
     * @return the open session
     */
    static LoadSession open() {
        LoadSession session = new LoadSession();
        CURRENT.set(session);
        return session;
    }

    /**
     * Drops the session for this thread.
     */
    static void close() {
        CURRENT.remove();
    }

    /**
     * Registers a binding list created while a session is open.
     *
     * @param bindingList the list FXML just constructed
     */
    static void register(BindingList bindingList) {
        LoadSession session = CURRENT.get();
        if (session != null) {
            session.bindingLists.add(bindingList);
        }
    }

    /**
     * Returns the lists registered during this session, in document order.
     *
     * @return the binding lists
     */
    List<BindingList> bindingLists() {
        return bindingLists;
    }
}
