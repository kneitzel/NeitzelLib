package de.neitzel.fx.example.preview;

import static org.junit.jupiter.api.Assertions.*;

import de.neitzel.fx.example.view.DeskController;
import de.neitzel.fx.view.ViewLoader;

import javafx.application.Platform;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

class PreviewProvidersTest {
    @BeforeAll
    static void startToolkit() throws Exception {
        var ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException alreadyRunning) {
            ready.countDown();
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    private static void onFx(Runnable action) throws Exception {
        var result = new CompletableFuture<Void>();
        Platform.runLater(
                () -> {
                    try {
                        action.run();
                        result.complete(null);
                    } catch (Throwable e) {
                        result.completeExceptionally(e);
                    }
                });
        result.get(20, TimeUnit.SECONDS);
    }

    @Test
    void deskSamplesAreFreshAndLoadTheRealDesk() throws Exception {
        onFx(
                () -> {
                    var provider = new DeskPreviewProvider();
                    var first = provider.open("desk");
                    var second = provider.open("desk");
                    assertNotSame(first.model(), second.model());
                    assertNotSame(first.model().getHome(), second.model().getHome());
                    assertNotSame(first.context(), second.context());
                    var view =
                            ViewLoader.load(
                                    DeskController.class.getResource("Desk.fxml"),
                                    first.model(),
                                    first.context());
                    assertNotNull(view.getRoot().lookup("#nameField"));
                    assertEquals("Ada Lovelace", first.model().getName());
                    assertTrue(first.ready().toCompletableFuture().isDone());
                    assertThrows(IllegalArgumentException.class, () -> provider.open("unknown"));
                });
    }

    @Test
    void componentSamplesAndIndependentRowActionHaveRequiredState() throws Exception {
        onFx(
                () -> {
                    var addresses = new AddressPreviewProvider();
                    var home = addresses.open("address");
                    assertNotNull(home.model().getNote());
                    var visit = addresses.open("visit");
                    assertNotNull(visit.model().getVisitedOn());
                    var row = addresses.open("row");
                    assertDoesNotThrow(
                            () ->
                                    row.context()
                                            .getComponent(DeskController.class)
                                            .removeVisit(row.model()));
                    var note = new NotePreviewProvider().open("note");
                    assertEquals("Kept at home", note.model().getText());
                    assertThrows(IllegalArgumentException.class, () -> addresses.open("desk"));
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new NotePreviewProvider().open("desk"));
                });
    }
}
