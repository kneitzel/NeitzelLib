package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PreparedDockableOwnershipTest {
    @BeforeAll static void startJavaFx() throws InterruptedException { FxThread.start(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rejectingAnAlreadyOpenedForeignDockableNeverDiscardsItsLiveResources(boolean restore) throws Exception {
        FxThread.onFx(() -> {
            DockLayout initial = new DockLayout(); initial.setCenter(new DockGroupDefinition("start"));
            DockingManager foreign = RestoreValidationTest.manager(initial);
            var model = new SimpleStringProperty("Live document");
            TextArea editor = new TextArea(); editor.textProperty().bindBidirectional(model);
            Dockable live = new Dockable(new DockableId("a"), editor);
            AtomicInteger discarded = new AtomicInteger(); AtomicInteger closed = new AtomicInteger();
            live.setOnDiscarded(() -> { discarded.incrementAndGet(); editor.textProperty().unbindBidirectional(model); });
            live.setOnClosed(closed::incrementAndGet);
            foreign.open(live, DockTarget.area("start"));
            Node parent = editor.getParent(); var original = foreign.layout();
            DockingManager recipient = RestoreValidationTest.manager(initial);
            DockFailure failure;
            if (restore) {
                failure = assertInstanceOf(RestoreResult.Rejected.class, recipient.restore(RestoreValidationTest.saved(),
                        id -> new DockableResolution.Available(live))).failure();
            } else {
                failure = assertInstanceOf(ActionResult.Rejected.class, recipient.open(live.getId(), () -> live,
                        DockTarget.area("start"))).failure();
            }
            assertEquals(DockFailure.Code.CONTENT_ALREADY_ATTACHED, failure.code());
            assertEquals(0, discarded.get(), "A resolver or factory cannot transfer content already owned by another manager");
            assertEquals(0, closed.get());
            assertSame(original, foreign.layout());
            assertSame(parent, editor.getParent());
            assertSame(live, foreign.findDockable(live.getId()).orElseThrow());
            model.set("Updated after rejection");
            assertEquals("Updated after rejection", editor.getText());
            editor.appendText(" and edited");
            assertEquals(editor.getText(), model.get(), "The live editor retains both directions of its model binding");
            assertTrue(recipient.findDockable(live.getId()).isEmpty());
        });
    }
}
