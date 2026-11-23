package de.neitzel.fx.view;

import de.neitzel.fx.i18n.Localizer;
import de.neitzel.fx.i18n.Messages;
import de.neitzel.injection.ApplicationContext;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;
import java.net.URL;

/**
 * Loads a View for one Model instance.
 */
public final class ViewLoader {

    /**
     * Prevents instantiation.
     */
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
     * A path that is not a read-write Attribute fails the load. The context creates a Controller
     * the document names, and injects that Controller's services. When the document names no
     * Controller, the library supplies one. A Controller that implements {@link HasViewModel}
     * receives the ViewModel after construction and before bindings. {@link ViewModelClass} on that
     * Controller names the ViewModel class. The library constructs it with the Model instance
     * before load. With no annotation, the library builds the generic ViewModel. The first View of a
     * Model instance fixes that class. A later View that names a different class fails the load,
     * and the earlier View stays as it was. A later View that names none reuses the ViewModel.
     * The context creates a
     * Converter when a Direct binding names one. A converter that cannot produce a value
     * keeps the previous value and records one error Message on the ViewModel that owns the last
     * Attribute, with that Attribute's name as the source. A parent carries a copy with the source
     * extended. It does not fail the load.
     * A parent document names a Component class as an element. The context creates that Component
     * and injects its services. A second element of the same class is another injected instance,
     * and the cached singleton stays the first one. The Component document {@code ClassName.fxml},
     * in the same package, is loaded with the Component as {@code fx:root} and as the Controller.
     * That document's bindings are not applied during this load. They run when the Component's
     * model property becomes a non-null Model. {@link ViewModelClass} on the Component names the
     * ViewModel class for that Model. A Component that implements {@link HasViewModel} receives
     * that ViewModel after the property is set and before those bindings run.
     * A {@link Repeat} in the same list names a pane, a Component class, and a collection
     * Attribute. One new Component child is placed in that pane for each Model instance, in list
     * order. Adding or removing an element updates that pane. Those instances are created with
     * {@link de.neitzel.injection.ApplicationContext#createComponent} so the cached singleton of
     * that class stays in place. A collection of Model instances is an
     * {@link javafx.collections.ObservableList} of those instances, shared with the entity.
     * Selection is a separate Attribute of the parent Model. Null means nothing is selected.
     * A Direct binding can tie a control property to that Attribute. The repeated Component does
     * not store the selection.
     * Loading does not open a popup or fill a status bar. A Direct binding whose path is
     * {@code latestMessage} shows the latest Message text. {@link MessagePopup#showNewMessages}
     * shows each newly added Message in a dialog.
     *
     * @param fxml    the FXML document
     * @param model   the Model instance
     * @param context the application context
     * @return the loaded View
     * @throws IllegalArgumentException when a Direct binding names a path that is not a read-write Attribute
     */
    public static LoadedView load(URL fxml, Object model, ApplicationContext context) {
        return load(fxml, model, context, new ViewLifetime(ViewModels.LEGACY));
    }

    /**
     * Loads a View with a fresh Controller and Components in an explicitly owned Model context.
     * Close the result when its UI is removed, or close the Model context to close all its Views.
     *
     * @param fxml the FXML document
     * @param model the Model instance
     * @param context the container for Controllers and services
     * @param models the Model context shared by this View and related Views
     * @return the loaded View
     */
    public static LoadedView load(URL fxml, Object model, ApplicationContext context, ViewModelContext models) {
        return load(fxml, model, context, models.openView());
    }

    private static LoadedView load(URL fxml, Object model, ApplicationContext context, ViewLifetime lifetime) {
        LoadSession session = LoadSession.nest();
        try {
            Class<?> controllerType = FxmlControllers.controllerClass(fxml);
            Class<?> viewModelClass = ViewModels.namedOn(controllerType);
            Object viewModel = ViewModels.of(lifetime.models(), model, viewModelClass);
            FXMLLoader loader = new FXMLLoader(fxml, resources(context, fxml, controllerType));
            FxmlHandlers.observe(loader, lifetime);
            loader.setControllerFactory(type -> createController(context, type, lifetime));
            loader.setBuilderFactory(type -> Components.builder(type, context, lifetime));
            Object loaded = loader.load();
            if (!(loaded instanceof Parent root)) {
                throw new IllegalArgumentException("View root must be a parent");
            }
            Object controller = loader.getController();
            if (controller == null) {
                controller = new SuppliedController();
            }
            if (controller instanceof HasViewModel hasViewModel && viewModel instanceof ViewModel bound) {
                hasViewModel.setViewModel(bound);
            }
            CommandBindingApplier.validate(session.bindingLists(), loader.getNamespace());
            for (BindingList bindingList : session.bindingLists()) {
                bindingList.apply(loader.getNamespace(), viewModel, controller, root, context, lifetime);
            }
            Table.openAll(root, viewModel, context, lifetime);
            session.checkFailure();
            return new LoadedView(root, viewModel, lifetime);
        } catch (IOException failure) {
            lifetime.rollback(failure);
            throw new IllegalStateException("Could not load view " + fxml, failure);
        } catch (RuntimeException | Error failure) {
            lifetime.rollback(failure);
            throw failure;
        } finally {
            LoadSession.unnest();
        }
    }

    static Object createController(ApplicationContext context, Class<?> type, ViewLifetime lifetime) {
        if (!lifetime.managed()) return context.getComponent(type);
        Object controller = context.createComponent(type);
        lifetime.ownController(controller);
        return controller;
    }

    static Messages resources(ApplicationContext context, URL fxml, Class<?> type) {
        Localizer languages;
        try {
            languages = context.getComponent(Localizer.class);
        } catch (IllegalArgumentException unregistered) {
            return null;
        }
        return languages.forView(fxml, type);
    }

}
