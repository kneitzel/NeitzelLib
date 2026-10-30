package de.neitzel.fx.view;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Holds the binding lists instantiated while one View is loading.
 * <p>
 * FXML objects declared in {@code fx:define} are not scene-graph children and have no
 * {@code fx:id} in the accepted document shape, so each {@link BindingList} registers
 * itself here from its constructor. A Component document opens a nested session, so its
 * lists stay off the parent session.
 */
final class LoadSession {

    private static final ThreadLocal<Deque<LoadSession>> STACK = new ThreadLocal<>();

    private final List<BindingList> bindingLists = new ArrayList<>();

    private LoadSession() {
    }

    /**
     * Starts a session on this thread.
     *
     * @return the open session
     */
    static LoadSession open() {
        Deque<LoadSession> stack = new ArrayDeque<>();
        LoadSession session = new LoadSession();
        stack.push(session);
        STACK.set(stack);
        return session;
    }

    /**
     * Opens a nested session for a Component document.
     * <p>
     * Binding lists created until {@link #unnest()} register here, not on the parent session.
     *
     * @return the nested session
     */
    static LoadSession nest() {
        LoadSession session = new LoadSession();
        STACK.get().push(session);
        return session;
    }

    /**
     * Restores the session that was current before {@link #nest()}.
     */
    static void unnest() {
        STACK.get().pop();
    }

    /**
     * Drops the session for this thread.
     */
    static void close() {
        STACK.remove();
    }

    /**
     * Registers a binding list created while a session is open.
     *
     * @param bindingList the list FXML just constructed
     */
    static void register(BindingList bindingList) {
        Deque<LoadSession> stack = STACK.get();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.peek().bindingLists.add(bindingList);
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
