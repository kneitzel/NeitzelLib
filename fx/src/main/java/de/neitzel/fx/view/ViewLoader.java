package de.neitzel.fx.view;

import de.neitzel.fx.i18n.Localizer;
import de.neitzel.fx.i18n.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

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
     * Loads FXML with application-supplied dependencies and a managed Model context.
     * Load and close the View on the JavaFX application thread. The caller owns the dependencies.
     * @param fxml the FXML document
     * @param model the Model instance
     * @param dependencies the adapter for View objects and services
     * @param models the Model context that owns the View
     * @return the loaded View
     */
    public static LoadedView loadWithDependencies(URL fxml, Object model,
                                                 ViewDependencies dependencies, ViewModelContext models) {
        return load(fxml, model, new ViewInstances(dependencies), models.openView());
    }

    /**
     * Loads a managed Component from its sibling ClassName.fxml on the JavaFX application thread.
     * @param type the registered Component class
     * @param model the initial non-null Model
     * @param dependencies the adapter for View objects and services
     * @param models the Model context that owns the View
     * @return the initialized Component and its effective ViewModel
     */
    public static LoadedView loadComponentWithDependencies(Class<? extends Parent> type, Object model,
                                                          ViewDependencies dependencies, ViewModelContext models) {
        return loadComponent(type, model, new ViewInstances(dependencies), models);
    }

    private static LoadedView loadComponent(Class<? extends Parent> type, Object model,
                                            ViewInstances context, ViewModelContext models) {
        Objects.requireNonNull(model, "Component Model");
        ViewLifetime lifetime = models.openView();
        try {
            Parent root = type.cast(Components.fresh(type, context, lifetime));
            Components.setModel(root, model, lifetime);
            Object viewModel = ViewModels.of(models, model, ViewModels.namedOn(type));
            return new LoadedView(root, viewModel, lifetime);
        } catch (RuntimeException | Error failure) {
            lifetime.rollback(failure);
            if (failure instanceof RuntimeException) {
                throw new IllegalStateException("Could not load Component " + type.getName() + " from "
                        + type.getSimpleName() + ".fxml: " + failure.getMessage(), failure);
            }
            throw failure;
        }
    }

    private static LoadedView load(URL fxml, Object model, ViewInstances context, ViewLifetime lifetime) {
        LoadSession session = LoadSession.nest();
        try {
            Class<?> controllerType = FxmlControllers.controllerClass(fxml);
            Class<?> viewModelClass = ViewModels.namedOn(controllerType);
            Object viewModel = ViewModels.of(lifetime.models(), model, viewModelClass);
            FXMLLoader loader = new FXMLLoader(fxml, resources(context, fxml, controllerType));
            FxmlHandlers.observe(loader, lifetime);
            loader.setControllerFactory(type -> context.controller(type, lifetime));
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
            if (failure instanceof RuntimeException) {
                throw new IllegalStateException("Could not load view " + fxml + ": " + failure.getMessage(), failure);
            }
            throw failure;
        } finally {
            LoadSession.unnest();
        }
    }

    static Messages resources(ViewInstances context, URL fxml, Class<?> type) {
        Localizer languages = context.localizer();
        return languages == null ? null : languages.forView(fxml, type);
    }
}
