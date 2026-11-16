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
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.input.MouseButton;
import javafx.scene.robot.Robot;
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
        AnimationTimer clickAfterDisplay = new AnimationTimer() {
            private int pulses;
            private long lastClick;

            @Override
            public void handle(long now) {
                if (stage.isFocused()) {
                    focused.countDown();
                    stop();
                    return;
                }
                if (++pulses < 2) return;
                if (now - lastClick < 250_000_000) return;
                lastClick = now;
                Bounds bounds = stage.getScene().getRoot().localToScreen(
                        stage.getScene().getRoot().getBoundsInLocal());
                Robot robot = new Robot();
                robot.mouseMove(bounds.getCenterX(), bounds.getCenterY());
                robot.mouseClick(MouseButton.PRIMARY);
                if (stage.isFocused()) focused.countDown();
            }
        };
        FxThread.onFx(() -> {
            stage.focusedProperty().addListener(listener);
            stage.setAlwaysOnTop(true);
            stage.toFront();
            stage.requestFocus();
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            clickAfterDisplay.start();
        });
        try {
            // A native click activates the application even if the previous test or
            // another application owned desktop focus. RequestFocus alone cannot
            // establish that precondition reliably on macOS. FX pulses do not
            // guarantee that the native compositor has displayed the window;
            // retry only this focus precondition within the bounded wait.
            assertTrue(focused.await(5, TimeUnit.SECONDS), "Desktop did not focus the requested window");
        } finally {
            FxThread.onFx(() -> {
                clickAfterDisplay.stop();
                stage.focusedProperty().removeListener(listener);
                stage.setAlwaysOnTop(false);
            });
        }
    }

    private record Fixture(Stage main, Stage other, DockingManager manager, Dockable dockable) { }
}
