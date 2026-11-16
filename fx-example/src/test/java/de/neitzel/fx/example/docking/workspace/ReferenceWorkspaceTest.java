package de.neitzel.fx.example.docking.workspace;

import de.neitzel.fx.docking.DockPane;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ReferenceWorkspaceTest {
    @TempDir Path directory;

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void javaAndFxmlWorkspacesRetainEditorAndLocalBarsAcrossFloatReturnAndReset(boolean fxml) throws Exception {
        onFx(() -> {
            Platform.setImplicitExit(false);
            List<Window> existing = List.copyOf(Window.getWindows());
            Stage stage = new Stage();
            try (ReferenceWorkspace app = new ReferenceWorkspace(stage, fxml, false,
                    new WorkspaceStore(directory.resolve("workspace.json")), Optional.empty())) {
                Parent root = stage.getScene().getRoot(); root.applyCss(); root.layout();
                assertInstanceOf(DockPane.class, root.lookup("#workspaceDock"));
                ToolBar global = (ToolBar) root.lookup("#globalTools"); assertNotNull(global);
                TextArea notes = (TextArea) root.lookup("#notesText"); assertNotNull(notes);
                notes.setText("Unsaved workspace text"); notes.selectRange(2, 9);
                Object local = root.lookup("#notesLocalTools"); assertNotNull(local);
                button(root, "floatActive").fire();
                Stage floating = (Stage) Window.getWindows().stream().filter(w -> !existing.contains(w) && w != stage)
                        .findFirst().orElseThrow();
                assertSame(notes, floating.getScene().getRoot().lookup("#notesText"));
                assertSame(local, floating.getScene().getRoot().lookup("#notesLocalTools"));
                assertSame(global, root.lookup("#globalTools"));
                button(root, "returnActive").fire();
                assertSame(notes, root.lookup("#notesText"));
                button(root, "splitActive").fire();
                button(root, "resetLayout").fire();
                assertSame(notes, root.lookup("#notesText"));
                assertEquals("Unsaved workspace text", notes.getText());
                assertEquals(2, notes.getSelection().getStart()); assertEquals(9, notes.getSelection().getEnd());
            } finally { Window.getWindows().stream().filter(w -> !existing.contains(w)).toList().forEach(Window::hide); }
        });
    }
    @org.junit.jupiter.api.Test
    void saveRetryCancelAndRepeatedReplaceProtectTextThenRestoreFromTheRealFile() throws Exception {
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        AtomicReference<ReferenceWorkspace> appRef = new AtomicReference<>();
        AtomicReference<TextArea> original = new AtomicReference<>();
        WorkspaceStore store = new WorkspaceStore(directory.resolve("saved/workspace.json"));
        try {
            onFx(() -> {
                Platform.setImplicitExit(false);
                Stage stage = new Stage(); stageRef.set(stage);
                appRef.set(new ReferenceWorkspace(stage, true, false, store, Optional.empty()));
                Parent root = stage.getScene().getRoot(); root.applyCss(); root.layout();
                TextArea notes = (TextArea) root.lookup("#notesText"); original.set(notes);
                notes.setText("Unsaved Ω, survives retry and cancellation");
                ((javafx.scene.control.CheckBox) root.lookup("#vetoReadme")).setSelected(true);
                button(root, "replaceWorkspace").fire();
                String history = ((TextArea) root.lookup("#workspaceEvents")).getText();
                for (String id : List.of("readme", "notes", "project", "changes")) assertTrue(history.contains("Checked " + id));
                assertFalse(history.contains("Closed ")); assertFalse(history.contains("All consent obtained"));
                ((javafx.scene.control.CheckBox) root.lookup("#vetoReadme")).setSelected(false);
                ((javafx.scene.control.CheckBox) root.lookup("#saveFailure")).setSelected(true);
                button(root, "replaceWorkspace").fire(); button(root, "retrySave").fire();
                assertTrue(label(root).contains("Save failed")); assertSame(notes, root.lookup("#notesText"));
                button(root, "cancelSave").fire(); assertTrue(label(root).contains("Canceled"));
                assertSame(notes, root.lookup("#notesText"));
                ((javafx.scene.control.CheckBox) root.lookup("#saveFailure")).setSelected(false);
                button(root, "replaceWorkspace").fire(); button(root, "replaceWorkspace").fire();
                button(root, "retrySave").fire();
            });
            awaitFx(() -> ((TextArea) stageRef.get().getScene().getRoot().lookup("#workspaceEvents")).getText().contains("Reattached workspace"));
            onFx(() -> {
                Parent root = stageRef.get().getScene().getRoot(); root.applyCss(); root.layout();
                TextArea restored = (TextArea) root.lookup("#notesText");
                assertNotSame(original.get(), restored);
                assertEquals(original.get().getText(), restored.getText());
                assertTrue(label(root).contains("Applied"), "A repeated Replace must not run a second attachment: " + label(root));
                assertEquals(1, ((TextArea) root.lookup("#workspaceEvents")).getText().lines().filter(line -> line.startsWith("Reattached")).count());
            });
            StoredWorkspace saved = store.read().orElseThrow();
            assertEquals("Unsaved Ω, survives retry and cancellation", saved.documents().get(new de.neitzel.fx.docking.DockableId("notes")).text());
        } finally {
            onFx(() -> { if (appRef.get() != null) appRef.get().close(); if (stageRef.get() != null) stageRef.get().hide(); });
        }
    }

    @org.junit.jupiter.api.Test
    void savedFloatingMvvmEditorRestoresBeforeShowAndMissingReadmeIsReportedOnReplacement() throws Exception {
        WorkspaceStore store = new WorkspaceStore(directory.resolve("mvvm.json"));
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        AtomicReference<ReferenceWorkspace> appRef = new AtomicReference<>();
        AtomicReference<List<Window>> initial = new AtomicReference<>();
        AtomicReference<TextArea> original = new AtomicReference<>();
        try {
            onFx(() -> {
                Platform.setImplicitExit(false); initial.set(List.copyOf(Window.getWindows()));
                Stage stage = new Stage(); stageRef.set(stage);
                appRef.set(new ReferenceWorkspace(stage, true, true, store, Optional.empty()));
                Parent root = stage.getScene().getRoot(); root.applyCss(); root.layout();
                TextArea notes = (TextArea) root.lookup("#notesText"); original.set(notes);
                notes.setText("MVVM notes saved in a floating host"); notes.selectRange(5, 10);
                button(root, "floatActive").fire();
                Stage floating = (Stage) Window.getWindows().stream().filter(w -> !initial.get().contains(w) && w != stage).findFirst().orElseThrow();
                assertSame(notes, floating.getScene().getRoot().lookup("#notesText"));
                button(root, "saveWorkspace").fire();
            });
            awaitFx(() -> label(stageRef.get().getScene().getRoot()).startsWith("Saved workspace"));
            StoredWorkspace saved = store.read().orElseThrow();
            assertEquals(2, saved.layout().windows().size());
            onFx(() -> {
                appRef.get().close(); Window.getWindows().stream().filter(w -> !initial.get().contains(w)).toList().forEach(Window::hide);
                Stage stage = new Stage(); stageRef.set(stage);
                appRef.set(new ReferenceWorkspace(stage, true, true, store, Optional.of(saved)));
                Parent root = stage.getScene().getRoot(); root.applyCss(); root.layout();
                Stage floating = (Stage) Window.getWindows().stream().filter(w -> !initial.get().contains(w) && w != stage).findFirst().orElseThrow();
                TextArea notes = (TextArea) floating.getScene().getRoot().lookup("#notesText");
                assertNotSame(original.get(), notes); assertEquals(original.get().getText(), notes.getText());
                notes.selectRange(5, 10); button(root, "returnActive").fire();
                assertSame(notes, root.lookup("#notesText")); assertEquals(5, notes.getSelection().getStart());
                button(root, "clearActive").fire(); assertEquals("", notes.getText());
                assertTrue(root.lookupAll(".tab").stream().anyMatch(n -> n.lookupAll(".tab-label").stream()
                        .filter(javafx.scene.control.Label.class::isInstance).map(javafx.scene.control.Label.class::cast)
                        .anyMatch(l -> l.getText().equals("Notes *"))), "MVVM title still follows the edited model");
                ((javafx.scene.control.CheckBox) root.lookup("#skipReadme")).setSelected(true);
                ((javafx.scene.control.CheckBox) root.lookup("#blockProject")).setSelected(true);
                button(root, "replaceWorkspace").fire(); button(root, "retrySave").fire();
            });
            awaitFx(() -> ((TextArea) stageRef.get().getScene().getRoot().lookup("#workspaceEvents")).getText().contains("Reattached workspace"));
            onFx(() -> {
                Parent root = stageRef.get().getScene().getRoot(); root.applyCss(); root.layout();
                assertTrue(label(root).contains("readme"), label(root));
                assertFalse(root.lookupAll(".tab-label").stream().filter(javafx.scene.control.Label.class::isInstance)
                        .map(javafx.scene.control.Label.class::cast).anyMatch(l -> l.getText().startsWith("Readme")));
                assertNotNull(root.lookup("#notesText"));
            });
        } finally { onFx(() -> {
            if (appRef.get() != null) appRef.get().close();
            if (initial.get() != null) Window.getWindows().stream().filter(w -> !initial.get().contains(w)).toList().forEach(Window::hide);
        }); }
    }

    @org.junit.jupiter.api.Test
    void rejectedSnapshotLeavesAVisibleDiagnosticWhenTheInitialWorkspaceIsOpened() throws Exception {
        onFx(() -> {
            Platform.setImplicitExit(false);
            Stage stage = new Stage();
            var invalid = new de.neitzel.fx.docking.LayoutSnapshot(999, List.of(), null, java.util.Map.of(), java.util.Map.of());
            var saved = new StoredWorkspace(StoredWorkspace.CURRENT_VERSION, invalid, java.util.Map.of());
            try (ReferenceWorkspace app = new ReferenceWorkspace(stage, true, false,
                    new WorkspaceStore(directory.resolve("rejected.json")), Optional.of(saved))) {
                Parent root = stage.getScene().getRoot(); root.applyCss(); root.layout();
                assertNotNull(root.lookup("#notesText"), "The initial arrangement remains usable after rejection");
                assertTrue(label(root).contains("UNSUPPORTED_SNAPSHOT_VERSION"), label(root));
                assertTrue(label(root).contains("initial workspace"), label(root));
            } finally { stage.hide(); }
        });
    }

    private static String label(Parent root) { return ((javafx.scene.control.Label) root.lookup("#workspaceStatus")).getText(); }
    private static void awaitFx(java.util.function.BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < end) {
            java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
            onFx(() -> done.set(condition.getAsBoolean()));
            if (done.get()) return;
            Thread.sleep(25);
        }
        fail("Application did not complete its asynchronous operation");
    }

    static Button button(Parent root, String id) { return (Button) root.lookup("#" + id); }
    static void onFx(FxAction action) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); } catch (IllegalStateException running) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> { try { action.run(); } catch (Throwable e) { failure.set(e); } finally { done.countDown(); } });
        assertTrue(done.await(20, TimeUnit.SECONDS));
        if (failure.get() != null) fail("Reference workspace failed", failure.get());
    }
    @FunctionalInterface interface FxAction { void run() throws Exception; }
}
