package de.neitzel.fx.view;

import de.neitzel.fx.docking.ActionResult;
import de.neitzel.fx.docking.DockGroupDefinition;
import de.neitzel.fx.docking.DockLayout;
import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Activating a background window is an applied action even when its selected tab stays the same. */
class ActivateDockingWindowTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @Test
    void activatingTheSameContentInABackgroundWindowReportsApplied() throws Exception {
        AtomicReference<Fixture> state = new AtomicReference<>();
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage main = new Stage();
            main.setScene(new Scene(pane, 480, 280));
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            TextArea editor = new TextArea("Unsaved text");
            Dockable dockable = new Dockable(new DockableId("notes"), editor);
            manager.open(dockable, DockTarget.area("editors"));
            Stage other = new Stage();
            other.setScene(new Scene(new TextArea("Other window"), 240, 160));
            state.set(new Fixture(main, other, manager, dockable));
            main.show();
            manager.activate(dockable.getId());
        });
        Fixture fixture = state.get();
        try {
            focus(fixture.main());
            FxThread.onFx(() -> fixture.other().show());
            focus(fixture.other());
            FxThread.onFx(() -> {
                assertFalse(fixture.main().isFocused());
                assertSame(fixture.dockable(), fixture.manager().activeDockable());
                assertSame(fixture.dockable().getContent(), fixture.main().getScene().getFocusOwner());
                assertInstanceOf(ActionResult.Applied.class, fixture.manager().activate(fixture.dockable().getId()));
            });
        } finally {
            FxThread.onFx(() -> {
                fixture.other().hide();
                fixture.main().hide();
            });
        }
    }

    private static void focus(Stage stage) throws Exception {
        CountDownLatch focused = new CountDownLatch(1);
        ChangeListener<Boolean> listener = (observable, old, current) -> {
            if (current) focused.countDown();
        };
        FxThread.onFx(() -> {
            stage.focusedProperty().addListener(listener);
            stage.toFront();
            stage.requestFocus();
            if (stage.isFocused()) focused.countDown();
        });
        try {
            assertTrue(focused.await(5, TimeUnit.SECONDS), "Desktop did not focus the requested window");
        } finally {
            FxThread.onFx(() -> stage.focusedProperty().removeListener(listener));
        }
    }

    private record Fixture(Stage main, Stage other, DockingManager manager, Dockable dockable) { }
}
