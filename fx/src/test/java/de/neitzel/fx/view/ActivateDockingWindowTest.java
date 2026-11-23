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
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/** Activating a background window is an applied action even when its selected tab stays the same. */
class ActivateDockingWindowTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        FxThread.start();
    }

    @Test
    void activatingTheSameContentInABackgroundWindowReportsApplied() throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage main = spy(new Stage());
            main.setScene(new Scene(pane, 480, 280));
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            TextArea editor = new TextArea("Unsaved text");
            Dockable dockable = new Dockable(new DockableId("notes"), editor);
            manager.open(dockable, DockTarget.area("editors"));

            // Only the desktop's window state is simulated. Selection, scene focus and
            // the activation result all come from the real controls and manager.
            doReturn(true).when(main).isShowing();
            doReturn(true).when(main).isFocused();
            manager.activate(dockable.getId());
            assertSame(dockable, manager.activeDockable());
            assertSame(editor, main.getScene().getFocusOwner());
            assertInstanceOf(ActionResult.NoChange.class, manager.activate(dockable.getId()));

            doReturn(false).when(main).isFocused();
            clearInvocations(main);
            assertInstanceOf(ActionResult.Applied.class, manager.activate(dockable.getId()));
            verify(main).requestFocus();
        });
    }
}
