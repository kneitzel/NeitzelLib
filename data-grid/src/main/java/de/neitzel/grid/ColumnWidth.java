package de.neitzel.grid;

/** Saved sizing intention, independent of a theme's current default. */
public sealed interface ColumnWidth {
    record Default() implements ColumnWidth {}

    record Explicit(double value) implements ColumnWidth {
        public Explicit {
            if (!Double.isFinite(value) || value <= 0)
                throw new IllegalArgumentException("Width must be finite and positive");
        }
    }
}
