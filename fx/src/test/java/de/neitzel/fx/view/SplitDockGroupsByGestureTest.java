package de.neitzel.fx.view;

import de.neitzel.fx.docking.DockGroupDefinition;
import de.neitzel.fx.docking.DockLayout;
import de.neitzel.fx.docking.DockLayoutView;
import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import de.neitzel.fx.docking.DockFailure;
import de.neitzel.fx.docking.ResolvedDockTarget;
import de.neitzel.fx.docking.SplitDirections;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TabPane;
import javafx.scene.control.Menu;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Split gestures are observed through visible controls and the public layout. */
class SplitDockGroupsByGestureTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    void anEdgeDropCreatesThePreviewedAdjacentGroup(Side edge) throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 900, 500));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            TextArea notes = new TextArea("Unsaved notes");
            Dockable editor = new Dockable(new DockableId("notes"), notes);
            editor.setTitle("Notes");
            TextArea project = new TextArea("Project");
            Dockable tools = new Dockable(new DockableId("project"), project);
            tools.setTitle("Project");
            manager.open(editor, DockTarget.area("editors"));
            manager.open(tools, DockTarget.area("editors"));
            stage.show();
            try {
                pane.applyCss();
                pane.layout();
                Node header = header(pane, "Notes");
                Bounds from = header.localToScreen(header.getBoundsInLocal());
                Bounds into = project.localToScreen(project.getBoundsInLocal());
                double x = edge == Side.LEFT ? into.getMinX() + 8
                        : edge == Side.RIGHT ? into.getMaxX() - 8 : into.getCenterX();
                double y = edge == Side.TOP ? into.getMinY() + 8
                        : edge == Side.BOTTOM ? into.getMaxY() - 8 : into.getCenterY();
                mouse(header, MouseEvent.MOUSE_PRESSED, from.getCenterX(), from.getCenterY(), true);
                mouse(header, MouseEvent.MOUSE_DRAGGED, x, y, true);
                assertTrue(pane.lookup(".dock-drop-indicator").isVisible());
                assertEquals(1, manager.layout().windows().getFirst().areas().getFirst().groups().size());
                mouse(header, MouseEvent.MOUSE_RELEASED, x, y, false);

                DockLayoutView.Area area = manager.layout().windows().getFirst().areas().getFirst();
                assertInstanceOf(DockLayoutView.Split.class, area.root());
                assertEquals(2, area.groups().size());
                boolean before = edge == Side.TOP || edge == Side.LEFT;
                assertEquals(List.of(editor.getId()), area.groups().get(before ? 0 : 1).dockableIds());
                assertSame(notes, manager.findDockable(editor.getId()).orElseThrow().getContent());
                assertEquals("Unsaved notes", notes.getText());
            } finally {
                stage.hide();
            }
        });
    }

    @Test
    void splitMenuExplainsRejectedDirectionsAndUsesTheSameMoveOperation() throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 900, 500));
            AtomicBoolean allowed = new AtomicBoolean(false);
            DockingOptions options = new DockingOptions();
            options.setPlacementRule((dockable, target) -> target instanceof ResolvedDockTarget.Split && !allowed.get()
                    ? Optional.of(new DockFailure(DockFailure.Code.PLACEMENT_REJECTED, dockable.getId(), "No splits today", null))
                    : Optional.empty());
            DockingManager manager = DockingManager.attach(stage, pane, options);
            Dockable notes = new Dockable(new DockableId("notes"), new TextArea("Notes"));
            notes.setTitle("Notes");
            manager.open(notes, DockTarget.area("editors"));
            manager.open(new Dockable(new DockableId("project"), new TextArea("Project")), DockTarget.area("editors"));
            stage.show();
            ContextMenu context = null;
            try {
                pane.applyCss();
                pane.layout();
                context = ((TabPane) pane.lookup(".tab-pane")).getTabs().getFirst().getContextMenu();
                context.show(stage, stage.getX() + 80, stage.getY() + 80);
                Menu splits = context.getItems().stream().filter(item -> "Split".equals(item.getText()))
                        .map(Menu.class::cast).findFirst().orElse(null);
                assertNotNull(splits);
                MenuItem right = splits.getItems().stream().filter(item -> item.getText().startsWith("Right"))
                        .findFirst().orElseThrow();
                assertTrue(right.isDisable());
                assertTrue(right.getText().contains("No splits today"));
                context.hide();
                allowed.set(true);
                context.show(stage, stage.getX() + 80, stage.getY() + 80);
                right = splits.getItems().stream().filter(item -> item.getText().startsWith("Right"))
                        .findFirst().orElseThrow();
                assertFalse(right.isDisable());
                right.fire();
                assertInstanceOf(DockLayoutView.Split.class, manager.layout().windows().getFirst().areas().getFirst().root());
            } finally {
                if (context != null) context.hide();
                stage.hide();
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"directions", "minimum", "placement"})
    void aRejectedEdgeSplitFallsBackToJoiningTheTargetGroup(String restriction) throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            DockGroupDefinition editors = new DockGroupDefinition("editors");
            if (restriction.equals("directions")) editors.setSplitDirections(SplitDirections.VERTICAL);
            if (restriction.equals("minimum")) editors.setMinimumWidth(400);
            initial.setCenter(editors);
            initial.setLeft(new DockGroupDefinition("tools"));
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 900, 500));
            DockingOptions options = new DockingOptions();
            if (restriction.equals("placement")) options.setPlacementRule((dockable, target) ->
                    target instanceof ResolvedDockTarget.Split ? Optional.of(new DockFailure(
                            DockFailure.Code.PLACEMENT_REJECTED, dockable.getId(), "No split", null)) : Optional.empty());
            DockingManager manager = DockingManager.attach(stage, pane, options);
            TextArea content = new TextArea("Editor");
            Dockable editor = new Dockable(new DockableId("editor"), content);
            manager.open(editor, DockTarget.area("editors"));
            Dockable notes = new Dockable(new DockableId("notes"), new TextArea("Notes"));
            notes.setTitle("Notes");
            manager.open(notes, DockTarget.area("tools"));
            stage.show();
            try {
                pane.applyCss();
                pane.layout();
                Node header = header(pane, "Notes");
                Bounds from = header.localToScreen(header.getBoundsInLocal());
                Bounds target = content.localToScreen(content.getBoundsInLocal());
                double x = target.getMaxX() - 8;
                double y = target.getCenterY();
                mouse(header, MouseEvent.MOUSE_PRESSED, from.getCenterX(), from.getCenterY(), true);
                mouse(header, MouseEvent.MOUSE_DRAGGED, x, y, true);
                assertTrue(pane.lookup(".dock-drop-indicator").isVisible());
                mouse(header, MouseEvent.MOUSE_RELEASED, x, y, false);
                DockLayoutView.Area area = manager.layout().windows().getFirst().areas().stream()
                        .filter(candidate -> candidate.id().value().equals("editors")).findFirst().orElseThrow();
                assertInstanceOf(DockLayoutView.Group.class, area.root());
                assertEquals(List.of(editor.getId(), notes.getId()), area.groups().getFirst().dockableIds());
            } finally { stage.hide(); }
        });
    }

    @Test
    void aSoleTabDroppedAtItsOwnEdgeIsAnExplicitNoChange() throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 900, 500));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            TextArea content = new TextArea("Unsaved notes");
            Dockable notes = new Dockable(new DockableId("notes"), content);
            notes.setTitle("Notes");
            manager.open(notes, DockTarget.area("editors"));
            stage.show();
            try {
                pane.applyCss();
                pane.layout();
                DockLayoutView before = manager.layout();
                Node header = header(pane, "Notes");
                Bounds from = header.localToScreen(header.getBoundsInLocal());
                Bounds target = content.localToScreen(content.getBoundsInLocal());
                mouse(header, MouseEvent.MOUSE_PRESSED, from.getCenterX(), from.getCenterY(), true);
                mouse(header, MouseEvent.MOUSE_DRAGGED, target.getMaxX() - 8, target.getCenterY(), true);
                Node marker = pane.lookup(".dock-drop-indicator");
                assertTrue(marker.isVisible());
                assertTrue(marker.getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("no-change")));
                mouse(header, MouseEvent.MOUSE_RELEASED, target.getMaxX() - 8, target.getCenterY(), false);
                assertEquals(before, manager.layout());
                assertSame(content, manager.findDockable(notes.getId()).orElseThrow().getContent());
            } finally { stage.hide(); }
        });
    }

    @ParameterizedTest
    @CsvSource({"LEFT,true", "RIGHT,true", "TOP,true", "BOTTOM,true",
            "LEFT,false", "RIGHT,false", "TOP,false", "BOTTOM,false"})
    void aPersistentGroupRemainsAnInvisibleDropTargetAndRestoresItsExtent(Side edge, boolean releaseSpace) throws Exception {
        FxThread.onFx(() -> {
            String property = edge.name().toLowerCase(java.util.Locale.ROOT);
            String fxml = """
                    <?import de.neitzel.fx.docking.*?>
                    <DockPane xmlns:fx="http://javafx.com/fxml/1">
                      <initialLayout><DockLayout>
                        <center><DockGroupDefinition destinationId="editors"/></center>
                        <%s><DockGroupDefinition destinationId="tools" persistent="true"
                          visibleWhenEmpty="false" releaseSpaceWhenEmpty="%s"/></%s>
                      </DockLayout></initialLayout>
                    </DockPane>
                    """.formatted(property, releaseSpace, property);
            DockPane pane = new FXMLLoader().load(new ByteArrayInputStream(fxml.getBytes(StandardCharsets.UTF_8)));
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 900, 500));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            manager.open(new Dockable(new DockableId("project"), new TextArea("Project")), DockTarget.area("editors"));
            Dockable notes = new Dockable(new DockableId("notes"), new TextArea("Notes"));
            notes.setTitle("Notes");
            manager.open(notes, DockTarget.area("tools"));
            stage.show();
            try {
                pane.applyCss();
                pane.layout();
                TabPane empty = pane.lookupAll(".tab-pane").stream().map(TabPane.class::cast)
                        .filter(tabs -> tabs.getTabs().stream().anyMatch(tab -> tab.getContent() == notes.getContent()))
                        .findFirst().orElseThrow();
                double occupiedExtent = edge.isVertical() ? empty.getWidth() : empty.getHeight();
                manager.move(notes.getId(), DockTarget.area("editors"));
                pane.applyCss();
                pane.layout();
                assertFalse(empty.isVisible());
                assertEquals(!releaseSpace, empty.isManaged());
                Node header = header(pane, "Notes");
                Bounds from = header.localToScreen(header.getBoundsInLocal());
                Bounds target = releaseSpace ? pane.localToScreen(pane.getBoundsInLocal())
                        : empty.localToScreen(empty.getBoundsInLocal());
                double x = !releaseSpace ? target.getCenterX() : edge == Side.LEFT ? target.getMinX() + 8
                        : edge == Side.RIGHT ? target.getMaxX() - 8 : target.getCenterX();
                double y = !releaseSpace ? target.getCenterY() : edge == Side.TOP ? target.getMinY() + 8
                        : edge == Side.BOTTOM ? target.getMaxY() - 8 : target.getCenterY();
                mouse(header, MouseEvent.MOUSE_PRESSED, from.getCenterX(), from.getCenterY(), true);
                mouse(header, MouseEvent.MOUSE_DRAGGED, x, y, true);
                assertTrue(pane.lookup(".dock-drop-indicator").isVisible());
                assertFalse(empty.isVisible());
                assertEquals(!releaseSpace, empty.isManaged());
                mouse(header, MouseEvent.MOUSE_RELEASED, x, y, false);
                DockLayoutView.Area tools = manager.layout().windows().getFirst().areas().stream()
                        .filter(area -> area.id().value().equals("tools")).findFirst().orElseThrow();
                assertEquals(List.of(notes.getId()), tools.groups().getFirst().dockableIds());
                assertTrue(empty.isVisible());
                assertTrue(empty.isManaged());
                pane.applyCss();
                pane.layout();
                assertEquals(occupiedExtent, edge.isVertical() ? empty.getWidth() : empty.getHeight(), 1.0);
            } finally { stage.hide(); }
        });
    }

    private static Node header(DockPane pane, String title) {
        return pane.lookupAll(".tab").stream().filter(node -> {
            Node label = node.lookup(".tab-label");
            return label instanceof Label text && title.equals(text.getText());
        }).findFirst().orElseThrow();
    }

    private static void mouse(Node source, javafx.event.EventType<MouseEvent> type,
                              double screenX, double screenY, boolean down) {
        var local = source.screenToLocal(screenX, screenY);
        source.fireEvent(new MouseEvent(type, local.getX(), local.getY(), screenX, screenY,
                MouseButton.PRIMARY, 1, false, false, false, false,
                down, false, false, false, false, false,
                new PickResult(source, local.getX(), local.getY())));
    }
}
