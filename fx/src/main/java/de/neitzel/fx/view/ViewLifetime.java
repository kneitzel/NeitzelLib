package de.neitzel.fx.view;

import java.util.LinkedHashSet;
import java.util.SequencedSet;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

/** Tracks the resources installed by one View or one of its dynamic children. */
final class ViewLifetime implements AutoCloseable {

    private final ViewModelContext models;
    private final SequencedSet<Runnable> cleanup = new LinkedHashSet<>();
    private boolean closed;

    ViewLifetime(ViewModelContext models) {
        this.models = models;
    }

    ViewModelContext models() {
        return models;
    }

    void own(Runnable removal) {
        if (closed) {
            removal.run();
        } else {
            cleanup.addLast(removal);
        }
    }

    <T> void listen(ObservableValue<T> observable, ChangeListener<? super T> listener) {
        observable.addListener(listener);
        own(() -> observable.removeListener(listener));
    }

    <T> void listen(ObservableList<T> observable, ListChangeListener<? super T> listener) {
        observable.addListener(listener);
        own(() -> observable.removeListener(listener));
    }

    ViewLifetime child() {
        ViewLifetime child = new ViewLifetime(models);
        Runnable closeChild = child::close;
        own(closeChild);
        child.own(() -> cleanup.remove(closeChild));
        return child;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        Throwable failure = null;
        while (!cleanup.isEmpty()) {
            try {
                cleanup.removeLast().run();
            } catch (RuntimeException | Error next) {
                if (failure == null) {
                    failure = next;
                } else if (failure != next) {
                    failure.addSuppressed(next);
                }
            }
        }
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
    }

    /** Cleans up an incomplete load while preserving its original failure. */
    void rollback(Throwable failure) {
        try {
            close();
        } catch (RuntimeException | Error cleanupFailure) {
            if (failure != cleanupFailure) failure.addSuppressed(cleanupFailure);
        }
    }
}
