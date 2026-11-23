package de.neitzel.fx.view;

import javafx.scene.Group;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Line;

/** Row decoration that preserves field borders, component identity and hit testing. */
final class ResultColumnSeparators extends Group {
    ResultColumnSeparators(HBox row, int columns) {
        setManaged(false);
        setMouseTransparent(true);
        for (int i = 1; i < columns; i++) {
            var line = new Line();
            line.getStyleClass().add("column-separator-line");
            line.endYProperty().bind(row.heightProperty());
            getChildren().add(line);
        }
    }

    static ResultColumnSeparators forHeader(HBox cell) {
        var decoration = new ResultColumnSeparators(cell, 2);
        var line = (Line) decoration.getChildren().getFirst();
        line.startXProperty().bind(cell.widthProperty().subtract(.5));
        line.endXProperty().bind(line.startXProperty());
        return decoration;
    }

    boolean matchesColumnCount(int columns) {
        return getChildren().size() == Math.max(0, columns - 1);
    }

    void position(double[] widths) {
        double x = 0;
        for (int i = 0; i < getChildren().size(); i++) {
            x += widths[i];
            var line = (Line) getChildren().get(i);
            line.setStartX(x - .5);
            line.setEndX(x - .5);
        }
    }
}
