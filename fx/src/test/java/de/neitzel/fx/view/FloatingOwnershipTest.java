package de.neitzel.fx.view;

import de.neitzel.fx.docking.ActionResult;
import de.neitzel.fx.docking.DockAreaId;
import de.neitzel.fx.docking.DockFailure;
import de.neitzel.fx.docking.DockGroupDefinition;
import de.neitzel.fx.docking.DockGroupId;
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
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Manager ownership and destination identity remain stable when windows and groups change. */
class FloatingOwnershipTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @Test
    void aFloatingWindowCannotBeAttachedToASecondManager() throws Exception {
        FxThread.onFx(() -> {
            try (Fixture fixture = fixture("editors", null)) {
                Dockable notes = fixture.open("notes", "editors");
                assertInstanceOf(ActionResult.Applied.class, fixture.manager.move(notes.getId(), DockTarget.floating()));
                Stage floating = (Stage) notes.getContent().getScene().getWindow();
                DockPane floatingPane = assertInstanceOf(DockPane.class, floating.getScene().getRoot());
                DockLayoutView before = fixture.manager.layout();
                assertThrows(IllegalStateException.class,
                        () -> DockingManager.attach(floating, floatingPane, new DockingOptions()));
                assertSame(before, fixture.manager.layout());
                assertSame(floating, notes.getContent().getScene().getWindow());
                assertSame(notes, fixture.manager.findDockable(notes.getId()).orElseThrow());
                assertInstanceOf(ActionResult.Applied.class,
                        fixture.manager.move(notes.getId(), DockTarget.returnToMain()));
                assertSame(fixture.main, notes.getContent().getScene().getWindow());
            }
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aRetiredConcreteGroupIdNeverTargetsANewFloatingOrSplitGroup(boolean floating) throws Exception {
        FxThread.onFx(() -> {
            DockGroupDefinition temporary = new DockGroupDefinition("group-1");
            temporary.setPersistent(false);
            try (Fixture fixture = fixture("editors", temporary)) {
                Dockable anchor = fixture.open("anchor", "editors");
                Dockable notes = fixture.open("notes", "group-1");
                DockGroupId retired = new DockGroupId("group-1");
                assertInstanceOf(ActionResult.Applied.class,
                        fixture.manager.move(notes.getId(), DockTarget.area("editors")));
                assertMissingGroup(fixture.manager, anchor.getId(), retired);

                DockTarget destination = floating ? DockTarget.floating()
                        : DockTarget.split(new DockGroupId("editors"), Side.RIGHT);
                assertInstanceOf(ActionResult.Applied.class, fixture.manager.move(notes.getId(), destination));
                DockGroupId allocated = fixture.manager.layout().windows().stream()
                        .flatMap(window -> window.areas().stream()).flatMap(area -> area.groups().stream())
                        .filter(group -> group.dockableIds().contains(notes.getId())).findFirst().orElseThrow().id();
                assertNotEquals(retired, allocated, "A stale concrete destination must not acquire a new meaning");
                assertMissingGroup(fixture.manager, anchor.getId(), retired);
            }
        });
    }

    @Test
    void aGeneratedFloatingAreaDoesNotShadowAnInitialAreaWithTheSameName() throws Exception {
        FxThread.onFx(() -> {
            try (Fixture fixture = fixture("floating-1", null)) {
                Dockable notes = fixture.open("notes", "floating-1");
                assertInstanceOf(ActionResult.Applied.class, fixture.manager.move(notes.getId(), DockTarget.floating()));
                DockLayoutView.Window floating = fixture.manager.layout().windows().stream()
                        .filter(window -> !window.main()).findFirst().orElseThrow();
                assertNotEquals(new DockAreaId("floating-1"), floating.areas().getFirst().id());
                Dockable readme = fixture.open("readme", "floating-1");
                assertSame(fixture.main, readme.getContent().getScene().getWindow(),
                        "The application's original area target must still select the main window");
            }
        });
    }

    private static void assertMissingGroup(DockingManager manager, DockableId id, DockGroupId group) {
        MoveCheck.Rejected rejected = assertInstanceOf(MoveCheck.Rejected.class,
                manager.checkMove(id, DockTarget.group(group)));
        assertEquals(DockFailure.Code.TARGET_NOT_FOUND, rejected.failure().code());
    }

    private static Fixture fixture(String centerId, DockGroupDefinition right) {
        List<Window> existing = List.copyOf(Window.getWindows());
        DockPane pane = new DockPane();
        pane.getInitialLayout().setCenter(new DockGroupDefinition(centerId));
        pane.getInitialLayout().setRight(right);
        Stage main = new Stage();
        main.setScene(new Scene(pane, 900, 600));
        DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
        main.show();
        return new Fixture(main, manager, existing);
    }

    private record Fixture(Stage main, DockingManager manager, List<Window> existing) implements AutoCloseable {
        Dockable open(String id, String area) {
            Dockable dockable = new Dockable(new DockableId(id), new TextArea(id));
            assertInstanceOf(ActionResult.Applied.class, manager.open(dockable, DockTarget.area(area)));
            return dockable;
        }

        @Override public void close() {
            for (Window window : List.copyOf(Window.getWindows())) {
                if (!existing.contains(window)) window.hide();
            }
        }
    }
}
