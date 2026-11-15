package de.neitzel.fx.example.docking;

import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.ActionResult;
import de.neitzel.fx.docking.DockFailure;
import de.neitzel.fx.docking.DockGroupId;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockableResolution;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import de.neitzel.fx.docking.LayoutSnapshot;
import de.neitzel.fx.docking.RestoreResult;
import de.neitzel.fx.docking.ResolvedDockTarget;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;
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
        Button missing = new Button("Restore without Preview/Project");
        Button changedRule = new Button("Restore with changed rule");
        Button rejected = new Button("Reject Project placement");
        Button floating = new Button("Prepare floating snapshot");
        Button partialFloating = new Button("Restore partial floating");
        Button offscreen = new Button("Restore off-screen windows");
        Button abortCustomizer = new Button("Abort floating customizer");
        CheckBox cleanupFailure = new CheckBox("Simulate cleanup failure");
        stage.setScene(new Scene(new BorderPane(pane, new VBox(new ToolBar(capture, restore, incompatible, failed, cleanupFailure),
                new ToolBar(missing, changedRule, rejected), new ToolBar(floating, offscreen, partialFloating, abortCustomizer)),
                null, status, null), 1120, 680));
        DockingManager manager = DockingManager.attach(stage, pane, restoreOptions(RestoreScenario.NORMAL));
        DOCUMENTS.forEach(document -> manager.open(content(document), DockTarget.area(document.area())));
        saved = manager.snapshot();
        Runnable updateFloatingActions = () -> {
            long count = saved.windows().stream().filter(window -> !window.main()).count();
            partialFloating.setDisable(count == 0);
            offscreen.setDisable(count == 0);
            abortCustomizer.setDisable(count < 2);
        };
        updateFloatingActions.run();
        capture.setOnAction(event -> {
            saved = manager.snapshot();
            updateFloatingActions.run();
            status.setText("Captured layout in application memory; document text remains outside the snapshot.");
        });
        restore.setOnAction(event -> restoreWindow(saved, RestoreScenario.NORMAL, false, status));
        incompatible.setOnAction(event -> restoreWindow(new LayoutSnapshot(saved.version() + 1,
                saved.windows(), saved.activeDockableId(), saved.returnPositions(), saved.initialIntents()), RestoreScenario.NORMAL, false, status));
        failed.setOnAction(event -> restoreWindow(saved, RestoreScenario.LOAD_FAILURE, cleanupFailure.isSelected(), status));
        missing.setOnAction(event -> restoreWindow(saved, RestoreScenario.MISSING_CONTENT, false, status));
        changedRule.setOnAction(event -> restoreWindow(saved, RestoreScenario.PROJECT_FALLBACK, false, status));
        rejected.setOnAction(event -> restoreWindow(saved, RestoreScenario.REJECT_PROJECT, false, status));
        floating.setOnAction(event -> {
            for (String id : List.of("preview", "project")) {
                ActionResult moved = manager.move(new DockableId(id), DockTarget.floating());
                if (moved instanceof ActionResult.Rejected failure) {
                    status.setText(manager.describeFailure(failure.failure()));
                    return;
                }
            }
            saved = manager.snapshot();
            updateFloatingActions.run();
            status.setText("Captured " + saved.windows().stream().filter(window -> !window.main()).count()
                    + " floating windows; Project retains its tools-area return target.");
        });
        partialFloating.setOnAction(event -> restoreWindow(saved, RestoreScenario.PARTIAL_FLOATING, false, status));
        offscreen.setOnAction(event -> restoreWindow(offscreenSnapshot(saved), RestoreScenario.OFFSCREEN, false, status));
        abortCustomizer.setOnAction(event -> restoreWindow(saved, RestoreScenario.ABORT_CUSTOMIZER, false, status));
        stage.setTitle("NeitzelLib — application-owned layout snapshots");
        stage.show();
    }

    private static void restoreWindow(LayoutSnapshot snapshot, RestoreScenario scenario, boolean failCleanup, Label sourceStatus) {
        List<Window> visibleBefore = List.copyOf(Window.getWindows());
        Stage target = new Stage();
        try {
            DockPane pane = loadPane();
            Label status = new Label();
            status.setId("snapshotStatus");
            status.setWrapText(true);
            target.setScene(new Scene(new BorderPane(pane, null, null, status, null), 1000, 650));
            DockingManager manager = DockingManager.attach(target, pane, restoreOptions(scenario));
            AtomicInteger prepared = new AtomicInteger();
            AtomicInteger discarded = new AtomicInteger();
            RestoreResult result = manager.restore(snapshot, id -> {
                if ((scenario == RestoreScenario.MISSING_CONTENT
                        && (id.value().equals("preview") || id.value().equals("project")))
                        || scenario == RestoreScenario.PARTIAL_FLOATING && id.value().equals("preview")) {
                    return new DockableResolution.Missing();
                }
                if (scenario == RestoreScenario.LOAD_FAILURE && id.value().equals("readme")) {
                    return new DockableResolution.Failed(new IllegalStateException("Demo document source is unavailable"));
                }
                Document document = DOCUMENTS.stream().filter(candidate -> candidate.id().equals(id.value())).findFirst().orElseThrow();
                Dockable content = content(document);
                if (scenario == RestoreScenario.PARTIAL_FLOATING && id.value().equals("project")) content.setMainWindowOnly(true);
                prepared.incrementAndGet();
                content.setOnDiscarded(() -> {
                    discarded.incrementAndGet();
                    if (failCleanup) throw new IllegalStateException("Demo cleanup could not finish");
                });
                return new DockableResolution.Available(content);
            });
            String message = switch (result) {
                case RestoreResult.Applied applied -> "Restored before showing this window; prepared " + prepared.get()
                        + " fresh contents, missing " + applied.missingIds().size()
                        + (applied.missingIds().isEmpty() ? "." : " " + applied.missingIds().stream().map(DockableId::value).toList() + ".")
                        + (scenario == RestoreScenario.PROJECT_FALLBACK || scenario == RestoreScenario.PARTIAL_FLOATING
                                ? " Configured editor fallback (primary)." : "")
                        + (scenario == RestoreScenario.OFFSCREEN ? " Saved off-screen bounds fitted to available displays." : "");
                case RestoreResult.Rejected rejected -> "Fallback layout retained: " + rejected.failure().code()
                        + (rejected.failure().dockableId() == null ? "" : " (" + rejected.failure().dockableId().value() + ")")
                        + "; prepared " + prepared.get() + ", discarded " + discarded.get()
                        + ", cleanup diagnostics " + rejected.cleanupFailures().size() + "."
                        + " " + manager.describeFailure(rejected.failure())
                        + (rejected.failure().cause() == null ? "" : " " + rejected.failure().cause().getMessage());
            };
            Button returnProject = new Button("Return Project");
            DockableId projectId = new DockableId("project");
            returnProject.setDisable(manager.findDockable(projectId).isEmpty());
            returnProject.setOnAction(event -> status.setText(switch (manager.move(projectId, DockTarget.returnToMain())) {
                case ActionResult.Applied ignored -> "Project returned to its preserved main-window target.";
                case ActionResult.NoChange ignored -> "Project is already in the main window.";
                case ActionResult.Rejected rejected -> manager.describeFailure(rejected.failure());
            }));
            ((BorderPane) target.getScene().getRoot()).setTop(new ToolBar(returnProject));
            target.setTitle(result instanceof RestoreResult.Applied ? "Restored snapshot" : "Unchanged fallback layout");
            long beforeShow = visibleFloatingWindows(visibleBefore, target);
            target.show();
            message += " Floating windows before main show: " + beforeShow + "; now: "
                    + visibleFloatingWindows(visibleBefore, target) + ".";
            status.setText(message);
            sourceStatus.setText(message);
        } catch (java.io.IOException failure) {
            target.hide();
            sourceStatus.setText("Cannot load the workspace FXML: " + failure.getMessage());
        }
    }

    private static DockPane loadPane() throws java.io.IOException {
        return FXMLLoader.load(SnapshotDockingApp.class.getResource("SnapshotDocking.fxml"));
    }

    private static long visibleFloatingWindows(List<Window> previous, Stage main) {
        return Window.getWindows().stream().filter(window -> window != main && !previous.contains(window)).count();
    }

    private static LayoutSnapshot offscreenSnapshot(LayoutSnapshot snapshot) {
        return new LayoutSnapshot(snapshot.version(), snapshot.windows().stream().map(window -> window.main() ? window
                : new LayoutSnapshot.Window(window.id(), false,
                        new Rectangle2D(-100_000, -100_000, window.bounds().getWidth(), window.bounds().getHeight()),
                        window.roots())).toList(), snapshot.activeDockableId(), snapshot.returnPositions(), snapshot.initialIntents());
    }

    private static DockingOptions restoreOptions(RestoreScenario scenario) {
        DockingOptions options = new DockingOptions();
        AtomicInteger configured = new AtomicInteger();
        options.setFloatingWindowCustomizer(configuration -> {
            if (scenario == RestoreScenario.ABORT_CUSTOMIZER && configured.incrementAndGet() == 2) {
                throw new IllegalStateException("Demo customizer rejected the second floating window");
            }
            configuration.setTitle("Snapshot floating content");
        });
        if (scenario == RestoreScenario.PARTIAL_FLOATING) options.setFallbackTarget(DockTarget.area("primary"));
        if (scenario == RestoreScenario.PROJECT_FALLBACK || scenario == RestoreScenario.REJECT_PROJECT) {
            options.setFallbackTarget(DockTarget.area("primary"));
            DockGroupId tools = new DockGroupId("tools");
            options.setPlacementRule((dockable, destination) -> {
                boolean forbidden = dockable.getId().value().equals("project")
                        && (scenario == RestoreScenario.REJECT_PROJECT
                        || destination instanceof ResolvedDockTarget.Insertion insertion && insertion.groupId().equals(tools));
                return forbidden ? Optional.of(new DockFailure(DockFailure.Code.PLACEMENT_REJECTED,
                        dockable.getId(), "Project is not allowed in this destination", null)) : Optional.empty();
            });
        }
        return options;
    }

    private static Dockable content(Document document) {
        Dockable content = new Dockable(new DockableId(document.id()), new TextArea(document.text()));
        content.setTitle(document.title());
        return content;
    }

    private record Document(String id, String area, String title, String text) { }
    private enum RestoreScenario {
        NORMAL, LOAD_FAILURE, MISSING_CONTENT, PROJECT_FALLBACK, REJECT_PROJECT,
        PARTIAL_FLOATING, OFFSCREEN, ABORT_CUSTOMIZER
    }
}
