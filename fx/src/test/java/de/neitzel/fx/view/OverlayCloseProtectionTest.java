package de.neitzel.fx.view;

import de.neitzel.fx.docking.ActionResult;
import de.neitzel.fx.docking.CloseConsent;
import de.neitzel.fx.docking.CloseResult;
import de.neitzel.fx.docking.DockFailure;
import de.neitzel.fx.docking.DockGroupDefinition;
import de.neitzel.fx.docking.DockGroupId;
import de.neitzel.fx.docking.DockGroupMode;
import de.neitzel.fx.docking.DockLayout;
import de.neitzel.fx.docking.DockLayoutView;
import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import de.neitzel.fx.docking.MoveCheck;
import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** An expanded empty destination must not bypass another overlay's pending-close protection. */
class OverlayCloseProtectionTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    static Stream<Arguments> operationsAndEmptyPresentation() {
        return Stream.of(false, true).flatMap(release -> Stream.of("check", "move", "open")
                .map(operation -> Arguments.of(release, operation)));
    }

    @ParameterizedTest(name = "release empty space={0}, operation={1}")
    @MethodSource("operationsAndEmptyPresentation")
    void anExpandedEmptyOverlayCannotHideAGroupAwaitingCloseConsent(boolean releaseEmptySpace,
                                                                   String operation) throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout();
            initial.setCenter(new DockGroupDefinition("editors"));
            initial.setLeft(overlay("protected", Side.LEFT));
            DockGroupDefinition empty = overlay("empty", Side.RIGHT);
            empty.setVisibleWhenEmpty(true);
            empty.setReleaseSpaceWhenEmpty(releaseEmptySpace);
            initial.setRight(empty);
            DockPane pane = new DockPane();
            pane.setInitialLayout(initial);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 900, 540));
            DockingManager manager = DockingManager.attach(stage, pane, new DockingOptions());
            Dockable editor = new Dockable(new DockableId("editor"), new TextArea("Editor"));
            Dockable protectedTab = new Dockable(new DockableId("protected"), new TextArea("Unsaved protected text"));
            manager.open(editor, DockTarget.area("editors"));
            assertInstanceOf(ActionResult.Applied.class,
                    manager.setGroupCollapsed(new DockGroupId("empty"), false));
            manager.open(protectedTab, DockTarget.area("protected"));
            stage.show();
            var answer = new CompletableFuture<CloseConsent>();
            protectedTab.setCloseCheck(request -> answer);
            var closing = manager.requestTabClose(protectedTab.getId());
            try {
                DockLayoutView before = manager.layout();
                if (releaseEmptySpace) {
                    assertFalse(group(manager, "empty").collapsed(), "The hidden empty group remains explicitly expanded");
                }
                assertTrue(group(manager, "empty").dockableIds().isEmpty());
                assertFalse(group(manager, "protected").collapsed());
                Dockable newcomer = new Dockable(new DockableId("new"), new TextArea("New content"));
                DockTarget target = DockTarget.area("empty");
                Object result = switch (operation) {
                    case "check" -> manager.checkMove(editor.getId(), target);
                    case "move" -> manager.move(editor.getId(), target);
                    case "open" -> manager.open(newcomer, target);
                    default -> throw new AssertionError("Unknown operation");
                };
                DockFailure failure = operation.equals("check")
                        ? assertInstanceOf(MoveCheck.Rejected.class, result).failure()
                        : assertInstanceOf(ActionResult.Rejected.class, result).failure();
                assertEquals(DockFailure.Code.CLOSE_IN_PROGRESS, failure.code());
                assertSame(before, manager.layout());
                assertFalse(group(manager, "protected").collapsed());
                assertTrue(group(manager, "empty").dockableIds().isEmpty());
                assertTrue(manager.findDockable(newcomer.getId()).isEmpty());
                assertNull(newcomer.getContent().getParent());
                assertEquals(java.util.List.of(editor.getId()), group(manager, "editors").dockableIds());
                TabPane protectedTabs = pane.lookupAll(".tab-pane").stream().map(TabPane.class::cast)
                        .filter(tabs -> tabs.getTabs().stream().anyMatch(tab -> tab.getContent() == protectedTab.getContent()))
                        .findFirst().orElseThrow();
                assertTrue(protectedTabs.lookup(".tab-content-area").isVisible());
                assertFalse(closing.toCompletableFuture().isDone());
            } finally {
                answer.complete(CloseConsent.VETO);
                stage.hide();
            }
            assertInstanceOf(CloseResult.Vetoed.class, closing.toCompletableFuture().getNow(null));
            assertNull(manager.closingScopeProperty().get());
        });
    }

    private static DockGroupDefinition overlay(String id, Side side) {
        DockGroupDefinition definition = new DockGroupDefinition(id);
        definition.setMode(DockGroupMode.OVERLAY);
        definition.setCollapsible(true);
        definition.setTabSide(side);
        definition.setPreferredWidth(240);
        return definition;
    }

    private static DockLayoutView.Group group(DockingManager manager, String name) {
        return manager.layout().windows().getFirst().areas().stream()
                .filter(area -> area.id().value().equals(name)).findFirst().orElseThrow().groups().getFirst();
    }
}
