package de.neitzel.fx.view;

import de.neitzel.fx.docking.DockGroupDefinition;
import de.neitzel.fx.docking.DockLayout;
import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.DockSplitDefinition;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Focus between already-selected visible contents must update saved active identity too. */
class SnapshotFocusTest {
    @BeforeAll static void startJavaFx() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @Test
    void focusBetweenSelectedGroupsUpdatesTheSnapshotWithoutChangingItsGeometry() throws Exception {
        AtomicReference<Fixture> fixture = new AtomicReference<>();
        FxThread.onFx(() -> {
            DockSplitDefinition split = new DockSplitDefinition();
            split.setDestinationId("editors");
            split.setFirst(new DockGroupDefinition("first"));
            split.setSecond(new DockGroupDefinition("second"));
            DockLayout initial = new DockLayout();
            initial.setCenter(split);
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 900, 600));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            Dockable first = new Dockable(new DockableId("first"), new TextArea("First editor"));
            Dockable second = new Dockable(new DockableId("second"), new TextArea("Second editor"));
            manager.open(first, DockTarget.area("first"));
            manager.open(second, DockTarget.area("second"));
            stage.show();
            pane.applyCss();
            pane.layout();
            fixture.set(new Fixture(stage, manager, first, second));
        });
        try {
            FxThread.onFx(() -> {
                Fixture current = fixture.get();
                current.manager().activate(current.first().getId());
                assertSame(current.first().getContent(), current.stage().getScene().getFocusOwner());
                var committed = current.manager().snapshot();
                current.second().getContent().requestFocus();
                assertSame(current.second().getContent(), current.stage().getScene().getFocusOwner());
                assertSame(current.second(), current.manager().activeDockable());
                assertEquals(current.second().getId(), current.manager().snapshot().activeDockableId(),
                        "A focus-only change must not leave the saved active identity stale");
                assertEquals(committed.windows(), current.manager().snapshot().windows());
            });
        } finally { FxThread.onFx(() -> fixture.get().stage().hide()); }
    }

    private record Fixture(Stage stage, DockingManager manager, Dockable first, Dockable second) { }
}
