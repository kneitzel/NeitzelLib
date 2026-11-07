package de.neitzel.fx.view;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Holds the binding lists instantiated while one View is loading.
 * <p>
 * FXML objects declared in {@code fx:define} are not scene-graph children and have no
 * {@code fx:id} in the accepted document shape, so each {@link BindingList} registers
 * itself here from its constructor. A Component document opens a nested session, so its
 * lists stay off the parent session.
 */
final class LoadSession {

    /**
     * The sessions open on this thread. The last one is the outermost load.
     */
    private static final ThreadLocal<Deque<LoadSession>> STACK = new ThreadLocal<>();

    /**
     * Binding lists created while this session is the current one, in document order.
     */
    private final List<BindingList> bindingLists = new ArrayList<>();

    /**
     * Component classes already built during the outermost load.
     */
    private final Set<Class<?>> built = new HashSet<>();

    /**
     * Prevents instantiation from outside this class.
     */
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
     * A Component created after the parent load has finished, such as a repeated child added
     * later, still gets a session so its own binding list can be captured.
     *
     * @return the nested session
     */
    static LoadSession nest() {
        Deque<LoadSession> stack = STACK.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            STACK.set(stack);
        }
        LoadSession session = new LoadSession();
        stack.push(session);
        return session;
    }

    /**
     * Restores the session that was current before {@link #nest()}.
     * <p>
     * When the nested session was the only one, the thread-local stack is dropped.
     */
    static void unnest() {
        Deque<LoadSession> stack = STACK.get();
        stack.pop();
        if (stack.isEmpty()) {
            STACK.remove();
        }
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
     * Records the first Component of {@code type} built during this load.
     * <p>
     * Nested documents share the outermost session, so a second Component of the same class
     * anywhere in the load is not the first.
     *
     * @param type the Component class
     * @return {@code true} when this is the first time that class is built during the load
     */
    static boolean claimFirst(Class<?> type) {
        Deque<LoadSession> stack = STACK.get();
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        return stack.peekLast().built.add(type);
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
