package de.neitzel.fx.docking;

import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.BoundingBox;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Translates pointer gestures on tab headers into ordinary checked manager moves. */
final class DockDragController {
    record GroupSurface(DockGroupId id, TabPane tabs) { }
    private record Proposal(DockTarget target, Bounds marker) { }
    private record Header(DockableId id, Bounds bounds) { }

    private final DockingManager manager;
    private final DockPane pane;
    private final Scene scene;
    private final Supplier<List<GroupSurface>> surfaces;
    private final Map<String, DockableId> headers = new HashMap<>();
    private final EventHandler<MouseEvent> mouseHandler = this::mouse;
    private final EventHandler<KeyEvent> keyHandler = this::key;
    private DockableId pressed;
    private double startX;
    private double startY;
    private boolean dragging;
    private long nextHeaderId;

    DockDragController(DockingManager manager, DockPane pane, Supplier<List<GroupSurface>> surfaces) {
        this.manager = manager;
        this.pane = pane;
        this.scene = pane.getScene();
        this.surfaces = surfaces;
        pane.addEventFilter(MouseEvent.ANY, mouseHandler);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
    }

    void installTab(Tab tab, DockableId id) {
        String headerId = "dock-tab-" + ++nextHeaderId;
        tab.setId(headerId);
        headers.put(headerId, id);
        Menu destinations = new Menu("Move to");
        ContextMenu menu = new ContextMenu(destinations);
        menu.setOnShowing(event -> populateDestinations(destinations, id));
        tab.setContextMenu(menu);
    }

    private void populateDestinations(Menu menu, DockableId id) {
        menu.getItems().clear();
        for (DockLayoutView.Window window : manager.layout().windows()) {
            for (DockLayoutView.Area area : window.areas()) {
                for (DockLayoutView.Group group : area.groups()) {
                    DockTarget target = DockTarget.group(group.id());
                    MoveCheck check = manager.checkMove(id, target);
                    String selectedTitle = group.selectedDockableId() == null ? "" : manager
                            .findDockable(group.selectedDockableId()).map(Dockable::getTitle).orElse("");
                    String label = group.id().value() + (selectedTitle.isBlank() ? "" : " — " + selectedTitle);
                    if (check instanceof MoveCheck.Rejected rejected) {
                        label += " (" + rejected.failure().message() + ")";
                    } else if (check instanceof MoveCheck.NoChange) {
                        label += " (Already here)";
                    }
                    MenuItem item = new MenuItem(label);
                    item.setDisable(!(check instanceof MoveCheck.Allowed));
                    item.setOnAction(event -> manager.move(id, target));
                    menu.getItems().add(item);
                }
            }
        }
    }

    void dispose() {
        pane.removeEventFilter(MouseEvent.ANY, mouseHandler);
        scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        headers.clear();
        cancel();
    }

    private void mouse(MouseEvent event) {
        if (event.getEventType() == MouseEvent.MOUSE_PRESSED && event.getButton() == MouseButton.PRIMARY) {
            cancel();
            for (Node node = event.getPickResult().getIntersectedNode(); node != null; node = node.getParent()) {
                DockableId id = headers.get(node.getId());
                if (id != null) {
                    pressed = id;
                    startX = event.getScreenX();
                    startY = event.getScreenY();
                    break;
                }
            }
        } else if (event.getEventType() == MouseEvent.MOUSE_DRAGGED && pressed != null) {
            dragging |= Math.hypot(event.getScreenX() - startX, event.getScreenY() - startY) >= 5;
            if (dragging) {
                preview(event.getScreenX(), event.getScreenY());
                event.consume();
            }
        } else if (event.getEventType() == MouseEvent.MOUSE_RELEASED && pressed != null) {
            DockableId id = pressed;
            boolean apply = dragging;
            cancel();
            if (apply) {
                Proposal proposal = proposal(event.getScreenX(), event.getScreenY());
                if (proposal != null) manager.move(id, proposal.target());
                event.consume();
            }
        }
    }

    private void preview(double screenX, double screenY) {
        Proposal proposal = proposal(screenX, screenY);
        if (proposal != null) {
            MoveCheck check = manager.checkMove(pressed, proposal.target());
            if (!(check instanceof MoveCheck.Rejected)) {
                pane.showDropIndicator(proposal.marker(), check instanceof MoveCheck.NoChange);
                return;
            }
        }
        pane.hideDropIndicator();
    }

    private Proposal proposal(double screenX, double screenY) {
        for (GroupSurface group : surfaces.get()) {
            Bounds bounds = group.tabs().localToScreen(group.tabs().getBoundsInLocal());
            if (bounds == null || !bounds.contains(screenX, screenY)) continue;
            Node strip = group.tabs().lookup(".tab-header-area");
            Bounds stripBounds = strip == null ? null : strip.localToScreen(strip.getBoundsInLocal());
            if (stripBounds != null && stripBounds.contains(screenX, screenY)) {
                Proposal insertion = insertion(group, screenX, screenY);
                if (insertion != null) return insertion;
            }
            return new Proposal(DockTarget.group(group.id()), bounds);
        }
        return null;
    }

    private Proposal insertion(GroupSurface group, double x, double y) {
        List<Header> visible = group.tabs().getTabs().stream().map(tab -> {
            Node node = group.tabs().lookup("#" + tab.getId());
            Bounds bounds = node == null ? null : node.localToScreen(node.getBoundsInLocal());
            return bounds == null ? null : new Header((DockableId) tab.getUserData(), bounds);
        }).filter(java.util.Objects::nonNull).toList();
        if (visible.isEmpty()) return null;
        Side side = group.tabs().getSide();
        boolean horizontal = side.isHorizontal();
        double direction = 1;
        if (visible.size() > 1) {
            double difference = center(visible.get(1).bounds(), horizontal)
                    - center(visible.getFirst().bounds(), horizontal);
            if (difference != 0) direction = Math.signum(difference);
        }
        double coordinate = horizontal ? x : y;
        for (Header header : visible) {
            if (coordinate * direction < center(header.bounds(), horizontal) * direction) {
                return new Proposal(DockTarget.group(group.id(), TabPosition.before(header.id())),
                        insertionMarker(header.bounds(), horizontal, direction < 0));
            }
        }
        Header last = visible.getLast();
        return new Proposal(DockTarget.group(group.id(), TabPosition.after(last.id())),
                insertionMarker(last.bounds(), horizontal, direction > 0));
    }

    private static double center(Bounds bounds, boolean horizontal) {
        return horizontal ? bounds.getCenterX() : bounds.getCenterY();
    }

    private static Bounds insertionMarker(Bounds bounds, boolean horizontal, boolean trailing) {
        if (horizontal) {
            double x = trailing ? bounds.getMaxX() : bounds.getMinX();
            return new BoundingBox(x - 1, bounds.getMinY(), 2, bounds.getHeight());
        }
        double y = trailing ? bounds.getMaxY() : bounds.getMinY();
        return new BoundingBox(bounds.getMinX(), y - 1, bounds.getWidth(), 2);
    }

    private void key(KeyEvent event) {
        if (pressed != null && event.getCode() == KeyCode.ESCAPE) {
            cancel();
            event.consume();
        }
    }

    private void cancel() {
        pressed = null;
        dragging = false;
        pane.hideDropIndicator();
    }
}
