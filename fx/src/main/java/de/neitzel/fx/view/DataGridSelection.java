package de.neitzel.fx.view;

import java.util.Collection;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

/**
 * An observable selection for a ViewModel to read, with explicit methods for selection changes.
 * Create it through {@link DataGrid#observeSelection()} and use it on the JavaFX application thread.
 * Closing the adapter detaches it without changing the grid's selection or closing the grid.
 *
 * @param <K> row key type
 * @param <T> row model type
 */
public final class DataGridSelection<K, T> implements AutoCloseable {
    private final ReadOnlyObjectWrapper<GridSelection<K, T>> selection = new ReadOnlyObjectWrapper<>();
    private DataGrid<K, T> grid;

    DataGridSelection(DataGrid<K, T> grid, ReadOnlyObjectProperty<GridSelection<K, T>> source) {
        this.grid = grid;
        selection.bind(source);
    }

    /** Returns one read-only observable containing the selected keys and available elements. */
    public ReadOnlyObjectProperty<GridSelection<K, T>> selectionProperty() {
        return selection.getReadOnlyProperty();
    }

    /** Returns the current snapshot, or the last snapshot when the adapter is closed. */
    public GridSelection<K, T> getSelection() {
        return selection.get();
    }

    /** Replaces the selection with one key, or clears it for a null key. */
    public void select(K key) {
        requireOpen().select(key);
    }

    /** Restores the selection by key, respecting the grid's current selection mode. */
    public void selectKeys(Collection<? extends K> keys) {
        requireOpen().selectKeys(keys);
    }

    private DataGrid<K, T> requireOpen() {
        if (grid == null) throw new IllegalStateException("Selection adapter is closed");
        return grid;
    }

    /** Detaches the adapter and preserves its last published snapshot. */
    @Override
    public void close() {
        if (!Platform.isFxApplicationThread())
            throw new IllegalStateException("Use the JavaFX application thread");
        selection.unbind();
        grid = null;
    }
}
