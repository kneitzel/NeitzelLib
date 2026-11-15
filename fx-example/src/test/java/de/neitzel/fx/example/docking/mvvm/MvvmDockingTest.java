package de.neitzel.fx.example.docking.mvvm;

import de.neitzel.fx.docking.ActionResult;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.CloseResult;
import javafx.application.Platform;
import javafx.beans.property.Property;
import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** The example's loaded FXML and public docking operations are the integration boundary. */
class MvvmDockingTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); }
        catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        onFx(() -> Platform.setImplicitExit(false));
    }

    @Test
    void closeAndDiscardReleaseApplicationBindingsWithoutClaimingMvvmCacheDisposal() throws Exception {
        onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            Stage main = new Stage();
            try (MvvmDockingWorkspace workspace = new MvvmDockingWorkspace(main)) {
                main.show();
                MvvmEditor editor = workspace.getEditor();
                set(editor.getView().property("text"), "Unsaved before close");
                assertInstanceOf(CloseResult.Closed.class,
                        workspace.getManager().requestTabClose(editor.getDockable().getId()).toCompletableFuture().join());
                assertFalse(editor.getDockable().titleProperty().isBound(), "Actual close releases application title binding");
                Label status = (Label) editor.getView().getRoot().lookup("#localMessage");
                assertEquals("Released application listeners", status.getText());
                String title = editor.getDockable().getTitle();
                set(editor.getView().property("text"), "The retained ViewModel is still accessible");
                assertEquals(title, editor.getDockable().getTitle());
                assertEquals("Released application listeners", status.getText());

                AtomicReference<MvvmEditor> prepared = new AtomicReference<>();
                assertInstanceOf(ActionResult.Rejected.class, workspace.getManager().open(new DockableId("unused"), () -> {
                    MvvmEditor unused = new MvvmEditor("unused");
                    prepared.set(unused);
                    return unused.getDockable();
                }, DockTarget.area("missing-area")));
                assertTrue(workspace.getManager().findDockable(new DockableId("unused")).isEmpty());
                assertFalse(prepared.get().getDockable().titleProperty().isBound(), "Rejected factory content uses onDiscarded");
                Label discardedStatus = (Label) prepared.get().getView().getRoot().lookup("#localMessage");
                assertEquals("Released application listeners", discardedStatus.getText());
                set(prepared.get().getView().property("text"), "After discard");
                assertEquals("Released application listeners", discardedStatus.getText());
            } finally {
                List.copyOf(Window.getWindows()).stream().filter(window -> !existing.contains(window)).forEach(Window::hide);
            }
        });
    }

    @Test
    void aGlobalActionKeepsItsActiveEditorWhenFocusMovesToTheMainToolbar() throws Exception {
        onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            Stage main = new Stage();
            try (MvvmDockingWorkspace workspace = new MvvmDockingWorkspace(main)) {
                main.show();
                main.getScene().getRoot().applyCss();
                main.getScene().getRoot().layout();
                Button save = (Button) main.getScene().getRoot().lookup("#saveActive");
                Label active = (Label) main.getScene().getRoot().lookup("#activeStatus");
                assertNotNull(save, "The global toolbar exposes an active-editor action");
                assertNotNull(active);
                assertTrue(save.isDisabled(), "An unchanged document needs no checkpoint");
                MvvmEditor editor = workspace.getEditor();
                TextArea plain = (TextArea) workspace.getPlain().getContent();
                String unchangedPlain = plain.getText();
                set(editor.getView().property("text"), "Changed while floating");
                assertFalse(save.isDisabled());
                workspace.getManager().move(editor.getDockable().getId(), DockTarget.floating());
                main.toFront();
                main.requestFocus();
                save.requestFocus();
                assertSame(save, main.getScene().getFocusOwner());
                assertSame(editor.getDockable(), workspace.getManager().activeDockable());
                assertTrue(active.getText().contains("Notes *"));
                save.fire();
                assertTrue(save.isDisabled());
                assertEquals("Notes", editor.getDockable().getTitle());
                assertEquals("Changed while floating", editor.getModel().getText());
                assertEquals(unchangedPlain, plain.getText());
                workspace.getManager().activate(workspace.getPlain().getId());
                assertTrue(save.isDisabled(), "Plain content has no MVVM checkpoint command");
                assertTrue(active.getText().contains("Plain editor"));
                set(editor.getView().property("text"), "Changed again");
                assertTrue(save.isDisabled(), "Changes in an inactive document do not steal the action target");
                workspace.getManager().activate(editor.getDockable().getId());
                assertFalse(save.isDisabled());
            } finally {
                List.copyOf(Window.getWindows()).stream().filter(window -> !existing.contains(window)).forEach(Window::hide);
            }
        });
    }

    @Test
    void theSameLoadedEditorAndItsBarsKeepBidirectionalBindingsAcrossEveryMove() throws Exception {
        onFx(() -> {
            List<Window> existing = List.copyOf(Window.getWindows());
            Stage main = new Stage();
            try (MvvmDockingWorkspace workspace = new MvvmDockingWorkspace(main)) {
                main.show();
                MvvmEditor editor = workspace.getEditor();
                var loaded = editor.getView();
                var model = editor.getModel();
                var viewModel = loaded.getViewModel();
                TextArea text = (TextArea) loaded.getRoot().lookup("#text");
                Node localTools = loaded.getRoot().lookup("#localTools");
                Node localStatus = loaded.getRoot().lookup("#localStatus");
                Node globalTools = main.getScene().getRoot().lookup("#globalTools");
                Node globalStatus = main.getScene().getRoot().lookup("#globalStatus");
                assertNotNull(localTools);
                assertNotNull(localStatus);
                assertNotNull(globalTools);
                assertNotNull(globalStatus);
                text.setText("Unsaved edits");
                assertEquals("Unsaved edits", model.getText());
                int step = 0;
                for (DockTarget target : List.of(DockTarget.area("tools"), DockTarget.floating(), DockTarget.returnToMain())) {
                    assertInstanceOf(ActionResult.Applied.class, workspace.getManager().move(editor.getDockable().getId(), target));
                    assertSame(loaded.getRoot(), editor.getDockable().getContent());
                    assertSame(viewModel, editor.getView().getViewModel());
                    assertSame(model, editor.getModel());
                    assertSame(text.getScene(), localTools.getScene());
                    assertSame(text.getScene(), localStatus.getScene());
                    assertSame(main.getScene(), globalTools.getScene());
                    assertSame(main.getScene(), globalStatus.getScene());
                    assertEquals("Unsaved edits", text.getText());
                    set(loaded.property("text"), "From model " + ++step);
                    assertEquals("From model " + step, text.getText());
                    assertEquals(text.getText(), model.getText());
                    text.setText("Unsaved edits");
                    assertEquals("Unsaved edits", loaded.property("text").getValue());
                    assertTrue(editor.getDockable().getTitle().endsWith("*"));
                }
                assertSame(main, text.getScene().getWindow());
            } finally {
                List.copyOf(Window.getWindows()).stream().filter(window -> !existing.contains(window)).forEach(Window::hide);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void set(Property<?> property, String value) { ((Property<String>) property).setValue(value); }

    private static void onFx(CheckedAction action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try { action.run(); }
            catch (Throwable thrown) { failure.set(thrown); }
            finally { done.countDown(); }
        });
        assertTrue(done.await(15, TimeUnit.SECONDS), "JavaFX operation completed");
        if (failure.get() != null) fail("MVVM docking integration failed", failure.get());
    }

    @FunctionalInterface
    private interface CheckedAction { void run() throws Exception; }
}
