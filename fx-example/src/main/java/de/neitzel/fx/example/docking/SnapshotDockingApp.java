package de.neitzel.fx.example.docking;

import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockableResolution;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import de.neitzel.fx.docking.LayoutSnapshot;
import de.neitzel.fx.docking.RestoreResult;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Keeps layout data in application memory and restores fresh content before showing another workspace. */
public final class SnapshotDockingApp extends Application {
    private static final List<Document> DOCUMENTS = List.of(
            new Document("notes", "primary", "Notes", "Notes loaded from the application document source."),
            new Document("readme", "secondary", "Readme", "The saved layout contains identities, not this text."),
            new Document("preview", "preview", "Preview", "Preview belongs to a nested editor split."),
            new Document("project", "tools", "Project", "Project is a temporary overlay."));
    private LayoutSnapshot saved;

    @Override public void start(Stage stage) throws Exception {
        DockPane pane = loadPane();
        Label status = new Label("Rearrange the workspace, capture its layout, then restore into a fresh window.");
        status.setId("snapshotStatus");
        status.setWrapText(true);
        Button capture = new Button("Capture layout");
        Button restore = new Button("Restore saved layout");
        Button incompatible = new Button("Try incompatible version");
        Button failed = new Button("Simulate load failure");
        CheckBox cleanupFailure = new CheckBox("Simulate cleanup failure");
        stage.setScene(new Scene(new BorderPane(pane, new ToolBar(capture, restore, incompatible, failed, cleanupFailure),
                null, status, null), 1120, 680));
        DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
        DOCUMENTS.forEach(document -> manager.open(content(document), DockTarget.area(document.area())));
        saved = manager.snapshot();
        capture.setOnAction(event -> {
            saved = manager.snapshot();
            status.setText("Captured layout in application memory; document text remains outside the snapshot.");
        });
        restore.setOnAction(event -> restoreWindow(saved, false, false, status));
        incompatible.setOnAction(event -> restoreWindow(new LayoutSnapshot(saved.version() + 1,
                saved.windows(), saved.activeDockableId(), saved.returnPositions(), saved.initialIntents()), false, false, status));
        failed.setOnAction(event -> restoreWindow(saved, true, cleanupFailure.isSelected(), status));
        stage.setTitle("NeitzelLib — application-owned layout snapshots");
        stage.show();
    }

    private static void restoreWindow(LayoutSnapshot snapshot, boolean failLoading, boolean failCleanup, Label sourceStatus) {
        Stage target = new Stage();
        try {
            DockPane pane = loadPane();
            Label status = new Label();
            status.setId("snapshotStatus");
            status.setWrapText(true);
            target.setScene(new Scene(new BorderPane(pane, null, null, status, null), 1000, 650));
            DockingManager manager = DockingManager.attach(target, pane, new DockingOptions());
            AtomicInteger prepared = new AtomicInteger();
            AtomicInteger discarded = new AtomicInteger();
            RestoreResult result = manager.restore(snapshot, id -> {
                if (failLoading && id.value().equals("readme")) {
                    return new DockableResolution.Failed(new IllegalStateException("Demo document source is unavailable"));
                }
                Document document = DOCUMENTS.stream().filter(candidate -> candidate.id().equals(id.value())).findFirst().orElseThrow();
                Dockable content = content(document);
                prepared.incrementAndGet();
                content.setOnDiscarded(() -> {
                    discarded.incrementAndGet();
                    if (failCleanup) throw new IllegalStateException("Demo cleanup could not finish");
                });
                return new DockableResolution.Available(content);
            });
            String message = switch (result) {
                case RestoreResult.Applied applied -> "Restored before showing this window; prepared " + prepared.get()
                        + " fresh contents, missing " + applied.missingIds().size() + ".";
                case RestoreResult.Rejected rejected -> "Fallback layout retained: " + rejected.failure().code()
                        + "; prepared " + prepared.get() + ", discarded " + discarded.get()
                        + ", cleanup diagnostics " + rejected.cleanupFailures().size() + "."
                        + (rejected.failure().cause() == null ? "" : " " + rejected.failure().cause().getMessage());
            };
            status.setText(message);
            sourceStatus.setText(message);
            target.setTitle(result instanceof RestoreResult.Applied ? "Restored snapshot" : "Unchanged fallback layout");
            target.show();
        } catch (java.io.IOException failure) {
            target.hide();
            sourceStatus.setText("Cannot load the workspace FXML: " + failure.getMessage());
        }
    }

    private static DockPane loadPane() throws java.io.IOException {
        return FXMLLoader.load(SnapshotDockingApp.class.getResource("SnapshotDocking.fxml"));
    }

    private static Dockable content(Document document) {
        Dockable content = new Dockable(new DockableId(document.id()), new TextArea(document.text()));
        content.setTitle(document.title());
        return content;
    }

    private record Document(String id, String area, String title, String text) { }
}
