package de.neitzel.fx.view;

import de.neitzel.grid.ColumnWidth;

import javafx.animation.AnimationTimer;
import javafx.event.EventHandler;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;

import java.util.*;

/** Native gesture routing for result-mode headers only. No source/query or storage ownership. */
final class ResultHeaderGestures implements AutoCloseable {
    private static final DataFormat FORMAT = new DataFormat("application/x-neitzel-grid-header");
    private final String owner = UUID.randomUUID().toString();
    private final TableResults<?, ?> renderer;
    private final HBox header;
    private final VBox body;
    private Runnable cancelResize;
    private double pointerX, pointerY;
    private AnimationTimer scrolling;
    private Runnable finishDrag;
    private long dragGeneration;

    ResultHeaderGestures(TableResults<?, ?> renderer, HBox header, VBox body) {
        this.renderer = renderer;
        this.header = header;
        this.body = body;
    }

    void header(HBox cell, Label label, Region handle, String id, Runnable sort) {
        boolean[] dragged = {false};
        cell.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) dragged[0] = false;
        });
        cell.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && !dragged[0]) sort.run();
        });
        label.setOnDragDetected(e -> {
            if (!e.isPrimaryButtonDown() && e.getButton() != MouseButton.PRIMARY) return;
            dragged[0] = true;
            start(label, "column", id);
            e.consume();
        });
        cell.setOnDragOver(e -> {
            var payload = payload(e.getDragboard());
            if (payload == null) return;
            pointerX = e.getSceneX();
            pointerY = e.getSceneY();
            clearTargets();
            int index =
                    insertion(
                            renderer.columnOrder(), payload[1], id, e.getX() > cell.getWidth() / 2);
            var order = move(renderer.columnOrder(), payload[1], index);
            boolean valid = payload[0].equals("column") && valid(order);
            cell.getStyleClass()
                    .add(
                            valid
                                    ? (e.getX() > cell.getWidth() / 2
                                            ? "drop-after"
                                            : "drop-before")
                                    : "drop-invalid");
            if (valid) e.acceptTransferModes(TransferMode.MOVE);
            e.consume();
        });
        cell.setOnDragDropped(e -> {
            var payload = payload(e.getDragboard());
            boolean success = false;
            if (payload != null && payload[0].equals("column")) {
                int index =
                        insertion(
                                renderer.columnOrder(),
                                payload[1],
                                id,
                                e.getX() > cell.getWidth() / 2);
                var order = move(renderer.columnOrder(), payload[1], index);
                if (valid(order)) {
                    renderer.setColumnOrder(order);
                    success = true;
                } else renderer.message("Fixed column cannot be displaced.");
            }
            clearTargets();
            e.setDropCompleted(success);
            e.consume();
        });
        handle.setOnMouseClicked(MouseEvent::consume);
        handle.setOnDragDetected(MouseEvent::consume);
        handle.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            dragged[0] = true;
            if (cancelResize != null) cancelResize.run();
            ColumnWidth original = renderer.columnWidths().get(id);
            double start = e.getScreenX(), width = renderer.width(id);
            var definition =
                    renderer.definitions().stream()
                            .filter(c -> c.id().equals(id))
                            .findFirst()
                            .orElseThrow();
            Scene scene = handle.getScene();
            EventHandler<KeyEvent> escape =
                    key -> {
                        if (key.getCode() == KeyCode.ESCAPE && cancelResize != null) {
                            cancelResize.run();
                            key.consume();
                        }
                    };
            double[] preview = {width};
            boolean[] active = {true};
            javafx.beans.value.ChangeListener<Boolean> focus =
                    (o, b, focused) -> {
                        if (!focused && cancelResize != null) cancelResize.run();
                    };
            var window = scene.getWindow();
            Runnable cleanup =
                    () -> {
                        active[0] = false;
                        scene.removeEventFilter(KeyEvent.KEY_PRESSED, escape);
                        if (window != null) window.focusedProperty().removeListener(focus);
                        handle.setOnMouseDragged(null);
                        handle.setOnMouseReleased(null);
                        cancelResize = null;
                    };
            cancelResize =
                    () -> {
                        renderer.previewWidth(id, original);
                        cleanup.run();
                        renderer.message("Resize cancelled.");
                    };
            scene.addEventFilter(KeyEvent.KEY_PRESSED, escape);
            if (window != null) window.focusedProperty().addListener(focus);
            handle.setOnMouseDragged(event -> {
                if (active[0]) {
                    preview[0] =
                            Math.clamp(
                                    width + event.getScreenX() - start,
                                    definition.minimumWidth(),
                                    definition.maximumWidth());
                    renderer.previewWidth(id, new ColumnWidth.Explicit(preview[0]));
                }
                event.consume();
            });
            handle.setOnMouseReleased(event -> {
                if (active[0]) {
                    cleanup.run();
                    renderer.setColumnWidth(id, new ColumnWidth.Explicit(preview[0]));
                    renderer.message("Width applied to " + definition.header() + ".");
                }
                event.consume();
            });
            e.consume();
        });
    }

    void groupTarget(HBox bar) {
        bar.setOnDragOver(e -> {
            var payload = payload(e.getDragboard());
            if (payload == null) return;
            clearTargets();
            boolean valid =
                    renderer.canGroup(payload[1])
                            && (payload[0].equals("group")
                                    || !renderer.groupingColumns().contains(payload[1]));
            bar.getStyleClass().add(valid ? "drop-before" : "drop-invalid");
            if (valid) {
                e.acceptTransferModes(TransferMode.MOVE);
                markGroupInsertion(bar, payload[1], e.getSceneX());
            }
            e.consume();
        });
        bar.setOnDragDropped(e -> {
            var payload = payload(e.getDragboard());
            boolean success = false;
            if (payload != null
                    && renderer.canGroup(payload[1])
                    && (payload[0].equals("group")
                            || !renderer.groupingColumns().contains(payload[1]))) {
                var order = new ArrayList<>(renderer.groupingColumns());
                order.remove(payload[1]);
                int index = groupIndex(bar, payload[1], e.getSceneX());
                order.add(Math.min(index, order.size()), payload[1]);
                try {
                    renderer.setGroupingColumns(order);
                    success = true;
                } catch (RuntimeException failure) {
                    renderer.message("Grouping failed: " + failure.getMessage());
                }
            }
            if (!success && payload == null)
                renderer.message("Column is already grouped or has no enabled grouping mapping.");
            clearTargets();
            e.setDropCompleted(success);
            e.consume();
        });
        bar.setOnDragExited(e -> {
            clearTargets();
            e.consume();
        });
    }

    void chip(Region chip, String id) {
        chip.setUserData(id);
        chip.setOnDragDetected(e -> {
            if (e.isPrimaryButtonDown() || e.getButton() == MouseButton.PRIMARY) {
                start(chip, "group", id);
                e.consume();
            }
        });
    }

    private void start(Node node, String kind, String id) {
        // Capture before entering native drag setup; snapshot uses the rendering pipeline.
        var ghost = node.snapshot(null, null);
        var board = node.startDragAndDrop(TransferMode.MOVE);
        var content = new ClipboardContent();
        content.put(FORMAT, owner + "\n" + kind + "\n" + id);
        board.setContent(content);
        board.setDragView(ghost);
        if (finishDrag != null) finishDrag.run();
        long generation = ++dragGeneration;
        Scene scene = node.getScene();
        // Header-only tracking leaves a stale edge x when the pointer exits
        // vertically. Follow the whole scene and qualify its current y as well.
        EventHandler<DragEvent> position =
                event -> {
                    if (payload(event.getDragboard()) != null) {
                        pointerX = event.getSceneX();
                        pointerY = event.getSceneY();
                    }
                };
        EventHandler<DragEvent> exited =
                event -> {
                    if (event.getTarget() == scene || event.getTarget() == scene.getRoot()) {
                        pointerX = pointerY = Double.NaN;
                        clearTargets();
                    }
                };
        scene.addEventFilter(DragEvent.DRAG_OVER, position);
        scene.addEventFilter(DragEvent.DRAG_EXITED_TARGET, exited);
        finishDrag =
                () -> {
                    scene.removeEventFilter(DragEvent.DRAG_OVER, position);
                    scene.removeEventFilter(DragEvent.DRAG_EXITED_TARGET, exited);
                    pointerX = pointerY = Double.NaN;
                    clearTargets();
                    stopScroll();
                    finishDrag = null;
                };
        node.setOnDragDone(e -> {
            if (generation != dragGeneration) return;
            if (finishDrag != null) finishDrag.run();
            renderer.message(
                    e.getTransferMode() == null
                            ? "Drag cancelled; layout retained."
                            : "Layout applied.");
        });
        startScroll();
    }

    private String[] payload(Dragboard board) {
        if (!board.hasContent(FORMAT)) return null;
        Object content = board.getContent(FORMAT);
        if (!(content instanceof String text)) return null;
        var parts = text.split("\n", 3);
        return parts.length == 3 && parts[0].equals(owner)
                ? new String[] {parts[1], parts[2]}
                : null;
    }

    private static List<String> move(List<String> current, String id, int index) {
        var result = new ArrayList<>(current);
        result.remove(id);
        result.add(Math.clamp(index, 0, result.size()), id);
        return result;
    }

    private static int insertion(List<String> current, String id, String target, boolean after) {
        if (id.equals(target)) return current.indexOf(id);
        var others = new ArrayList<>(current);
        others.remove(id);
        return others.indexOf(target) + (after ? 1 : 0);
    }

    private boolean valid(List<String> order) {
        if (!new HashSet<>(order).equals(new HashSet<>(renderer.columnOrder()))
                || order.size() != renderer.columnOrder().size()) return false;
        for (var c : renderer.definitions())
            if (c.fixedPosition() >= 0 && !order.get(c.fixedPosition()).equals(c.id()))
                return false;
        return true;
    }

    private int groupIndex(HBox bar, String dragged, double sceneX) {
        int index = 0;
        for (Node node : bar.getChildren())
            if (node.getUserData() instanceof String id && !id.equals(dragged)) {
                var bounds = node.localToScene(node.getBoundsInLocal());
                if (sceneX < bounds.getMinX() + bounds.getWidth() / 2) return index;
                index++;
            }
        return index;
    }

    private void markGroupInsertion(HBox bar, String dragged, double x) {
        var chips =
                bar.getChildren().stream()
                        .filter(n -> n.getUserData() instanceof String id && !id.equals(dragged))
                        .toList();
        int index = groupIndex(bar, dragged, x);
        if (!chips.isEmpty())
            chips.get(Math.min(index, chips.size() - 1))
                    .getStyleClass()
                    .add(index < chips.size() ? "drop-before" : "drop-after");
    }

    private void clearTargets() {
        Parent root = header.getScene() == null ? header : header.getScene().getRoot();
        for (String style : List.of("drop-before", "drop-after", "drop-invalid"))
            root.lookupAll("." + style).forEach(n -> n.getStyleClass().remove(style));
    }

    private void startScroll() {
        stopScroll();
        pointerX = pointerY = Double.NaN;
        scrolling =
                new AnimationTimer() {
                    long last;

                    @Override
                    public void handle(long now) {
                        if (now - last < 35_000_000) return;
                        last = now;
                        ScrollPane scroll = null;
                        for (Node node = body.getParent(); node != null; node = node.getParent())
                            if (node instanceof ScrollPane pane) {
                                scroll = pane;
                                break;
                            }
                        ScrollPane headerViewport = null;
                        for (Node node = header.getParent(); node != null; node = node.getParent())
                            if (node instanceof ScrollPane pane) {
                                headerViewport = pane;
                                break;
                            }
                        if (scroll == null
                                || headerViewport == null
                                || Double.isNaN(pointerX)
                                || !headerViewport
                                        .localToScene(headerViewport.getBoundsInLocal())
                                        .contains(pointerX, pointerY)) return;
                        var bounds = scroll.localToScene(scroll.getViewportBounds());
                        double delta =
                                pointerX < bounds.getMinX() + 30
                                        ? -.012
                                        : pointerX > bounds.getMaxX() - 30 ? .012 : 0;
                        scroll.setHvalue(Math.clamp(scroll.getHvalue() + delta, 0, 1));
                    }
                };
        scrolling.start();
    }

    private void stopScroll() {
        if (scrolling != null) {
            scrolling.stop();
            scrolling = null;
        }
    }

    @Override
    public void close() {
        if (cancelResize != null) cancelResize.run();
        if (finishDrag != null) finishDrag.run();
        stopScroll();
    }
}
