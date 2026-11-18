package de.neitzel.fx.view;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Makes equal normalized scroll values represent equal pixel offsets in queried results. */
final class ResultScrollAlignment implements AutoCloseable {
    private final HBox header;
    private final double originalMinimum;
    private final DoubleBinding minimum;

    ResultScrollAlignment(HBox header, VBox body, ScrollPane headerScroll, ScrollPane bodyScroll) {
        this.header = header;
        originalMinimum = header.getMinWidth();
        // A vertical body scrollbar narrows its viewport. Trailing row actions can also
        // make its content wider than the column headings. Reserve blank header space
        // so both scrollable extents match; the existing two-way hvalue binding then
        // moves every mapped field and heading by the same number of pixels.
        minimum =
                Bindings.createDoubleBinding(
                        () ->
                                body.getLayoutBounds().getWidth()
                                        + Math.max(
                                                0,
                                                headerScroll.getViewportBounds().getWidth()
                                                        - bodyScroll
                                                                .getViewportBounds()
                                                                .getWidth()),
                        body.layoutBoundsProperty(),
                        headerScroll.viewportBoundsProperty(),
                        bodyScroll.viewportBoundsProperty());
        header.minWidthProperty().bind(minimum);
    }

    @Override
    public void close() {
        header.minWidthProperty().unbind();
        header.setMinWidth(originalMinimum);
        minimum.dispose();
    }
}
