package de.neitzel.fx.view;

/** Immutable result-column definition. fixedPosition is zero-based, or -1 for movable. */
public record ResultColumn(
        String id,
        String header,
        double defaultWidth,
        double minimumWidth,
        double maximumWidth,
        int fixedPosition) {
    public ResultColumn {
        if (id == null || id.isBlank() || header == null)
            throw new IllegalArgumentException("Invalid column identity/header");
        if (!Double.isFinite(minimumWidth)
                || !Double.isFinite(maximumWidth)
                || minimumWidth <= 0
                || maximumWidth < minimumWidth
                || !Double.isFinite(defaultWidth)
                || defaultWidth < minimumWidth
                || defaultWidth > maximumWidth
                || fixedPosition < -1)
            throw new IllegalArgumentException("Invalid column width/position");
    }
}
