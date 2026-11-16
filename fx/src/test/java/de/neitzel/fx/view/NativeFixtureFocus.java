package de.neitzel.fx.view;

import javafx.geometry.Bounds;
import javafx.scene.control.IndexRange;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Establishes the starting desktop state once; never used to repair focus after a tested operation. */
final class NativeFixtureFocus {
    private NativeFixtureFocus() { }

    static void establish(Robot robot, TextArea editor) throws Exception {
        Point original = MouseInfo.getPointerInfo().getLocation();
        AtomicReference<Point> client = new AtomicReference<>();
        AtomicReference<IndexRange> selection = new AtomicReference<>();
        FxThread.onFx(() -> {
            Stage stage = (Stage) editor.getScene().getWindow();
            stage.toFront();
            editor.getScene().getRoot().applyCss();
            editor.getScene().getRoot().layout();
            selection.set(editor.getSelection());
            Bounds bounds = editor.localToScreen(editor.getBoundsInLocal());
            client.set(new Point((int) bounds.getCenterX(), (int) bounds.getCenterY()));
        });
        try {
            robot.mouseMove(client.get().x, client.get().y);
            try { robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); }
            finally { robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            AtomicReference<Boolean> focused = new AtomicReference<>(false);
            do {
                Thread.sleep(25);
                FxThread.onFx(() -> focused.set(editor.getScene().getWindow().isFocused()
                        && editor.getScene().getFocusOwner() == editor));
            } while (!focused.get() && System.nanoTime() < deadline);
            assertTrue(focused.get(), "The initial editor click must establish native window and content focus");
            FxThread.onFx(() -> editor.selectRange(selection.get().getStart(), selection.get().getEnd()));
        } finally {
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseMove(original.x, original.y);
        }
    }
}
