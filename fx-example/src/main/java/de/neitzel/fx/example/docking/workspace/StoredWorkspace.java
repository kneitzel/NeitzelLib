package de.neitzel.fx.example.docking.workspace;

import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.LayoutSnapshot;

import java.util.Map;
import java.util.Objects;

/** Application data: document contents are stored separately from the library's layout snapshot. */
public record StoredWorkspace(int version, LayoutSnapshot layout, Map<DockableId, Document> documents) {
    public static final int CURRENT_VERSION = 1;

    public StoredWorkspace {
        Objects.requireNonNull(layout, "layout");
        documents = Map.copyOf(documents);
    }

    /** Text documents have no dependency on their plain-JavaFX or optional MVVM presentation. */
    public record Document(String title, String text) {
        public Document {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(text, "text");
        }
    }
}
