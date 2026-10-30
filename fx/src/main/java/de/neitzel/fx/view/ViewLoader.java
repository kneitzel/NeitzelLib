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
     * Reuses the ViewModel already built for {@code model}, or builds one, then loads {@code fxml}
     * and applies binding elements declared in {@code fx:define}. A Computed binding writes a
     * Controller method result to a control and does not write the Model. Direction {@code both}
     * keeps the control and the Attribute
     * aligned, so editing the control writes the Attribute immediately. Direction {@code view}
     * shows the Attribute and follows later property changes without writing the Attribute back.
     * Direction {@code model} shows the current value, writes the Attribute when the control
     * changes, and does not push later property changes to the control. A Path of one name is an
     * Attribute of this View's ViewModel. Further names step into a nested ViewModel, and the
     * direction applies to the last name. When a Complex attribute along the Path is null, the
     * control and the Model keep their current values. A collection of simple values is one shared
     * list: the Direct binding sets the control property to that
     * {@link javafx.collections.ObservableList}, so an add or remove is visible on both sides.
     * A path that is not a read-write Attribute fails the load. The context creates a Controller,
     * and a Converter when a Direct binding names one. A converter that cannot produce a value
     * keeps the previous value and records one error Message. It does not fail the load.
     * A parent document names a Component class as an element. The context creates that Component
     * and injects its services. The Component document {@code ClassName.fxml}, in the same package,
     * is loaded with the Component as {@code fx:root} and as the Controller. That document's
     * bindings are not applied during this load. They run when the Component's model property
     * becomes a non-null Model. A Component that implements {@link HasViewModel} receives that
     * ViewModel after the property is set and before those bindings run.
     *
     * @param fxml    the FXML document
     * @param model   the Model instance
     * @param context the application context
     * @return the loaded View
     * @throws IllegalArgumentException when a Direct binding names a path that is not a read-write Attribute
     */
    public static LoadedView load(URL fxml, Object model, ApplicationContext context) {
        ViewModel viewModel = ViewModels.of(model);
        LoadSession session = LoadSession.open();
        try {
            FXMLLoader loader = new FXMLLoader(fxml);
            loader.setControllerFactory(type -> controller(context, type));
            loader.setBuilderFactory(type -> Components.builder(type, context));
            Object loaded = loader.load();
            if (!(loaded instanceof Parent root)) {
                throw new IllegalArgumentException("View root must be a parent");
            }
            for (BindingList bindingList : session.bindingLists()) {
                bindingList.apply(loader.getNamespace(), viewModel, loader.getController(), root, context);
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
