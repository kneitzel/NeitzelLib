package de.neitzel.fx.control;

import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

/** A button graphic that follows the control's text color and exposes a tooltip and accessible name. */
public final class ActionIcon {
    private ActionIcon() {}

    /** Installs an outlined icon drawn on a 16 by 16 coordinate grid. */
    public static void install(ButtonBase button, String description, String path) {
        var icon = new SVGPath();
        icon.setContent(path);
        icon.setFill(null);
        icon.strokeProperty().bind(button.textFillProperty());
        icon.setStrokeWidth(1.5);
        icon.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        icon.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        var graphic = new StackPane(icon);
        graphic.setMinSize(18, 18);
        graphic.setPrefSize(18, 18);
        graphic.setMaxSize(18, 18);
        graphic.setMouseTransparent(true);
        button.setGraphic(graphic);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setAccessibleText(description);
        button.setTooltip(new Tooltip(description));
    }
}
