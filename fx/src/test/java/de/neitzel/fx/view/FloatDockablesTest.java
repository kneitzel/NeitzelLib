package de.neitzel.fx.view;

import de.neitzel.fx.docking.ActionResult;
import de.neitzel.fx.docking.DockGroupDefinition;
import de.neitzel.fx.docking.DockPane;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.Dockable;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.DockingManager;
import de.neitzel.fx.docking.DockingOptions;
import de.neitzel.fx.docking.MoveCheck;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.IndexRange;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Window moves retain application content and can be inspected without creating a window. */
class FloatDockablesTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @Test
    void failedWindowCustomizationLeavesLiveContentUntouchedAndDiscardsOnlyFactoryContent() throws Exception {
        FxThread.onFx(() -> {
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 800, 500));
            RuntimeException cause = new IllegalStateException("Window customization failed");
            DockingOptions options = new DockingOptions();
            options.setFloatingWindowCustomizer(configuration -> { throw cause; });
            DockingManager manager = DockingManager.attach(main, pane, options);
            Dockable notes = new Dockable(new DockableId("notes"), new TextArea("Unsaved"));
            manager.open(notes, DockTarget.area("editors"));
            var layout = manager.layout();
            var windows = List.copyOf(Window.getWindows());
            assertInstanceOf(MoveCheck.Allowed.class, manager.checkMove(notes.getId(), DockTarget.floating()));
            var moved = assertInstanceOf(ActionResult.Rejected.class, manager.move(notes.getId(), DockTarget.floating()));
            assertSame(cause, moved.failure().cause());
            assertSame(layout, manager.layout());
            assertSame(main, notes.getContent().getScene().getWindow());
            var discarded = new java.util.concurrent.atomic.AtomicInteger();
            var id = new DockableId("prepared");
            var opened = manager.open(id, () -> {
                Dockable prepared = new Dockable(id, new TextArea("Prepared"));
                prepared.setOnDiscarded(discarded::incrementAndGet);
                return prepared;
            }, DockTarget.floating());
            assertInstanceOf(ActionResult.Rejected.class, opened);
            assertEquals(1, discarded.get());
            assertTrue(manager.findDockable(id).isEmpty());
            assertSame(layout, manager.layout());
            assertEquals(windows, List.copyOf(Window.getWindows()));
        });
    }

    @Test
    void floatingGroupsCanSplitAndRejoinWithoutReplacingOrClosingTheirContents() throws Exception {
        FxThread.onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 800, 500));
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            Dockable first = new Dockable(new DockableId("first"), new TextArea("First unsaved"));
            Dockable second = new Dockable(new DockableId("second"), new TextArea("Second unsaved"));
            var closed = new java.util.concurrent.atomic.AtomicInteger();
            first.setOnClosed(closed::incrementAndGet);
            second.setOnClosed(closed::incrementAndGet);
            main.show();
            try {
                manager.open(first, DockTarget.floating());
                var group = manager.layout().windows().getLast().areas().getFirst().groups().getFirst().id();
                manager.open(second, DockTarget.group(group));
                Window owner = first.getContent().getScene().getWindow();
                assertInstanceOf(ActionResult.Applied.class,
                        manager.move(second.getId(), DockTarget.split(group, javafx.geometry.Side.RIGHT)));
                var area = manager.layout().windows().getLast().areas().getFirst();
                assertInstanceOf(de.neitzel.fx.docking.DockLayoutView.Split.class, area.root());
                assertEquals(2, area.groups().size());
                assertSame(owner, second.getContent().getScene().getWindow());
                assertInstanceOf(ActionResult.Applied.class, manager.move(second.getId(), DockTarget.group(group)));
                var joined = manager.layout().windows().getLast().areas().getFirst();
                assertEquals(1, joined.groups().size());
                assertEquals(List.of(first.getId(), second.getId()), joined.groups().getFirst().dockableIds());
                assertSame(first.getContent(), manager.findDockable(first.getId()).orElseThrow().getContent());
                assertEquals("Second unsaved", ((TextArea) second.getContent()).getText());
                assertEquals(0, closed.get());
            } finally {
                for (Window window : List.copyOf(Window.getWindows())) if (!existing.contains(window)) window.hide();
            }
        });
    }

    @Test
    void invalidFloatingGeometryIsRejectedWithoutAllocatingAWindow() throws Exception {
        FxThread.onFx(() -> {
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 800, 500));
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            Dockable notes = new Dockable(new DockableId("notes"), new TextArea("Unsaved"));
            manager.open(notes, DockTarget.area("editors"));
            var before = manager.layout();
            for (Rectangle2D bounds : List.of(new Rectangle2D(0, 0, 0, 100),
                    new Rectangle2D(Double.NaN, 0, 100, 100),
                    new Rectangle2D(0, 0, Double.POSITIVE_INFINITY, 100))) {
                var rejected = assertInstanceOf(MoveCheck.Rejected.class,
                        manager.checkMove(notes.getId(), DockTarget.floating(bounds)));
                assertEquals(de.neitzel.fx.docking.DockFailure.Code.INVALID_WINDOW_BOUNDS, rejected.failure().code());
                assertInstanceOf(ActionResult.Rejected.class, manager.move(notes.getId(), DockTarget.floating(bounds)));
                assertSame(before, manager.layout());
                assertSame(main, notes.getContent().getScene().getWindow());
            }
        });
    }

    @Test
    void checkedDetachmentAndReturnKeepTheLiveEditorAndSelection() throws Exception {
        FxThread.onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 800, 500));
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            TextArea notes = new TextArea("Edited without saving");
            Dockable editor = new Dockable(new DockableId("notes"), notes);
            editor.setTitle("Notes");
            manager.open(editor, DockTarget.area("editors"));
            main.show();
            try {
                notes.selectRange(2, 8);
                var before = manager.layout();
                int windows = Window.getWindows().size();
                DockTarget floating = DockTarget.floating(new Rectangle2D(150, 150, 520, 360));
                assertInstanceOf(MoveCheck.Allowed.class, manager.checkMove(editor.getId(), floating));
                assertSame(before, manager.layout());
                assertEquals(windows, Window.getWindows().size());
                assertInstanceOf(ActionResult.Applied.class, manager.move(editor.getId(), floating));
                Window detached = notes.getScene().getWindow();
                assertNotSame(main, detached);
                assertTrue(detached.isShowing());
                assertEquals(2, manager.layout().windows().size());
                assertEquals("Edited without saving", notes.getText());
                assertEquals(new IndexRange(2, 8), notes.getSelection());
                assertSame(editor, manager.activeDockable());
                assertSame(notes, manager.findDockable(editor.getId()).orElseThrow().getContent());
                assertInstanceOf(ActionResult.Applied.class, manager.move(editor.getId(), DockTarget.returnToMain()));
                assertSame(main, notes.getScene().getWindow());
                assertFalse(detached.isShowing());
                assertEquals(1, manager.layout().windows().size());
                assertEquals(new IndexRange(2, 8), notes.getSelection());
                assertEquals("Edited without saving", notes.getText());
            } finally {
                for (Window window : List.copyOf(Window.getWindows())) {
                    if (!existing.contains(window)) window.hide();
                }
            }
        });
    }

    @Test
    void mainWindowOnlyContentCannotCreateOrEnterAFloatingWindow() throws Exception {
        FxThread.onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 800, 500));
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            Dockable notes = new Dockable(new DockableId("notes"), new TextArea("Notes"));
            Dockable project = new Dockable(new DockableId("project"), new TextArea("Project"));
            project.setMainWindowOnly(true);
            manager.open(notes, DockTarget.area("editors"));
            manager.open(project, DockTarget.area("editors"));
            main.show();
            try {
                var before = manager.layout();
                assertInstanceOf(MoveCheck.Rejected.class, manager.checkMove(project.getId(), DockTarget.floating()));
                assertInstanceOf(ActionResult.Rejected.class, manager.move(project.getId(), DockTarget.floating()));
                assertSame(before, manager.layout());
                assertEquals(1, manager.layout().windows().size());
                assertInstanceOf(ActionResult.Applied.class, manager.move(notes.getId(), DockTarget.floating()));
                var floatingGroup = manager.layout().windows().stream().filter(window -> !window.main())
                        .findFirst().orElseThrow().areas().getFirst().groups().getFirst();
                var target = DockTarget.group(floatingGroup.id());
                assertInstanceOf(MoveCheck.Rejected.class, manager.checkMove(project.getId(), target));
                assertInstanceOf(ActionResult.Rejected.class, manager.move(project.getId(), target));
                assertSame(main, project.getContent().getScene().getWindow());
                Dockable another = new Dockable(new DockableId("another-project"), new TextArea("Another project"));
                another.setMainWindowOnly(true);
                assertInstanceOf(ActionResult.Rejected.class, manager.open(another, DockTarget.floating()));
                assertTrue(manager.findDockable(another.getId()).isEmpty());
            } finally {
                for (Window window : List.copyOf(Window.getWindows())) {
                    if (!existing.contains(window)) window.hide();
                }
            }
        });
    }

    @Test
    void floatingCustomizationAndLiveContentAreReadyBeforeTheWindowAppears() throws Exception {
        FxThread.onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 800, 500));
            var icon = new javafx.scene.image.WritableImage(1, 1);
            String stylesheet = getClass().getResource("floating-window.css").toExternalForm();
            var customized = new java.util.concurrent.atomic.AtomicInteger();
            DockingOptions options = new DockingOptions();
            options.setFloatingWindowCustomizer(configuration -> {
                customized.incrementAndGet();
                assertTrue(Window.getWindows().stream().allMatch(window -> existing.contains(window) || window == main));
                configuration.setTitle("Floating editor");
                configuration.getIcons().add(icon);
                configuration.getStylesheets().add(stylesheet);
            });
            DockingManager manager = DockingManager.attach(main, pane, options);
            TextArea notes = new TextArea("Edited without saving");
            Dockable editor = new Dockable(new DockableId("notes"), notes);
            manager.open(editor, DockTarget.area("editors"));
            main.show();
            var appearedWithContent = new java.util.concurrent.atomic.AtomicBoolean();
            javafx.collections.ListChangeListener<Window> shown = change -> {
                while (change.next()) {
                    for (Window window : change.getAddedSubList()) {
                        if (window != main && !existing.contains(window)) {
                            appearedWithContent.set(notes.getScene() == window.getScene());
                        }
                    }
                }
            };
            Window.getWindows().addListener(shown);
            try {
                assertInstanceOf(MoveCheck.Allowed.class, manager.checkMove(editor.getId(), DockTarget.floating()));
                assertEquals(0, customized.get());
                assertInstanceOf(ActionResult.Applied.class, manager.move(editor.getId(), DockTarget.floating()));
                Stage floating = (Stage) notes.getScene().getWindow();
                assertEquals(1, customized.get());
                assertEquals("Floating editor", floating.getTitle());
                assertEquals(List.of(icon), floating.getIcons());
                String defaults = DockPane.class.getResource("docking.css").toExternalForm();
                assertEquals(List.of(defaults, stylesheet), floating.getScene().getStylesheets(),
                        "Scoped library defaults precede the application's exact theme stylesheet");
                assertTrue(appearedWithContent.get(), "A floating window must appear with its prepared content");
            } finally {
                Window.getWindows().removeListener(shown);
                for (Window window : List.copyOf(Window.getWindows())) {
                    if (!existing.contains(window)) window.hide();
                }
            }
        });
    }

    @Test
    void returningUsesThePreviousRelativeTabPosition() throws Exception {
        FxThread.onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 800, 500));
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            Dockable notes = new Dockable(new DockableId("notes"), new TextArea("Notes"));
            Dockable readme = new Dockable(new DockableId("readme"), new TextArea("Readme"));
            manager.open(notes, DockTarget.area("editors"));
            manager.open(readme, DockTarget.area("editors"));
            main.show();
            try {
                manager.move(notes.getId(), DockTarget.floating());
                manager.move(notes.getId(), DockTarget.returnToMain());
                assertEquals(List.of(notes.getId(), readme.getId()), manager.layout().windows().getFirst()
                        .areas().getFirst().groups().getFirst().dockableIds());
            } finally {
                for (Window window : List.copyOf(Window.getWindows())) {
                    if (!existing.contains(window)) window.hide();
                }
            }
        });
    }

    @Test
    void returningFindsTheOriginalAreaAfterItsConcreteSplitGroupDisappears() throws Exception {
        FxThread.onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            DockGroupDefinition tools = new DockGroupDefinition("tools");
            tools.setPreferredWidth(400);
            pane.getInitialLayout().setRight(tools);
            Stage main = new Stage();
            main.setScene(new Scene(pane, 1000, 600));
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            Dockable notes = new Dockable(new DockableId("notes"), new TextArea("Notes"));
            Dockable anchor = new Dockable(new DockableId("anchor"), new TextArea("Anchor"));
            manager.open(anchor, DockTarget.area("tools"));
            manager.open(notes, DockTarget.area("tools"));
            main.show();
            try {
                assertInstanceOf(ActionResult.Applied.class, manager.move(notes.getId(),
                        DockTarget.split(new de.neitzel.fx.docking.DockGroupId("tools"), javafx.geometry.Side.BOTTOM)));
                manager.move(notes.getId(), DockTarget.floating());
                assertEquals(1, manager.layout().windows().getFirst().areas().stream()
                        .filter(area -> area.id().value().equals("tools")).findFirst().orElseThrow().groups().size());
                assertInstanceOf(ActionResult.Applied.class, manager.move(notes.getId(), DockTarget.returnToMain()));
                var returned = manager.layout().windows().getFirst().areas().stream()
                        .filter(area -> area.id().value().equals("tools")).findFirst().orElseThrow();
                assertEquals(List.of(anchor.getId(), notes.getId()), returned.groups().getFirst().dockableIds());
            } finally {
                for (Window window : List.copyOf(Window.getWindows())) {
                    if (!existing.contains(window)) window.hide();
                }
            }
        });
    }

    @Test
    void returnUsesTheCapturedFallbackAndKeepsFloatingWhenNeitherTargetAcceptsIt() throws Exception {
        FxThread.onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            pane.getInitialLayout().setLeft(new DockGroupDefinition("return-place"));
            pane.getInitialLayout().setRight(new DockGroupDefinition("tools"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 1000, 600));
            var allowTools = new java.util.concurrent.atomic.AtomicBoolean(true);
            var allowFallback = new java.util.concurrent.atomic.AtomicBoolean(true);
            DockingOptions options = new DockingOptions();
            options.setFallbackTarget(DockTarget.area("return-place"));
            options.setPlacementRule((dockable, destination) -> {
                if (destination instanceof de.neitzel.fx.docking.ResolvedDockTarget.Insertion insertion
                        && ((!allowTools.get() && insertion.groupId().value().equals("tools"))
                        || (!allowFallback.get() && insertion.groupId().value().equals("return-place")))) {
                    return java.util.Optional.of(new de.neitzel.fx.docking.DockFailure(
                            de.neitzel.fx.docking.DockFailure.Code.PLACEMENT_REJECTED, dockable.getId(), "Destination disabled", null));
                }
                return java.util.Optional.empty();
            });
            DockingManager manager = DockingManager.attach(main, pane, options);
            options.setFallbackTarget(DockTarget.area("editors"));
            Dockable notes = new Dockable(new DockableId("notes"), new TextArea("Notes"));
            manager.open(notes, DockTarget.area("tools"));
            main.show();
            try {
                manager.move(notes.getId(), DockTarget.floating());
                allowTools.set(false);
                assertInstanceOf(ActionResult.Applied.class, manager.move(notes.getId(), DockTarget.returnToMain()));
                var fallback = manager.layout().windows().getFirst().areas().stream()
                        .filter(area -> area.id().value().equals("return-place")).findFirst().orElseThrow();
                assertEquals(List.of(notes.getId()), fallback.groups().getFirst().dockableIds());
                manager.move(notes.getId(), DockTarget.floating());
                allowFallback.set(false);
                var before = manager.layout();
                Window floating = notes.getContent().getScene().getWindow();
                assertInstanceOf(MoveCheck.Rejected.class, manager.checkMove(notes.getId(), DockTarget.returnToMain()));
                assertInstanceOf(ActionResult.Rejected.class, manager.move(notes.getId(), DockTarget.returnToMain()));
                assertSame(before, manager.layout());
                assertSame(floating, notes.getContent().getScene().getWindow());
                assertTrue(floating.isShowing());
            } finally {
                for (Window window : List.copyOf(Window.getWindows())) {
                    if (!existing.contains(window)) window.hide();
                }
            }
        });
    }

    @Test
    void returnUsesItsRememberedSideAreaWhenTheRelativeAnchorHasAlsoFloated() throws Exception {
        FxThread.onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows()); Stage main = new Stage();
            try {
                DockPane pane = new DockPane();
                pane.getInitialLayout().setCenter(new DockGroupDefinition("unrelated-editors"));
                pane.getInitialLayout().setRight(new DockGroupDefinition("tools"));
                main.setScene(new Scene(pane, 1000, 600));
                DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
                Dockable notes = new Dockable(new DockableId("notes"), new TextArea("Notes"));
                Dockable anchor = new Dockable(new DockableId("anchor"), new TextArea("Anchor"));
                manager.open(notes, DockTarget.area("tools")); manager.open(anchor, DockTarget.area("tools"));
                main.show();
                manager.move(notes.getId(), DockTarget.floating()); manager.move(anchor.getId(), DockTarget.floating());
                MoveCheck.Allowed checked = assertInstanceOf(MoveCheck.Allowed.class,
                        manager.checkMove(notes.getId(), DockTarget.returnToMain()));
                assertEquals(new de.neitzel.fx.docking.DockGroupId("tools"),
                        assertInstanceOf(de.neitzel.fx.docking.ResolvedDockTarget.Insertion.class, checked.destination()).groupId());
                assertInstanceOf(ActionResult.Applied.class, manager.move(notes.getId(), DockTarget.returnToMain()));
                var originalArea = manager.layout().windows().getFirst().areas().stream()
                        .filter(area -> area.id().value().equals("tools")).findFirst().orElseThrow();
                assertEquals(List.of(notes.getId()), originalArea.groups().getFirst().dockableIds());
                assertSame(main, notes.getContent().getScene().getWindow());
            } finally {
                List.copyOf(Window.getWindows()).stream().filter(window -> !existing.contains(window)).forEach(Window::hide);
                main.hide();
            }
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"never-docked", "removed", "denied"})
    void returnWithoutADesignatedFallbackDoesNotChooseAnUnrelatedMainArea(String previousTarget) throws Exception {
        FxThread.onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            Stage main = new Stage();
            try {
                DockPane pane = new DockPane();
                pane.getInitialLayout().setCenter(new DockGroupDefinition("unrelated-editors"));
                DockGroupDefinition tools = new DockGroupDefinition("tools");
                tools.setPersistent(!previousTarget.equals("removed"));
                pane.getInitialLayout().setRight(tools);
                main.setScene(new Scene(pane, 1000, 600));
                var allowTools = new java.util.concurrent.atomic.AtomicBoolean(true);
                DockingOptions options = new DockingOptions();
                options.setPlacementRule((dockable, destination) -> {
                    if (!allowTools.get() && destination instanceof de.neitzel.fx.docking.ResolvedDockTarget.Insertion insertion
                            && insertion.groupId().value().equals("tools")) {
                        return java.util.Optional.of(new de.neitzel.fx.docking.DockFailure(
                                de.neitzel.fx.docking.DockFailure.Code.PLACEMENT_REJECTED, dockable.getId(),
                                "The remembered tools destination is unavailable", null, "return.tools.denied"));
                    }
                    return java.util.Optional.empty();
                });
                DockingManager manager = DockingManager.attach(main, pane, options);
                Dockable notes = new Dockable(new DockableId("notes"), new TextArea("Unsaved floating notes"));
                assertInstanceOf(ActionResult.Applied.class, manager.open(notes,
                        previousTarget.equals("never-docked") ? DockTarget.floating() : DockTarget.area("tools")));
                if (!previousTarget.equals("never-docked")) {
                    assertInstanceOf(ActionResult.Applied.class, manager.move(notes.getId(), DockTarget.floating()));
                }
                main.show();
                if (previousTarget.equals("denied")) allowTools.set(false);
                var before = manager.layout(); var snapshot = manager.snapshot();
                Window floating = notes.getContent().getScene().getWindow();
                MoveCheck.Rejected checked = assertInstanceOf(MoveCheck.Rejected.class,
                        manager.checkMove(notes.getId(), DockTarget.returnToMain()));
                ActionResult.Rejected result = assertInstanceOf(ActionResult.Rejected.class,
                        manager.move(notes.getId(), DockTarget.returnToMain()));
                assertEquals(checked.failure(), result.failure());
                assertEquals(previousTarget.equals("denied") ? de.neitzel.fx.docking.DockFailure.Code.PLACEMENT_REJECTED
                        : de.neitzel.fx.docking.DockFailure.Code.TARGET_NOT_FOUND, result.failure().code());
                assertEquals(notes.getId(), result.failure().dockableId());
                if (previousTarget.equals("denied")) assertEquals("return.tools.denied", result.failure().messageKey());
                assertSame(before, manager.layout()); assertSame(snapshot, manager.snapshot());
                assertSame(floating, notes.getContent().getScene().getWindow()); assertTrue(floating.isShowing());
                assertEquals("Unsaved floating notes", ((TextArea) notes.getContent()).getText());
            } finally {
                List.copyOf(Window.getWindows()).stream().filter(window -> !existing.contains(window)).forEach(Window::hide);
                main.hide();
            }
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void aMoveCheckFitsRequestedFloatingBoundsToAnAvailableScreen(boolean tinyFrame) throws Exception {
        FxThread.onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage main = new Stage();
            main.setScene(new Scene(pane, 800, 500));
            DockingManager manager = DockingManager.attach(main, pane, new DockingOptions());
            Dockable notes = new Dockable(new DockableId("notes"), new TextArea("Notes"));
            manager.open(notes, DockTarget.area("editors"));
            main.show();
            try {
                Rectangle2D available = javafx.stage.Screen.getPrimary().getVisualBounds();
                Rectangle2D requested = tinyFrame ? new Rectangle2D(available.getMaxX() - 1, available.getMaxY() - 1, 1, 1)
                        : new Rectangle2D(available.getMinX() - 10000, available.getMinY() - 10000,
                        available.getWidth() + 100, available.getHeight() + 100);
                MoveCheck.Allowed allowed = assertInstanceOf(MoveCheck.Allowed.class,
                        manager.checkMove(notes.getId(), DockTarget.floating(requested)));
                var target = assertInstanceOf(de.neitzel.fx.docking.ResolvedDockTarget.Detachment.class, allowed.destination());
                assertEquals(tinyFrame ? requested : available, target.bounds());
                assertInstanceOf(ActionResult.Applied.class, manager.move(notes.getId(), DockTarget.floating(requested)));
                Window floating = notes.getContent().getScene().getWindow();
                assertTrue(floating.getX() >= available.getMinX() - 1);
                assertTrue(floating.getY() >= available.getMinY() - 1);
                assertTrue(floating.getX() + floating.getWidth() <= available.getMaxX() + 1);
                assertTrue(floating.getY() + floating.getHeight() <= available.getMaxY() + 1);
            } finally {
                for (Window window : List.copyOf(Window.getWindows())) {
                    if (!existing.contains(window)) window.hide();
                }
            }
        });
    }
}
