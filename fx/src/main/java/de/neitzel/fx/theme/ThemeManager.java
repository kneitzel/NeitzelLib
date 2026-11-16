package de.neitzel.fx.theme;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.scene.*;
import javafx.stage.*;

import java.net.URI;
import java.util.*;
import java.util.function.Consumer;

/** Opt-in, application-owned theme lifetime. All operations use the FX application thread. */
public final class ThemeManager implements AutoCloseable {
    private static final Object MANAGER = new Object(), OWNER_SCENE = new Object();
    private static final String SCOPE = "neitzel-theme";

    public record Failure(String message, Throwable cause) {}

    private final Map<String, Theme> themes = new LinkedHashMap<>();
    private final Map<Scene, Registration> scenes = new LinkedHashMap<>();
    private final Map<Window, Registration> automatic = new IdentityHashMap<>();
    private final ReadOnlyObjectWrapper<Theme> selected =
            new ReadOnlyObjectWrapper<>(Theme.light());
    private final ReadOnlyObjectWrapper<Failure> failure = new ReadOnlyObjectWrapper<>();
    private Consumer<Scene> installationCheck = scene -> {};
    private boolean closed, switching;
    private final ListChangeListener<Window> windows =
            change -> {
                while (change.next()) {
                    if (change.wasRemoved())
                        for (var window : change.getRemoved()) {
                            var r = automatic.remove(window);
                            if (r != null) r.close();
                        }
                    if (change.wasAdded())
                        for (var window : change.getAddedSubList()) inherit(window);
                }
            };

    public ThemeManager() {
        requireFx();
        for (var theme : List.of(Theme.light(), Theme.dark(), Theme.highContrast()))
            themes.put(theme.id(), theme);
        Window.getWindows().addListener(windows);
    }

    public ReadOnlyObjectProperty<Theme> selectedThemeProperty() {
        return selected.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<Failure> failureProperty() {
        return failure.getReadOnlyProperty();
    }

    public List<Theme> themes() {
        return List.copyOf(themes.values());
    }

    public void addTheme(Theme theme) {
        check();
        if (themes.putIfAbsent(theme.id(), theme) != null)
            throw new IllegalArgumentException("Duplicate theme ID: " + theme.id());
    }

    /**
     * Optional application CSS qualification; throwing rolls back a detectable installation
     * failure.
     */
    public void setInstallationCheck(Consumer<Scene> check) {
        check();
        installationCheck = Objects.requireNonNull(check);
    }

    public Registration register(Scene scene) {
        check();
        Objects.requireNonNull(scene);
        if (scenes.containsKey(scene))
            throw new IllegalArgumentException("Scene already registered");
        if (scene.getProperties().containsKey(MANAGER))
            throw new IllegalArgumentException("Scene belongs to another theme manager");
        preflight(selected.get());
        var registration = new Registration(scene);
        var original = List.copyOf(scene.getStylesheets());
        try {
            registration.install(selected.get());
            installationCheck.accept(scene);
            scenes.put(scene, registration);
            scene.getProperties().put(MANAGER, this);
            scene.rootProperty().addListener(registration.rootChanged);
            for (var window : List.copyOf(Window.getWindows())) inherit(window);
            return registration;
        } catch (RuntimeException error) {
            registration.unmark(scene.getRoot());
            if (!registration.ownerBound) scene.getStylesheets().setAll(original);
            failure.set(new Failure("Could not theme scene: " + error.getMessage(), error));
            throw error;
        }
    }

    public boolean select(String id) {
        check();
        if (switching) throw new IllegalStateException("Theme switch already in progress");
        var theme = themes.get(id);
        if (theme == null) {
            failure.set(
                    new Failure("Theme unavailable: " + id + ". Choose a registered theme.", null));
            return false;
        }
        Map<Registration, List<String>> before = new LinkedHashMap<>();
        Map<Registration, List<String>> owned = new LinkedHashMap<>();
        switching = true;
        try {
            preflight(theme);
            for (var r : scenes.values()) {
                if (!r.ownerBound) {
                    before.put(r, List.copyOf(r.scene.getStylesheets()));
                    owned.put(r, r.managed);
                }
            }
            for (var r : List.copyOf(scenes.values())) {
                r.install(theme);
                installationCheck.accept(r.scene);
            }
            selected.set(theme);
            failure.set(null);
            return true;
        } catch (RuntimeException error) {
            for (var entry : before.entrySet()) {
                try {
                    entry.getKey().scene.getStylesheets().setAll(entry.getValue());
                    entry.getKey().managed = owned.get(entry.getKey());
                    entry.getKey().scene.getRoot().applyCss();
                } catch (RuntimeException rollback) {
                    error.addSuppressed(rollback);
                }
            }
            failure.set(
                    new Failure(
                            "Could not install theme "
                                    + id
                                    + "; retained "
                                    + selected.get().id()
                                    + ": "
                                    + error.getMessage(),
                            error));
            return false;
        } finally {
            switching = false;
        }
    }

    /** Startup fallback is explicit, and retains an explanation even after Light installs. */
    public boolean selectSaved(String id) {
        if (select(id)) return true;
        var problem = failure.get();
        if (select("light"))
            failure.set(
                    new Failure(
                            "Saved theme unavailable; using light. " + problem.message(),
                            problem.cause()));
        return false;
    }

    /** Link a library-owned floating window without imposing JavaFX window ownership semantics. */
    public static void link(Window child, Scene owner) {
        requireFx();
        child.getProperties().put(OWNER_SCENE, Objects.requireNonNull(owner));
    }

    /** Copy application/default CSS to another surface without freezing a selected theme there. */
    public static List<String> applicationStyles(Scene scene) {
        var manager = (ThemeManager) scene.getProperties().get(MANAGER);
        if (manager == null) return List.copyOf(scene.getStylesheets());
        var reserved = manager.reserved();
        return scene.getStylesheets().stream().filter(s -> !reserved.contains(s)).toList();
    }

    private Set<String> reserved() {
        Set<String> urls = new HashSet<>();
        themes.values().forEach(t -> urls.addAll(t.stylesheets()));
        return urls;
    }

    private void inherit(Window window) {
        if (closed
                || automatic.containsKey(window)
                || window.getScene() == null
                || scenes.containsKey(window.getScene())) return;
        Scene owner = (Scene) window.getProperties().get(OWNER_SCENE);
        if (owner == null) {
            Window parent =
                    window instanceof PopupWindow p
                            ? p.getOwnerWindow()
                            : window instanceof Stage s ? s.getOwner() : null;
            if (parent != null) owner = parent.getScene();
        }
        if (owner != null && owner.getProperties().get(MANAGER) == this) {
            try {
                automatic.put(window, register(window.getScene()));
            } catch (RuntimeException error) {
                failure.set(
                        new Failure("Could not theme new window: " + error.getMessage(), error));
            }
        }
    }

    private static void preflight(Theme theme) {
        for (String url : theme.stylesheets())
            try {
                var connection = URI.create(url).toURL().openConnection();
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                try (var stream = connection.getInputStream()) {
                    stream.read();
                }
            } catch (Exception error) {
                throw new IllegalArgumentException(
                        "Missing or unreadable theme stylesheet: " + url, error);
            }
    }

    public final class Registration implements AutoCloseable {
        private final Scene scene;
        private final boolean ownerBound;
        private List<String> managed = List.of();
        private Parent marked;
        private boolean addedClass, disposed;
        private final ChangeListener<Parent> rootChanged =
                (o, old, next) -> {
                    unmark(old);
                    mark(next);
                    next.applyCss();
                };

        private Registration(Scene scene) {
            this.scene = scene;
            // Standard JavaFX Dialogs bind this list to their owner. Never edit the bound target.
            this.ownerBound =
                    scene.getRoot() instanceof javafx.scene.control.DialogPane
                            && scene.getWindow() instanceof Stage window
                            && window.getOwner() instanceof Stage owner
                            && owner.getScene() != null
                            && scene.getStylesheets().equals(owner.getScene().getStylesheets());
            if (ownerBound
                    && ((Stage) ((Stage) scene.getWindow()).getOwner())
                                    .getScene()
                                    .getProperties()
                                    .get(MANAGER)
                            != ThemeManager.this)
                throw new IllegalArgumentException(
                        "Register a dialog's owner scene before the dialog");
        }

        private void mark(Parent root) {
            marked = root;
            addedClass = !root.getStyleClass().contains(SCOPE);
            if (addedClass) root.getStyleClass().add(SCOPE);
        }

        private void unmark(Parent root) {
            if (root == marked && addedClass) root.getStyleClass().remove(SCOPE);
        }

        private void install(Theme theme) {
            if (marked == null) mark(scene.getRoot());
            if (ownerBound) {
                scene.getRoot().applyCss();
                return;
            }
            var next = new ArrayList<>(scene.getStylesheets());
            // Reserved bundle URLs copied from an owner are theme-owned, never application
            // overrides.
            next.removeIf(reserved()::contains);
            int index = 0;
            while (index < next.size()
                    && (next.get(index).endsWith("/docking.css")
                            || next.get(index).endsWith("/filters.css"))) index++;
            next.addAll(index, theme.stylesheets());
            scene.getStylesheets().setAll(next);
            managed = theme.stylesheets();
            scene.getRoot().applyCss();
        }

        public void close() {
            requireFx();
            if (disposed) return;
            disposed = true;
            scene.rootProperty().removeListener(rootChanged);
            if (!ownerBound) scene.getStylesheets().removeAll(managed);
            unmark(scene.getRoot());
            scenes.remove(scene);
            if (scene.getProperties().get(MANAGER) == ThemeManager.this)
                scene.getProperties().remove(MANAGER);
        }
    }

    private static void requireFx() {
        if (!Platform.isFxApplicationThread())
            throw new IllegalStateException("Use the FX application thread");
    }

    private void check() {
        requireFx();
        if (closed) throw new IllegalStateException("Theme manager closed");
    }

    public void close() {
        requireFx();
        if (closed) return;
        closed = true;
        Window.getWindows().removeListener(windows);
        for (var r : List.copyOf(scenes.values())) r.close();
        automatic.clear();
    }
}
