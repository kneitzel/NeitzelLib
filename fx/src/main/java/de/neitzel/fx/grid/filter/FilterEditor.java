package de.neitzel.fx.grid.filter;

import de.neitzel.grid.FilterCondition;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.scene.Parent;

/** Fresh per session; controllers own drafts only. Null draft explicitly clears the filter. */
public interface FilterEditor extends AutoCloseable {
    Parent view();

    ReadOnlyStringProperty validationMessageProperty();

    /** Throws an actionable IllegalArgumentException for invalid input; never mutates a query. */
    FilterCondition draft();

    default boolean nestedPopupShowing() {
        return false;
    }

    default void closeNestedPopup() {}

    @Override
    void close();
}
