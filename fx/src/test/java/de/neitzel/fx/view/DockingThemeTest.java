package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.*;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DockingThemeTest {
    @BeforeAll static void startJavaFx() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @Test void applicationThemeStylesHeadersAndBothDragPreviewsAcrossWindows() throws Exception {
        FxThread.onFx(() -> {
            var existing = List.copyOf(Window.getWindows());
            String theme = getClass().getResource("docking-theme.css").toExternalForm();
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            pane.getInitialLayout().setRight(new DockGroupDefinition("tools"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 760, 460));
            main.setX(80); main.setY(100);
            main.getScene().getStylesheets().add(theme);
            DockingOptions options = new DockingOptions();
            options.setFloatingWindowCustomizer(configuration -> configuration.getStylesheets().add(theme));
            DockingManager manager = DockingManager.attach(main, pane, options);
            Dockable notes = dockable("Notes"), tools = dockable("Tools");
            manager.open(notes, DockTarget.area("editors"));
            manager.open(tools, DockTarget.area("tools"));
            main.show();
            try {
                pane.applyCss(); pane.layout();
                Node source = header(pane, "Notes");
                assertHeaderColor(source);
                Bounds from = source.localToScreen(source.getBoundsInLocal());
                Bounds into = tools.getContent().localToScreen(tools.getContent().getBoundsInLocal());
                mouse(source, MouseEvent.MOUSE_PRESSED, from.getCenterX(), from.getCenterY(), true);
                mouse(source, MouseEvent.MOUSE_DRAGGED, into.getCenterX(), into.getCenterY(), true);
                pane.applyCss();
                Rectangle marker = (Rectangle) pane.lookup(".dock-drop-indicator");
                assertTrue(marker.isVisible());
                assertEquals(Color.web("#123456"), marker.getFill());
                assertEquals(Color.web("#654321"), marker.getStroke());
                mouse(source, MouseEvent.MOUSE_DRAGGED, into.getCenterX() + 2, into.getCenterY(), true);
                pane.applyCss();
                assertEquals(Color.web("#123456"), marker.getFill(), "Continuous drag preserves the application theme");
                mouse(source, MouseEvent.MOUSE_DRAGGED, main.getX() + main.getWidth() + 60, main.getY() + 100, true);
                Window preview = Window.getWindows().stream().filter(window -> window.getScene() != null
                        && window.getScene().getRoot().lookup(".dock-floating-preview-frame") != null).findFirst().orElseThrow();
                preview.getScene().getRoot().applyCss();
                Rectangle frame = (Rectangle) preview.getScene().getRoot().lookup(".dock-floating-preview-frame");
                assertEquals(Color.web("#234567"), frame.getFill());
                assertEquals(Color.web("#765432"), frame.getStroke());
                Event.fireEvent(main.getScene(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                        false, false, false, false));
                assertFalse(preview.isShowing());
                manager.move(notes.getId(), DockTarget.floating());
                var floatingRoot = notes.getContent().getScene().getRoot();
                floatingRoot.applyCss(); floatingRoot.layout();
                assertHeaderColor(header(floatingRoot, "Notes"));
            } finally {
                for (Window window : List.copyOf(Window.getWindows())) if (!existing.contains(window)) window.hide();
            }
        });
    }
    @Test void splitAndOuterDividersAreThemeableBeforeAnyPointerInteraction() throws Exception {
        FxThread.onFx(() -> {
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            pane.getInitialLayout().setRight(new DockGroupDefinition("tools"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 1000, 600));
            main.getScene().getStylesheets().add(getClass().getResource("docking-theme.css").toExternalForm());
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            Dockable notes = dockable("Notes"), other = dockable("Other");
            manager.open(notes, DockTarget.area("editors"));
            manager.open(other, DockTarget.area("editors"));
            manager.open(dockable("Tools"), DockTarget.area("tools"));
            main.show();
            try {
                pane.applyCss(); pane.layout();
                var group = manager.layout().windows().getFirst().areas().getFirst().groups().getFirst().id();
                manager.move(other.getId(), DockTarget.split(group, javafx.geometry.Side.BOTTOM));
                pane.applyCss(); pane.layout();
                Node split = pane.lookup(".dock-divider");
                assertNotNull(split, "A split exposes its theme hook before the user points at it");
                assertEquals(Color.web("#345678"), ((Region) split).getBackground().getFills().getFirst().getFill());
                Node outer = pane.lookup(".dock-area-divider");
                assertNotNull(outer);
                assertEquals(Color.web("#456789"), ((Region) outer).getBackground().getFills().getFirst().getFill());
                assertFalse(pane.lookupAll(".dock-edge-area").isEmpty());
            } finally { main.hide(); }
        });
    }

    private static void assertHeaderColor(Node node) {
        assertEquals(Color.web("#f1e2c3"), ((Region) node).getBackground().getFills().getFirst().getFill());
    }
    private static Dockable dockable(String name) {
        Dockable result = new Dockable(new DockableId(name), new TextArea(name)); result.setTitle(name); return result;
    }
    private static Node header(Node root, String text) {
        return root.lookupAll(".dock-tab-header").stream()
                .filter(node -> node.lookup(".tab-label") instanceof Label label && text.equals(label.getText()))
                .findFirst().orElseThrow();
    }
    private static void mouse(Node source, javafx.event.EventType<MouseEvent> type, double x, double y, boolean down) {
        var local = source.screenToLocal(x, y);
        source.fireEvent(new MouseEvent(type, local.getX(), local.getY(), x, y, MouseButton.PRIMARY, 1,
                false, false, false, false, down, false, false, false, false, false,
                new PickResult(source, local.getX(), local.getY())));
    }
}
