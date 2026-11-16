package de.neitzel.fx.example.docking.workspace;

import de.neitzel.fx.docking.*;
import javafx.geometry.Orientation;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Side;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the example's actual file boundary without JavaFX Nodes or a running toolkit. */
class WorkspaceStoreTest {
    @TempDir Path directory;

    @Test
    void fileRoundTripKeepsDocumentTextAndTheCompleteTypedLayoutGraph() throws Exception {
        WorkspaceStore store = new WorkspaceStore(directory.resolve("state/workspace.json"));
        assertTrue(store.read().isEmpty());
        StoredWorkspace workspace = workspace();
        store.save(workspace);
        assertEquals(workspace, new WorkspaceStore(store.getFile()).read().orElseThrow());
        String json = Files.readString(store.getFile());
        assertTrue(json.contains("Unsaved text"));
        assertFalse(json.contains("javafx.scene"), "Persistence stores application data, not live controls");
    }

    @Test
    void failedReplacementPreservesExistingDataCleansPreparationAndAllowsRetry() throws Exception {
        Path state = Files.createDirectory(directory.resolve("state"));
        Path destination = Files.createDirectory(state.resolve("workspace.json"));
        Path existing = destination.resolve("existing-document.txt");
        Files.writeString(existing, "Existing application data");
        WorkspaceStore store = new WorkspaceStore(destination);

        assertThrows(IOException.class, () -> store.save(workspace()));
        assertEquals("Existing application data", Files.readString(existing));
        try (var files = Files.list(state)) {
            assertEquals(List.of(destination), files.toList(), "A failed replacement must remove its prepared file");
        }

        Files.delete(existing);
        Files.delete(destination);
        store.save(workspace());
        assertEquals(workspace(), new WorkspaceStore(destination).read().orElseThrow());
    }

    @Test
    void invalidInputAndUnsupportedSaveNeverRewriteExistingFile() throws Exception {
        WorkspaceStore store = new WorkspaceStore(directory.resolve("workspace.json"));
        StoredWorkspace original = workspace();
        store.save(original);
        byte[] savedBytes = Files.readAllBytes(store.getFile());
        StoredWorkspace future = new StoredWorkspace(999, original.layout(), original.documents());
        assertThrows(IOException.class, () -> store.save(future));
        assertArrayEquals(savedBytes, Files.readAllBytes(store.getFile()));
        assertEquals(original, store.read().orElseThrow());

        String unsupported = Files.readString(store.getFile()).replaceFirst("\"version\"\\s*:\\s*1", "\"version\": 999");
        for (String invalid : List.of("{", unsupported)) {
            Files.writeString(store.getFile(), invalid);
            assertThrows(IOException.class, store::read);
            assertEquals(invalid, Files.readString(store.getFile()), "Reading invalid data must preserve it for recovery");
        }
    }

    private static StoredWorkspace workspace() {
        var notes = new DockableId("notes");
        var before = new DockableId("before");
        var after = new DockableId("after");
        var appended = new DockableId("appended");
        var settings = new LayoutSnapshot.GroupSettings(Side.TOP, DockGroupMode.DOCKED,
                240, 180, 80, 80, true, true, false, true, SplitDirections.ALL);
        var editor = group("primary", settings, List.of(notes), notes);
        var secondary = group("secondary", settings, List.of(), null);
        var split = new LayoutSnapshot.Split(List.of(new DockAreaId("editors")), Orientation.HORIZONTAL,
                .37, editor, secondary);
        var overlaySettings = new LayoutSnapshot.GroupSettings(Side.LEFT, DockGroupMode.OVERLAY,
                210, 160, 80, 80, true, false, true, true, SplitDirections.VERTICAL);
        var main = new LayoutSnapshot.Window(new DockWindowId("main"), true, null, List.of(
                new LayoutSnapshot.Root(LayoutSnapshot.Placement.CENTER, -1, -1, split),
                new LayoutSnapshot.Root(LayoutSnapshot.Placement.LEFT, 210, -1, group("project", overlaySettings, List.of(), null))));
        var floating = new LayoutSnapshot.Window(new DockWindowId("floating-7"), false,
                new Rectangle2D(-900, 123, 640, 420), List.of(new LayoutSnapshot.Root(
                LayoutSnapshot.Placement.CENTER, -1, -1, group("floating-area", settings, List.of(before, after, appended), before))));
        var layout = new LayoutSnapshot(LayoutSnapshot.CURRENT_VERSION, List.of(main, floating), before,
                Map.of(notes, new LayoutSnapshot.ReturnPosition(DockTarget.area("editors"), new DockAreaId("editors")),
                        before, new LayoutSnapshot.ReturnPosition(DockTarget.group(editor.id(), TabPosition.before(notes)), new DockAreaId("editors")),
                        after, new LayoutSnapshot.ReturnPosition(DockTarget.group(editor.id(), TabPosition.after(notes)), new DockAreaId("editors")),
                        appended, new LayoutSnapshot.ReturnPosition(DockTarget.group(editor.id()), new DockAreaId("editors"))),
                Map.of(notes, new DockAreaId("primary"), before, new DockAreaId("secondary")));
        return new StoredWorkspace(StoredWorkspace.CURRENT_VERSION, layout, Map.of(
                notes, new StoredWorkspace.Document("Notes", "Unsaved text\nÄnderung: \"quoted\" and λ"),
                before, new StoredWorkspace.Document("Before", "Before document"),
                after, new StoredWorkspace.Document("After", "After document"),
                appended, new StoredWorkspace.Document("Appended", "Appended document")));
    }

    private static LayoutSnapshot.Group group(String id, LayoutSnapshot.GroupSettings settings,
                                               List<DockableId> tabs, DockableId selected) {
        return new LayoutSnapshot.Group(List.of(new DockAreaId(id)), new DockGroupId(id), new DockAreaId(id),
                settings, settings.mode() == DockGroupMode.OVERLAY, 317.5, 248.25, tabs, selected);
    }
}
