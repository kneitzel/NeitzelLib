package de.neitzel.fx.view;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.util.Map;
import java.util.Objects;

/**
 * A freshly bound row with explicit direct-child field mapping and application-owned cleanup.
 * Unmapped children are trailing action controls. A factory must return fresh nodes for every call.
 */
public record ResultRow(HBox root, Map<String, Region> fields, Runnable dispose) {
    public ResultRow {
        Objects.requireNonNull(root);
        fields = Map.copyOf(fields);
        Objects.requireNonNull(dispose);
    }
}
