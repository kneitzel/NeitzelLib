package de.neitzel.fx.docking;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Owns the live tab groups of one embedded docking area. */
public final class DockingManager {
    private static final Object ATTACHMENT_KEY = new Object();
    private final Stage stage;
    private final DockPane pane;
    private boolean updating;
    private final Map<DockAreaId, Group> groups = new LinkedHashMap<>();
    private final Map<DockableId, Dockable> dockables = new LinkedHashMap<>();
    private final Map<DockableId, Node> previousFocus = new LinkedHashMap<>();
    private final javafx.beans.value.ChangeListener<Node> focusListener = (observable, old, current) -> rememberFocus(current);
    private final ReadOnlyObjectWrapper<Dockable> active = new ReadOnlyObjectWrapper<>(this, "activeDockable");
    private final ReadOnlyObjectWrapper<DockLayoutView> layout = new ReadOnlyObjectWrapper<>(this, "layout");

    private DockingManager(Stage stage, DockPane pane) {
        this.stage = stage;
        this.pane = pane;
        DockLayout initial = Objects.requireNonNull(pane.getInitialLayout(), "initialLayout");
        validate(initial);
        addGroup(initial.getCenter(), pane.surface::setCenter);
        addGroup(initial.getLeft(), pane.surface::setLeft);
        addGroup(initial.getRight(), pane.surface::setRight);
        addGroup(initial.getTop(), pane.surface::setTop);
        addGroup(initial.getBottom(), pane.surface::setBottom);
        publish();
        stage.getScene().focusOwnerProperty().addListener(focusListener);
    }

    public static DockingManager attach(Stage stage, DockPane pane, DockingOptions options) {
        requireFxThread();
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(pane, "pane");
        Objects.requireNonNull(options, "options");
        if (stage.getScene() == null || stage.getScene() != pane.getScene()) {
            throw new IllegalArgumentException("DockPane must belong to the main Stage's Scene");
        }
        if (pane.manager != null || stage.getProperties().containsKey(ATTACHMENT_KEY)) {
            throw new IllegalStateException("DockPane or main Stage already has a docking manager");
        }
        DockingManager manager = new DockingManager(stage, pane);
        pane.manager = manager;
        stage.getProperties().put(ATTACHMENT_KEY, manager);
        return manager;
    }

    public ActionResult open(Dockable dockable, DockTarget target) {
        requireFxThread();
        Objects.requireNonNull(dockable, "dockable");
        if (dockables.containsKey(dockable.getId())) return activate(dockable.getId());
        Objects.requireNonNull(target, "target");
        Group group = groups.get(((DockTarget.Area) target).id());
        if (group == null) return rejected(DockFailure.Code.TARGET_NOT_FOUND, dockable.getId(), "Destination does not exist");
        if (dockable.getContent().getParent() != null || dockable.getContent().getScene() != null) {
            return rejected(DockFailure.Code.CONTENT_ALREADY_ATTACHED, dockable.getId(), "Content already belongs to a scene graph");
        }
        Tab tab = new Tab();
        tab.setClosable(false);
        tab.textProperty().bind(dockable.titleProperty());
        tab.graphicProperty().bind(dockable.iconProperty());
        tab.setContent(dockable.getContent());
        tab.setUserData(dockable.getId());
        dockables.put(dockable.getId(), dockable);
        updating = true;
        try {
            group.tabs.getTabs().add(tab);
            group.tabs.getSelectionModel().select(tab);
        } finally {
            updating = false;
        }
        active.set(dockable);
        focus(dockable);
        publish();
        return new ActionResult.Applied();
    }

    public ActionResult activate(DockableId id) {
        requireFxThread();
        Dockable dockable = dockables.get(id);
        if (dockable == null) return rejected(DockFailure.Code.DOCKABLE_NOT_FOUND, id, "Dockable is not open");
        Node target = focusTarget(dockable);
        boolean changed = active.get() != dockable || stage.getScene().getFocusOwner() != target
                || (stage.isShowing() && !stage.isFocused());
        for (Group group : groups.values()) {
            for (Tab tab : group.tabs.getTabs()) {
                if (id.equals(tab.getUserData())) {
                    changed |= group.tabs.getSelectionModel().getSelectedItem() != tab;
                    updating = true;
                    try {
                        group.tabs.getSelectionModel().select(tab);
                    } finally {
                        updating = false;
                    }
                }
            }
        }
        active.set(dockable);
        if (stage.isShowing()) stage.toFront();
        focus(dockable);
        if (changed) publish();
        return changed ? new ActionResult.Applied() : new ActionResult.NoChange();
    }

    public Optional<Dockable> findDockable(DockableId id) { return Optional.ofNullable(dockables.get(id)); }
    public Dockable activeDockable() { return active.get(); }
    public ReadOnlyObjectProperty<Dockable> activeDockableProperty() { return active.getReadOnlyProperty(); }
    public DockLayoutView layout() { return layout.get(); }
    public ReadOnlyObjectProperty<DockLayoutView> layoutProperty() { return layout.getReadOnlyProperty(); }

    private void addGroup(DockGroupDefinition definition, java.util.function.Consumer<javafx.scene.Node> place) {
        if (definition == null) return;
        DockAreaId area = new DockAreaId(definition.getDestinationId());
        if (groups.containsKey(area)) throw new IllegalArgumentException("Duplicate destination: " + area.value());
        TabPane tabs = new TabPane();
        tabs.setSide(Objects.requireNonNull(definition.getTabSide(), "tabSide"));
        tabs.setPrefSize(definition.getPreferredWidth(), definition.getPreferredHeight());
        Group group = new Group(new DockGroupId(area.value()), tabs);
        groups.put(area, group);
        tabs.getSelectionModel().selectedItemProperty().addListener((observable, old, selected) -> {
            if (!updating && selected != null) {
                active.set(dockables.get((DockableId) selected.getUserData()));
                focus(active.get());
                publish();
            }
        });
        place.accept(tabs);
    }

    private void rememberFocus(Node current) {
        if (current == null) return;
        for (Dockable dockable : dockables.values()) {
            if (contains(dockable.getContent(), current)) {
                previousFocus.put(dockable.getId(), current);
                active.set(dockable);
                return;
            }
        }
    }

    private Node focusTarget(Dockable dockable) {
        Node target = dockable.getFocusTarget() == null ? previousFocus.get(dockable.getId()) : dockable.getFocusTarget().get();
        if (target == null || !contains(dockable.getContent(), target)) return dockable.getContent();
        return target;
    }

    private void focus(Dockable dockable) {
        if (dockable == null) return;
        pane.applyCss();
        Node target = focusTarget(dockable);
        target.requestFocus();
    }

    private static boolean contains(Node root, Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == root) return true;
        }
        return false;
    }

    private static void validate(DockLayout initial) {
        var destinations = new java.util.HashSet<DockAreaId>();
        for (DockGroupDefinition definition : new DockGroupDefinition[]{initial.getCenter(), initial.getLeft(),
                initial.getRight(), initial.getTop(), initial.getBottom()}) {
            if (definition == null) continue;
            DockAreaId id = new DockAreaId(definition.getDestinationId());
            if (!destinations.add(id)) throw new IllegalArgumentException("Duplicate destination: " + id.value());
            if (definition.getTabSide() == null) throw new IllegalArgumentException("Tab side is required");
            if (!Double.isFinite(definition.getPreferredWidth()) || definition.getPreferredWidth() <= 0
                    || !Double.isFinite(definition.getPreferredHeight()) || definition.getPreferredHeight() <= 0) {
                throw new IllegalArgumentException("Group preferred dimensions must be positive and finite");
            }
        }
    }

    private void publish() {
        List<DockLayoutView.Area> areas = groups.entrySet().stream().map(entry -> {
            Group group = entry.getValue();
            List<DockableId> ids = group.tabs.getTabs().stream().map(tab -> (DockableId) tab.getUserData()).toList();
            Tab selected = group.tabs.getSelectionModel().getSelectedItem();
            return new DockLayoutView.Area(entry.getKey(), List.of(new DockLayoutView.Group(group.id,
                    group.tabs.getSide(), ids, selected == null ? null : (DockableId) selected.getUserData())));
        }).toList();
        layout.set(new DockLayoutView(List.of(new DockLayoutView.Window(new DockWindowId("main"), true, areas))));
    }

    private static ActionResult rejected(DockFailure.Code code, DockableId id, String message) {
        return new ActionResult.Rejected(new DockFailure(code, id, message, null));
    }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Docking operations require the JavaFX Application Thread");
    }

    private record Group(DockGroupId id, TabPane tabs) { }
}
