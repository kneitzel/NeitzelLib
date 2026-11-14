package de.neitzel.fx.view;

import javafx.beans.property.Property;
import javafx.scene.Parent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The scene-graph root and ViewModel produced by loading a View for a Model instance.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class LoadedView {

    /**
     * The scene-graph root of the loaded View.
     */
    @Getter
    private final Parent root;

    /**
     * The ViewModel the View is bound to, or a replacement that exposes JavaFX properties by name.
     */
    private final Object viewModel;

    /**
     * Returns the ViewModel the View is bound to.
     * <p>
     * A generic ViewModel, and a subclass of it, are returned as that ViewModel. A replacement
     * that does not extend {@link ViewModel} is addressed through {@link #property(String)}.
     *
     * @return the ViewModel
     * @throws IllegalStateException when the View is bound to a replacement
     */
    public ViewModel getViewModel() {
        if (viewModel instanceof ViewModel bound) {
            return bound;
        }
        throw new IllegalStateException("View is bound to " + viewModel.getClass().getName());
    }

    /**
     * Returns the JavaFX property for an Attribute name.
     * <p>
     * The property's class follows the Attribute's declared type. The name {@code latestMessage}
     * is a string property holding the text of the latest Message.
     *
     * @param name the Attribute name, {@code latestMessage}, or a JavaFX property declared on
     *             the ViewModel
     * @return the property, or {@code null} when that name is not a read-write Attribute,
     *         not {@code latestMessage}, and not a JavaFX property declared on the ViewModel
     */
    public Property<?> property(String name) {
        if (viewModel instanceof ViewModel bound) {
            return bound.property(name);
        }
        return NamedProperties.get(viewModel, name);
    }
}
