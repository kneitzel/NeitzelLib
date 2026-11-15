package de.neitzel.fx.docking;

import javafx.animation.AnimationTimer;
import javafx.beans.InvalidationListener;
import javafx.geometry.Rectangle2D;
import javafx.stage.Stage;

import java.util.Objects;

/** Commits floating geometry only after native mouse buttons are released. */
final class DockWindowGeometry {
    private final Stage stage;
    private final Runnable onCommitted;
    private final InvalidationListener geometryChanged = observable -> changed();
    private final InvalidationListener visibilityChanged = observable -> watch();
    private final AnimationTimer timer = new AnimationTimer() {
        @Override public void handle(long now) {
            if (captureIfReady()) onCommitted.run();
        }
    };
    private Rectangle2D bounds;
    private boolean dirty;
    private boolean running;
    private boolean disposed;

    DockWindowGeometry(Stage stage, Runnable onCommitted) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.onCommitted = Objects.requireNonNull(onCommitted, "onCommitted");
        bounds = current();
        stage.xProperty().addListener(geometryChanged);
        stage.yProperty().addListener(geometryChanged);
        stage.widthProperty().addListener(geometryChanged);
        stage.heightProperty().addListener(geometryChanged);
        stage.showingProperty().addListener(visibilityChanged);
        stage.focusedProperty().addListener(visibilityChanged);
    }

    Rectangle2D bounds() { return bounds; }

    /** Includes an unfinished gesture that has returned to its original frame size. */
    boolean hasPendingChanges() { return dirty; }

    /**
     * Completes pending geometry for a manager publication without invoking its callback.
     * A gesture returning to the same frame still completes, because child split positions may have changed.
     */
    boolean captureIfReady() {
        if (disposed) return false;
        Rectangle2D current = current();
        if (current == null) return false;
        if (current.equals(bounds) && !dirty) {
            stop();
            return false;
        }
        NativeMouseButtons.State buttons = NativeMouseButtons.state();
        if (buttons != NativeMouseButtons.State.UP) {
            if (buttons == NativeMouseButtons.State.UNKNOWN) stop();
            return false;
        }
        bounds = current;
        dirty = false;
        stop();
        return true;
    }

    private Rectangle2D current() {
        double x = stage.getX();
        double y = stage.getY();
        double width = stage.getWidth();
        double height = stage.getHeight();
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(width) && Double.isFinite(height)
                && width > 0 && height > 0 ? new Rectangle2D(x, y, width, height) : null;
    }

    private void changed() {
        dirty = true;
        watch();
    }

    private void watch() {
        if (!disposed && dirty && stage.isShowing()) {
            if (!running) {
                running = true;
                timer.start();
            }
        } else stop();
    }

    private void stop() {
        running = false;
        timer.stop();
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        stop();
        stage.xProperty().removeListener(geometryChanged);
        stage.yProperty().removeListener(geometryChanged);
        stage.widthProperty().removeListener(geometryChanged);
        stage.heightProperty().removeListener(geometryChanged);
        stage.showingProperty().removeListener(visibilityChanged);
        stage.focusedProperty().removeListener(visibilityChanged);
    }
}
