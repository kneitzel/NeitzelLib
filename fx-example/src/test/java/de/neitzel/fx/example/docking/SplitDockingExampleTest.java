package de.neitzel.fx.example.docking;

import de.neitzel.fx.docking.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the example resource through the same ordinary FXML/public manager path as clients. */
class SplitDockingExampleTest {
    @Test
    void theNestedFxmlExampleOpensEachNamedDestinationWithoutMvvm() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); }
        catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                DockPane pane = FXMLLoader.load(DockingApp.class.getResource("SplitDocking.fxml"));
                Stage stage = new Stage();
                stage.setScene(new Scene(pane, 900, 600));
                DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
                for (String target : new String[]{"primary", "preview", "output", "tools"}) {
                    assertInstanceOf(ActionResult.Applied.class,
                            manager.open(new Dockable(new DockableId(target), new TextArea(target)), DockTarget.area(target)));
                }
                DockLayoutView.Area editors = manager.layout().windows().getFirst().areas().getFirst();
                assertEquals(3, editors.groups().size());
                assertInstanceOf(DockLayoutView.Split.class,
                        assertInstanceOf(DockLayoutView.Split.class, editors.root()).second());
            } catch (Throwable thrown) { failure.set(thrown); }
            finally { done.countDown(); }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) fail("Nested docking example failed", failure.get());
    }
}
