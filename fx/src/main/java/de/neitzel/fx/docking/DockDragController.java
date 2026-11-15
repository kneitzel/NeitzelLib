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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Translates pointer gestures on tab headers into ordinary checked manager moves. */
final class DockDragController {
    record GroupSurface(DockGroupId id, TabPane tabs, Side edge) {
        GroupSurface(DockGroupId id, TabPane tabs) { this(id, tabs, null); }
    }
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
        Menu splits = new Menu("Split");
        MenuItem close = new MenuItem("Close");
        close.setOnAction(event -> manager.requestTabClose(id));
        ContextMenu menu = new ContextMenu(destinations, splits, close);
        menu.setOnShowing(event -> {
            populateDestinations(destinations, id);
            populateSplits(splits, id);
        });
        tab.setContextMenu(menu);
    }

    void uninstallTab(Tab tab) {
        DockableId removed = headers.remove(tab.getId());
        if (removed != null && removed.equals(pressed)) cancel();
        if (tab.getContextMenu() != null) {
            tab.getContextMenu().hide();
            tab.getContextMenu().setOnShowing(null);
            tab.setContextMenu(null);
        }
    }

    private void populateDestinations(Menu menu, DockableId id) {
        menu.getItems().clear();
        var visited = new HashSet<DockGroupId>();
        for (DockLayoutView.Window window : manager.layout().windows()) {
            for (DockLayoutView.Area area : window.areas()) {
                for (DockLayoutView.Group group : area.groups()) {
                    if (!visited.add(group.id())) continue;
                    DockTarget target = DockTarget.group(group.id());
                    String selectedTitle = group.selectedDockableId() == null ? "" : manager
                            .findDockable(group.selectedDockableId()).map(Dockable::getTitle).orElse("");
                    String label = group.id().value() + (selectedTitle.isBlank() ? "" : " — " + selectedTitle);
                    menu.getItems().add(moveItem(label, id, target));
                }
            }
        }
    }

    private void populateSplits(Menu menu, DockableId id) {
        menu.getItems().clear();
        manager.layout().windows().stream().flatMap(window -> window.areas().stream())
                .flatMap(area -> area.groups().stream()).filter(group -> group.dockableIds().contains(id))
                .findFirst().ifPresent(group -> {
                    for (Side side : Side.values()) {
                        String label = switch (side) {
                            case TOP -> "Above";
                            case RIGHT -> "Right";
                            case BOTTOM -> "Below";
                            case LEFT -> "Left";
                        };
                        menu.getItems().add(moveItem(label, id, DockTarget.split(group.id(), side)));
                    }
                });
        menu.setDisable(menu.getItems().isEmpty());
    }

    private MenuItem moveItem(String label, DockableId id, DockTarget target) {
        MoveCheck check = manager.checkMove(id, target);
        if (check instanceof MoveCheck.Rejected rejected) label += " (" + rejected.failure().message() + ")";
        else if (check instanceof MoveCheck.NoChange) label += " (Already here)";
        MenuItem item = new MenuItem(label);
        item.setDisable(!(check instanceof MoveCheck.Allowed));
        item.setOnAction(event -> manager.move(id, target));
        return item;
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
                Proposal proposal = proposal(id, event.getScreenX(), event.getScreenY());
                if (proposal != null) manager.move(id, proposal.target());
                event.consume();
            }
        }
    }

    private void preview(double screenX, double screenY) {
        Proposal proposal = proposal(pressed, screenX, screenY);
        if (proposal != null) {
            MoveCheck check = manager.checkMove(pressed, proposal.target());
            if (!(check instanceof MoveCheck.Rejected)) {
                pane.showDropIndicator(proposal.marker(), check instanceof MoveCheck.NoChange);
                return;
            }
        }
        pane.hideDropIndicator();
    }

    private Proposal proposal(DockableId id, double screenX, double screenY) {
        List<GroupSurface> groups = surfaces.get();
        for (GroupSurface group : groups) {
            if (hidden(group) && (group.edge() != null || group.tabs().isManaged())) {
                Proposal empty = emptyProposal(group, screenX, screenY);
                if (empty != null) return empty;
            }
        }
        for (GroupSurface group : groups) {
            if (hidden(group)) continue;
            Bounds bounds = group.tabs().localToScreen(group.tabs().getBoundsInLocal());
            if (bounds == null || !bounds.contains(screenX, screenY)) continue;
            Node strip = group.tabs().lookup(".tab-header-area");
            Bounds stripBounds = strip == null ? null : strip.localToScreen(strip.getBoundsInLocal());
            if (stripBounds != null && stripBounds.contains(screenX, screenY)) {
                Proposal insertion = insertion(group, screenX, screenY);
                if (insertion != null) return insertion;
            }
            Bounds content = contentBounds(bounds, stripBounds, group.tabs().getSide());
            Side edge = edgeAt(content, screenX, screenY);
            if (edge != null) {
                DockTarget target = DockTarget.split(group.id(), edge);
                MoveCheck split = manager.checkMove(id, target);
                if (split instanceof MoveCheck.Allowed) return new Proposal(target, half(content, edge));
                if (split instanceof MoveCheck.NoChange) return new Proposal(target, bounds);
            }
            return new Proposal(DockTarget.group(group.id()), bounds);
        }
        for (GroupSurface group : groups) {
            if (hidden(group) && group.edge() == null && !group.tabs().isManaged()) {
                Proposal empty = emptyProposal(group, screenX, screenY);
                if (empty != null) return empty;
            }
        }
        return null;
    }

    private static boolean hidden(GroupSurface group) {
        return !group.tabs().isVisible() || !group.tabs().isManaged();
    }

    private Proposal emptyProposal(GroupSurface group, double x, double y) {
        Bounds bounds = group.tabs().isManaged()
                ? group.tabs().localToScreen(group.tabs().getBoundsInLocal())
                : pane.localToScreen(pane.getBoundsInLocal());
        if (bounds == null) return null;
        if (!group.tabs().isManaged() && group.edge() != null) {
            double width = Math.min(20, bounds.getWidth());
            double height = Math.min(20, bounds.getHeight());
            bounds = switch (group.edge()) {
                case LEFT -> new BoundingBox(bounds.getMinX(), bounds.getMinY(), width, bounds.getHeight());
                case RIGHT -> new BoundingBox(bounds.getMaxX() - width, bounds.getMinY(), width, bounds.getHeight());
                case TOP -> new BoundingBox(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), height);
                case BOTTOM -> new BoundingBox(bounds.getMinX(), bounds.getMaxY() - height, bounds.getWidth(), height);
            };
        }
        return bounds.contains(x, y) ? new Proposal(DockTarget.group(group.id()), bounds) : null;
    }

    private static Bounds contentBounds(Bounds group, Bounds strip, Side side) {
        if (strip == null) return group;
        double left = side == Side.LEFT ? Math.max(group.getMinX(), strip.getMaxX()) : group.getMinX();
        double top = side == Side.TOP ? Math.max(group.getMinY(), strip.getMaxY()) : group.getMinY();
        double right = side == Side.RIGHT ? Math.min(group.getMaxX(), strip.getMinX()) : group.getMaxX();
        double bottom = side == Side.BOTTOM ? Math.min(group.getMaxY(), strip.getMinY()) : group.getMaxY();
        return new BoundingBox(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    private static Side edgeAt(Bounds content, double x, double y) {
        if (!content.contains(x, y) || content.getWidth() <= 0 || content.getHeight() <= 0) return null;
        double horizontal = Math.min(40, content.getWidth() / 5);
        double vertical = Math.min(40, content.getHeight() / 5);
        double best = 1;
        Side selected = null;
        for (Side side : Side.values()) {
            double distance = switch (side) {
                case LEFT -> (x - content.getMinX()) / horizontal;
                case RIGHT -> (content.getMaxX() - x) / horizontal;
                case TOP -> (y - content.getMinY()) / vertical;
                case BOTTOM -> (content.getMaxY() - y) / vertical;
            };
            if (distance < best) {
                best = distance;
                selected = side;
            }
        }
        return selected;
    }

    private static Bounds half(Bounds bounds, Side side) {
        return switch (side) {
            case LEFT -> new BoundingBox(bounds.getMinX(), bounds.getMinY(), bounds.getWidth() / 2, bounds.getHeight());
            case RIGHT -> new BoundingBox(bounds.getCenterX(), bounds.getMinY(), bounds.getWidth() / 2, bounds.getHeight());
            case TOP -> new BoundingBox(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight() / 2);
            case BOTTOM -> new BoundingBox(bounds.getMinX(), bounds.getCenterY(), bounds.getWidth(), bounds.getHeight() / 2);
        };
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
