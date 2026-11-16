package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Applications open and observe live content without MVVM. */
class OpenDockablesTest {
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        FxThread.start();
    }

    @Test
    void opensExistingContentInANamedAreaBeforeShowingTheWindow() throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            TextArea editor = new TextArea("Unsaved text");
            Dockable document = new Dockable(new DockableId("notes"), editor);
            assertInstanceOf(ActionResult.Applied.class, manager.open(document, DockTarget.area("editors")));
            assertSame(document, manager.findDockable(document.getId()).orElseThrow());
            assertSame(document, manager.activeDockable());
            assertEquals(List.of(document.getId()), manager.layout().windows().getFirst().areas().getFirst()
                    .groups().getFirst().dockableIds());
            pane.applyCss();
            pane.layout();
            assertSame(pane.getScene(), editor.getScene());
            assertFalse(stage.isShowing());
        });
    }
    @Test
    void selectingATabPublishesTheCompletedSelectionAndActiveContent() throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            Dockable first = new Dockable(new DockableId("first"), new TextArea("one"));
            Dockable second = new Dockable(new DockableId("second"), new TextArea("two"));
            manager.open(first, DockTarget.area("editors"));
            manager.open(second, DockTarget.area("editors"));
            var changes = new java.util.ArrayList<DockLayoutView>();
            manager.layoutProperty().addListener((observable, old, current) -> changes.add(current));
            ((javafx.scene.control.TabPane) pane.lookup(".tab-pane")).getSelectionModel().selectFirst();
            assertSame(first, manager.activeDockable());
            assertEquals(1, changes.size());
            assertEquals(first.getId(), changes.getFirst().windows().getFirst().areas().getFirst()
                    .groups().getFirst().selectedDockableId());
        });
    }
    @Test
    void standardFxmlAndJavaHaveTheSameCapturedLayout() throws Exception {
        FxThread.onFx(() -> {
            DockPane fxmlPane = javafx.fxml.FXMLLoader.load(getClass().getResource("docking-initial.fxml"));
            Stage fxmlStage = new Stage();
            fxmlStage.setScene(new Scene(fxmlPane));
            DockingManager fxml = DockingManager.attach(fxmlStage, fxmlPane, new DockingOptions());
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            DockGroupDefinition tools = new DockGroupDefinition("tools");
            tools.setTabSide(javafx.geometry.Side.RIGHT);
            tools.setPreferredWidth(200);
            initial.setRight(tools);
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            assertEquals(fxml.layout(), manager.layout());
            initial.setCenter(new DockGroupDefinition("replacement"));
            tools.setDestinationId("changed");
            tools.setTabSide(javafx.geometry.Side.LEFT);
            assertEquals(fxml.layout(), manager.layout());
            assertThrows(UnsupportedOperationException.class, () -> manager.layout().windows().clear());
            assertThrows(UnsupportedOperationException.class, () -> manager.layout().windows().getFirst().areas().clear());
            Dockable project = new Dockable(new DockableId("project"), new TextArea());
            assertInstanceOf(ActionResult.Applied.class, manager.open(project, DockTarget.area("tools")));
        });
    }
    @Test
    void invalidDefinitionsLeaveThePaneAvailableForACorrectedAttach() throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            initial.setLeft(new DockGroupDefinition("editors"));
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane));
            assertThrows(IllegalArgumentException.class, () -> DockingManager.attach(stage, pane, new DockingOptions()));
            assertNull(pane.lookup(".tab-pane"));
            initial.getLeft().setDestinationId("tools");
            assertNotNull(DockingManager.attach(stage, pane, new DockingOptions()));
        });
    }
    @Test
    void activationUsesTheContentFocusTargetAndKeepsItAcrossGlobalActions() throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            javafx.scene.control.TextField first = new javafx.scene.control.TextField();
            javafx.scene.control.TextField second = new javafx.scene.control.TextField();
            Dockable document = new Dockable(new DockableId("document"), new javafx.scene.layout.VBox(first, second));
            document.setFocusTarget(() -> second);
            javafx.scene.control.Button global = new javafx.scene.control.Button("Global action");
            javafx.scene.layout.BorderPane shell = new javafx.scene.layout.BorderPane(pane);
            shell.setTop(global);
            Stage stage = new Stage();
            stage.setScene(new Scene(shell, 500, 300));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            manager.open(document, DockTarget.area("editors"));
            pane.applyCss();
            pane.layout();
            manager.activate(document.getId());
            assertSame(second, stage.getScene().getFocusOwner());
            global.requestFocus();
            assertSame(document, manager.activeDockable());
            assertInstanceOf(ActionResult.Applied.class, manager.activate(document.getId()));
            assertSame(second, stage.getScene().getFocusOwner());
        });
    }
    @Test
    void actualContentFocusActivatesAndIsRememberedForTheNextActivation() throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            initial.setRight(new DockGroupDefinition("tools"));
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            javafx.scene.control.TextField first = new javafx.scene.control.TextField();
            javafx.scene.control.TextField second = new javafx.scene.control.TextField();
            Dockable document = new Dockable(new DockableId("document"), new javafx.scene.layout.VBox(first, second));
            javafx.scene.control.Button global = new javafx.scene.control.Button("Global");
            javafx.scene.layout.BorderPane shell = new javafx.scene.layout.BorderPane(pane);
            shell.setTop(global);
            Stage stage = new Stage();
            stage.setScene(new Scene(shell, 500, 300));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            manager.open(document, DockTarget.area("editors"));
            manager.open(new Dockable(new DockableId("tool"), new TextArea()), DockTarget.area("tools"));
            first.requestFocus();
            assertSame(document, manager.activeDockable());
            global.requestFocus();
            manager.activate(document.getId());
            assertSame(first, stage.getScene().getFocusOwner());
        });
    }
    @Test
    void duplicateOpenRetainsContentAndBindingWithoutMovingIt() throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            initial.setRight(new DockGroupDefinition("tools"));
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            Dockable original = new Dockable(new DockableId("notes"), new TextArea("Unsaved"));
            var title = new javafx.beans.property.SimpleStringProperty("Notes");
            original.titleProperty().bind(title);
            var icon = new javafx.scene.control.Label("N");
            original.setIcon(icon);
            manager.open(original, DockTarget.area("editors"));
            Dockable replacement = new Dockable(original.getId(), new TextArea("Replacement"));
            assertInstanceOf(ActionResult.NoChange.class, manager.open(replacement, DockTarget.area("tools")));
            assertSame(original, manager.findDockable(original.getId()).orElseThrow());
            assertNull(replacement.getContent().getParent());
            assertTrue(manager.layout().windows().getFirst().areas().getLast().groups().getFirst().dockableIds().isEmpty());
            title.set("Renamed");
            javafx.scene.control.TabPane tabs = pane.lookupAll(".tab-pane").stream()
                    .map(javafx.scene.control.TabPane.class::cast).filter(tabPane -> !tabPane.getTabs().isEmpty()).findFirst().orElseThrow();
            assertEquals("Renamed", tabs.getTabs().getFirst().getText());
            assertSame(icon, tabs.getTabs().getFirst().getGraphic());
            title.set("");
            assertEquals("", tabs.getTabs().getFirst().getText());
            assertSame(icon, tabs.getTabs().getFirst().getGraphic());
            assertInstanceOf(ActionResult.Rejected.class, manager.activate(new DockableId("missing")));
        });
    }

    @Test
    void attachRequiresTheMatchingSceneAndExclusivePaneAndStage() throws Exception {
        FxThread.onFx(() -> {
            DockPane pane = new DockPane();
            DockPane other = new DockPane();
            Stage stage = new Stage();
            stage.setScene(new Scene(new javafx.scene.layout.HBox(pane, other)));
            Stage wrongStage = new Stage();
            wrongStage.setScene(new Scene(new javafx.scene.layout.Pane()));
            assertThrows(IllegalArgumentException.class, () -> DockingManager.attach(wrongStage, pane, new DockingOptions()));
            DockingManager.attach(stage, pane, new DockingOptions());
            assertThrows(IllegalStateException.class, () -> DockingManager.attach(stage, pane, new DockingOptions()));
            assertThrows(IllegalStateException.class, () -> DockingManager.attach(stage, other, new DockingOptions()));
        });
    }
}
