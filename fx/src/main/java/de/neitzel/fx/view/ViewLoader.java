package de.neitzel.fx.view;

import de.neitzel.injection.ApplicationContext;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;
import java.net.URL;

/**
 * Loads a View for one Model instance.
 */
public final class ViewLoader {

    private ViewLoader() {
    }

    /**
     * Loads a View for one Model instance.
     * <p>
     * Builds a ViewModel for {@code model}, loads {@code fxml}, and applies Direct bindings
     * declared in {@code fx:define}. Direction {@code both} keeps the control and the Attribute
     * aligned, so editing the control writes the Attribute immediately. Direction {@code view}
     * shows the Attribute and follows later property changes without writing the Attribute back.
     * Direction {@code model} shows the current value, writes the Attribute when the control
     * changes, and does not push later property changes to the control. A collection of simple
     * values is one shared list: the Direct binding sets the control property to that
     * {@link javafx.collections.ObservableList}, so an add or remove is visible on both sides.
     * A path that is not a read-write Attribute fails the load. The context is used when the
     * document names a Controller.
     *
     * @param fxml    the FXML document
     * @param model   the Model instance
     * @param context the application context
     * @return the loaded View
     * @throws IllegalArgumentException when a Direct binding names a path that is not a read-write Attribute
     */
    public static LoadedView load(URL fxml, Object model, ApplicationContext context) {
        ViewModel viewModel = new ViewModel(model);
        LoadSession session = LoadSession.open();
        try {
            FXMLLoader loader = new FXMLLoader(fxml);
            loader.setControllerFactory(type -> controller(context, type));
            Object loaded = loader.load();
            if (!(loaded instanceof Parent root)) {
                throw new IllegalArgumentException("View root must be a parent");
            }
            for (BindingList bindingList : session.bindingLists()) {
                bindingList.apply(loader.getNamespace(), viewModel);
            }
            return new LoadedView(root, viewModel);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not load view " + fxml, failure);
        } finally {
            LoadSession.close();
        }
    }

    private static Object controller(ApplicationContext context, Class<?> type) {
        return context.getComponent(type.asSubclass(Object.class));
    }
}
