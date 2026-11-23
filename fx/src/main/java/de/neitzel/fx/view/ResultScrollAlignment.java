package de.neitzel.fx.view;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Makes equal normalized scroll values represent equal pixel offsets in queried results. */
final class ResultScrollAlignment implements AutoCloseable {
    private final HBox header;
    private final VBox body;
    private final double originalMinimum;
    private final Insets originalPadding;
    private final ObjectBinding<Insets> bodyPadding;
    private final DoubleBinding minimum;
    private final DoubleBinding viewportDifference;

    ResultScrollAlignment(
            HBox header, VBox body, ScrollPane headerScroll, ScrollPane bodyScroll,
            boolean hasHeaderAccessory) {
        this.header = header;
        this.body = body;
        originalMinimum = header.getMinWidth();
        originalPadding = body.getPadding();
        viewportDifference = Bindings.createDoubleBinding(
                () -> headerScroll.getViewportBounds().getWidth()
                        - bodyScroll.getViewportBounds().getWidth(),
                headerScroll.viewportBoundsProperty(), bodyScroll.viewportBoundsProperty());
        // The fixed header menu can make the header viewport narrower. Add trailing body space
        // so the last column can scroll fully into view in both viewports.
        bodyPadding = hasHeaderAccessory ? Bindings.createObjectBinding(
                () -> new Insets(originalPadding.getTop(),
                        originalPadding.getRight() + Math.max(0, -viewportDifference.get()),
                        originalPadding.getBottom(), originalPadding.getLeft()),
                viewportDifference) : null;
        if (bodyPadding != null) body.paddingProperty().bind(bodyPadding);
        // A vertical body scrollbar narrows its viewport. Trailing row actions can also
        // make its content wider than the column headings. Reserve blank header space
        // so both scrollable extents match; the existing two-way hvalue binding then
        // moves every mapped field and heading by the same number of pixels.
        minimum =
                Bindings.createDoubleBinding(
                        () -> body.getLayoutBounds().getWidth()
                                + (hasHeaderAccessory
                                        ? viewportDifference.get()
                                        : Math.max(0, viewportDifference.get())),
                        body.layoutBoundsProperty(), viewportDifference);
        header.minWidthProperty().bind(minimum);
    }

    @Override
    public void close() {
        header.minWidthProperty().unbind();
        header.setMinWidth(originalMinimum);
        minimum.dispose();
        if (bodyPadding != null) {
            body.paddingProperty().unbind();
            body.setPadding(originalPadding);
            bodyPadding.dispose();
        }
        viewportDifference.dispose();
    }
}
