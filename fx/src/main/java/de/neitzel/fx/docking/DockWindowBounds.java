package de.neitzel.fx.docking;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** Fits requested floating frames to the visible part of an available screen. */
final class DockWindowBounds {
    private DockWindowBounds() { }

    static Rectangle2D defaults(Stage main) {
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double x = Double.isFinite(main.getX()) ? main.getX() + 40 : screen.getMinX() + 40;
        double y = Double.isFinite(main.getY()) ? main.getY() + 40 : screen.getMinY() + 40;
        return new Rectangle2D(x, y, 600, 400);
    }

    static boolean valid(Rectangle2D bounds) {
        return Double.isFinite(bounds.getMinX()) && Double.isFinite(bounds.getMinY())
                && Double.isFinite(bounds.getWidth()) && Double.isFinite(bounds.getHeight())
                && bounds.getWidth() > 0 && bounds.getHeight() > 0;
    }

    static Rectangle2D fit(Rectangle2D requested) {
        Rectangle2D screen = Screen.getScreensForRectangle(requested).stream()
                .max(java.util.Comparator.comparingDouble(candidate -> overlap(requested, candidate.getVisualBounds())))
                .orElse(Screen.getPrimary()).getVisualBounds();
        double width = Math.min(requested.getWidth(), screen.getWidth());
        double height = Math.min(requested.getHeight(), screen.getHeight());
        double x = Math.clamp(requested.getMinX(), screen.getMinX(), screen.getMaxX() - width);
        double y = Math.clamp(requested.getMinY(), screen.getMinY(), screen.getMaxY() - height);
        return new Rectangle2D(x, y, width, height);
    }

    private static double overlap(Rectangle2D first, Rectangle2D second) {
        return Math.max(0, Math.min(first.getMaxX(), second.getMaxX()) - Math.max(first.getMinX(), second.getMinX()))
                * Math.max(0, Math.min(first.getMaxY(), second.getMaxY()) - Math.max(first.getMinY(), second.getMinY()));
    }
}
