package de.neitzel.fx.view;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Owns the ViewModel identity and loaded Views for a window, document or archive session.
 * Use this context on the JavaFX application thread. Closing it closes its remaining Views and
 * releases its Model registrations. Close an individual {@link LoadedView} earlier to detach it
 * while its Model remains available to other Views in this context.
 */
public final class ViewModelContext implements AutoCloseable {

    private final Map<Object, Object> viewModels = new IdentityHashMap<>();
    private final ViewLifetime views = new ViewLifetime(this);
    private boolean closed;

    ViewLifetime openView() {
        checkOpen();
        return views.child();
    }

    Map<Object, Object> viewModels() {
        checkOpen();
        return viewModels;
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("ViewModelContext is closed");
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            views.close();
        } finally {
            viewModels.clear();
        }
    }
}
