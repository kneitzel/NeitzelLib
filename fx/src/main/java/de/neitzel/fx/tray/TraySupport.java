package de.neitzel.fx.tray;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.event.EventHandler;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.awt.EventQueue;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** Close-to-tray lifecycle for one primary JavaFX window, with an accessible fallback. */
public final class TraySupport implements AutoCloseable {
    /** Installed native icon. Removal must be safe from any thread. */
    @FunctionalInterface public interface Registration extends AutoCloseable {
        @Override void close();
    }

    /** Native integration port. Return null when unsupported; callbacks may run on any thread. */
    @FunctionalInterface public interface Backend {
        CompletableFuture<Registration> install(Image icon, String label, Runnable open, Runnable quit);
    }

    private final Stage stage;
    private final BooleanSupplier mayQuit;
    private final Runnable quit;
    private final boolean previousImplicitExit;
    private final ReadOnlyBooleanWrapper available = new ReadOnlyBooleanWrapper(false);
    private final EventHandler<WindowEvent> closeHandler;
    private volatile boolean closed;
    private boolean quitting;
    private Registration registration;
    private final Runnable removeNativeQuit;
    private final Object lifecycleLock = new Object();

    /** Attaches asynchronously to the platform tray. Call on the JavaFX application thread. */
    public static TraySupport attach(Stage stage, Image icon, String label, BooleanSupplier mayQuit, Runnable quit) {
        return attach(stage, icon, label, mayQuit, quit, TraySupport::installAwt);
    }

    /** Attaches using an alternate native backend; useful where AWT tray integration is unavailable. */
    public static TraySupport attach(Stage stage, Image icon, String label, BooleanSupplier mayQuit, Runnable quit, Backend backend) {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Attach tray support on the JavaFX thread");
        return new TraySupport(stage, icon, label, mayQuit, quit, backend);
    }

    private TraySupport(Stage stage, Image icon, String label, BooleanSupplier mayQuit, Runnable quit, Backend backend) {
        this.stage = Objects.requireNonNull(stage);
        this.mayQuit = Objects.requireNonNull(mayQuit);
        this.quit = Objects.requireNonNull(quit);
        previousImplicitExit = Platform.isImplicitExit();
        removeNativeQuit = MacApplicationQuit.install(() -> onFx(this::requestQuit));
        closeHandler = event -> {
            event.consume();
            if (available.get()) stage.hide(); else open();
        };
        stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closeHandler);
        CompletableFuture<Registration> installing;
        try { installing = backend.install(Objects.requireNonNull(icon), Objects.requireNonNull(label), () -> onFx(this::open), () -> onFx(this::requestQuit)); }
        catch (RuntimeException unsupported) { installing = CompletableFuture.completedFuture(null); }
        installing.whenComplete((installed, failure) -> {
            synchronized (lifecycleLock) {
                if (closed) { if (installed != null) installed.close(); return; }
                registration = installed;
            }
            onFx(() -> {
                if (closed) return;
                available.set(installed != null && failure == null);
                if (available.get()) Platform.setImplicitExit(false);
            });
        });
    }

    /** Whether a native icon has actually been installed; false while initialization is pending. */
    public boolean isAvailable() { return available.get(); }
    public ReadOnlyBooleanProperty availableProperty() { return available.getReadOnlyProperty(); }

    /** Restores and focuses the window. Call on the JavaFX thread. */
    public void open() {
        if (closed) return;
        stage.show(); stage.setIconified(false); stage.toFront(); stage.requestFocus();
    }

    /** Requests an explicit quit. A false callback leaves the window and icon available. */
    public void requestQuit() {
        if (closed || quitting) return;
        quitting = true;
        open();
        boolean accepted = false;
        try {
            if (mayQuit.getAsBoolean()) { accepted = true; close(); quit.run(); }
        } finally { if (!accepted) quitting = false; }
    }

    /** Removes integration and restores the prior implicit-exit setting. Call on the FX thread. */
    @Override public void close() {
        synchronized (lifecycleLock) {
            if (closed) return;
            closed = true;
            if (registration != null) registration.close();
        }
        removeNativeQuit.run();
        stage.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closeHandler);
        available.set(false);
        Platform.setImplicitExit(previousImplicitExit);
    }

    private static void onFx(Runnable task) {
        if (Platform.isFxApplicationThread()) task.run(); else Platform.runLater(task);
    }

    private static CompletableFuture<Registration> installAwt(Image image, String label, Runnable open, Runnable quit) {
        CompletableFuture<Registration> result = new CompletableFuture<>();
        EventQueue.invokeLater(() -> {
            try {
                if (!SystemTray.isSupported()) { result.complete(null); return; }
                PopupMenu menu = new PopupMenu();
                MenuItem restore = new MenuItem("Open"), exit = new MenuItem("Quit");
                restore.addActionListener(event -> open.run()); exit.addActionListener(event -> quit.run());
                menu.add(restore); menu.addSeparator(); menu.add(exit);
                TrayIcon icon = new TrayIcon(image, label, menu); icon.setImageAutoSize(true);
                icon.addActionListener(event -> open.run());
                SystemTray tray = SystemTray.getSystemTray(); tray.add(icon);
                result.complete(() -> EventQueue.invokeLater(() -> tray.remove(icon)));
            } catch (Exception unsupported) { result.completeExceptionally(unsupported); }
        });
        return result;
    }
}
