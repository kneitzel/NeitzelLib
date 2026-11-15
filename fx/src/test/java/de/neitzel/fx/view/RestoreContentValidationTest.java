package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RestoreContentValidationTest {
    @BeforeAll static void startJavaFx() throws InterruptedException { FxThread.start(); }

    @ParameterizedTest @ValueSource(strings = {"wrong-id", "attached", "shared-node", "policy-reject", "policy-throw"})
    void preparedContentAndPlacementsAreCheckedBeforeAnyLayoutMutation(String fault) throws Exception {
        FxThread.onFx(() -> {
            LayoutSnapshot saved = RestoreValidationTest.saved();
            RuntimeException policyFailure = new IllegalStateException("Placement service failed");
            DockingOptions options = new DockingOptions();
            options.setPlacementRule((dockable, target) -> {
                if (dockable.getId().value().equals("b")) {
                    if (fault.equals("policy-throw")) throw policyFailure;
                    if (fault.equals("policy-reject")) return Optional.of(new DockFailure(
                            DockFailure.Code.PLACEMENT_REJECTED, null, "Destination is no longer allowed", null, "app.restore.blocked"));
                }
                return Optional.empty();
            });
            DockLayout initial = new DockLayout(); initial.setCenter(new DockGroupDefinition("start"));
            DockPane pane = new DockPane(); pane.setInitialLayout(initial);
            Stage stage = new Stage(); stage.setScene(new Scene(pane, 900, 600));
            DockingManager manager = DockingManager.attach(stage, pane, options);
            var before = manager.layout(); var snapshotBefore = manager.snapshot();
            TextArea shared = new TextArea("Existing content");
            StackPane foreignParent = new StackPane();
            if (fault.equals("attached")) foreignParent.getChildren().add(shared);
            List<Dockable> prepared = new ArrayList<>();
            AtomicInteger discards = new AtomicInteger();
            RestoreResult.Rejected result = assertInstanceOf(RestoreResult.Rejected.class,
                    manager.restore(saved, id -> {
                        Dockable dockable = new Dockable(fault.equals("wrong-id") ? new DockableId("wrong") : id,
                                fault.equals("shared-node") || fault.equals("attached") ? shared : new TextArea("Fresh " + id.value()));
                        dockable.setOnDiscarded(discards::incrementAndGet);
                        prepared.add(dockable);
                        return new DockableResolution.Available(dockable);
                    }));
            assertEquals(switch (fault) {
                case "wrong-id" -> DockFailure.Code.ID_MISMATCH;
                case "attached", "shared-node" -> DockFailure.Code.CONTENT_ALREADY_ATTACHED;
                case "policy-reject" -> DockFailure.Code.PLACEMENT_REJECTED;
                default -> DockFailure.Code.PLACEMENT_CHECK_FAILED;
            }, result.failure().code());
            assertNotNull(result.failure().dockableId());
            if (fault.equals("policy-reject")) assertEquals("app.restore.blocked", result.failure().messageKey());
            if (fault.equals("policy-throw")) assertSame(policyFailure, result.failure().cause());
            assertEquals(fault.equals("attached") ? 0 : prepared.size(), discards.get());
            assertSame(before, manager.layout());
            assertEquals(snapshotBefore, manager.snapshot());
            if (fault.equals("attached")) assertSame(foreignParent, shared.getParent());
            else for (Dockable dockable : prepared) assertNull(dockable.getContent().getParent());
        });
    }
}
