package de.neitzel.fx.view;

import de.neitzel.fx.control.StatusBar;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Standalone bars display application content without a docking or MVVM runtime. */
class StandaloneBarsTest {
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        FxThread.start();
    }

    @Test
    void statusContentStaysAtOppositeEdgesAndFollowsBindings() throws Exception {
        FxThread.onFx(() -> {
            var text = new SimpleStringProperty("Ready");
            Label status = new Label();
            status.textProperty().bind(text);
            Label cursor = new Label("Line 4, column 7");
            StatusBar bar = new StatusBar();
            bar.getLeftItems().add(status);
            bar.getRightItems().add(cursor);
            BorderPane root = new BorderPane();
            root.setBottom(bar);
            Scene scene = new Scene(root, 640, 240);
            root.applyCss();
            root.layout();

            assertSame(scene, status.getScene());
            assertSame(scene, cursor.getScene());
            assertTrue(status.localToScene(status.getBoundsInLocal()).getMinX() < 20);
            assertTrue(cursor.localToScene(cursor.getBoundsInLocal()).getMaxX() > 620);
            assertTrue(status.localToScene(status.getBoundsInLocal()).getMaxX()
                    < cursor.localToScene(cursor.getBoundsInLocal()).getMinX());
            text.set("Saved");
            assertEquals("Saved", status.getText());
        });
    }

    @Test
    void plainFxmlLoadsBothBarsAndTheirApplicationNodes() throws Exception {
        FxThread.onFx(() -> {
            var resource = getClass().getResource("standalone-bars.fxml");
            assertNotNull(resource);
            FXMLLoader loader = new FXMLLoader(resource);
            BorderPane root = loader.load();
            ToolBar toolbar = (ToolBar) root.getTop();
            StatusBar statusBar = (StatusBar) root.getBottom();
            Button action = (Button) toolbar.getItems().getFirst();
            Label status = (Label) statusBar.getLeftItems().getFirst();
            ProgressBar progress = (ProgressBar) statusBar.getRightItems().getFirst();
            action.setOnAction(event -> {
                status.setText("Complete");
                progress.setProgress(1);
            });
            new Scene(root, 640, 240);
            root.applyCss();
            root.layout();

            action.fire();
            assertEquals("Complete", status.getText());
            assertEquals(1, progress.getProgress());
            assertNotNull(progress.getScene());
            statusBar.getRightItems().remove(progress);
            assertNull(progress.getParent());
            assertNull(progress.getScene());
        });
    }
}
