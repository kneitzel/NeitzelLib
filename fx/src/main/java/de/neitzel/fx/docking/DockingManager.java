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
    private final PlacementRule placementRule;
    private final DockDragController drag;
    private boolean updating;
    private final Map<DockAreaId, Group> groups = new LinkedHashMap<>();
    private final Map<DockableId, Dockable> dockables = new LinkedHashMap<>();
    private final Map<DockableId, Node> previousFocus = new LinkedHashMap<>();
    private final javafx.beans.value.ChangeListener<Node> focusListener = (observable, old, current) -> rememberFocus(current);
    private final ReadOnlyObjectWrapper<Dockable> active = new ReadOnlyObjectWrapper<>(this, "activeDockable");
    private final ReadOnlyObjectWrapper<DockLayoutView> layout = new ReadOnlyObjectWrapper<>(this, "layout");

    private DockingManager(Stage stage, DockPane pane, DockingOptions options) {
        this.stage = stage;
        this.pane = pane;
        this.placementRule = Objects.requireNonNull(options.getPlacementRule(), "placementRule");
        DockLayout initial = Objects.requireNonNull(pane.getInitialLayout(), "initialLayout");
        validate(initial);
        addGroup(initial.getCenter(), pane.surface::setCenter);
        addGroup(initial.getLeft(), pane.surface::setLeft);
        addGroup(initial.getRight(), pane.surface::setRight);
        addGroup(initial.getTop(), pane.surface::setTop);
        addGroup(initial.getBottom(), pane.surface::setBottom);
        drag = new DockDragController(this, pane, () -> groups.values().stream()
                .map(group -> new DockDragController.GroupSurface(group.id, group.tabs)).toList());
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
        DockingManager manager = new DockingManager(stage, pane, options);
        pane.manager = manager;
        stage.getProperties().put(ATTACHMENT_KEY, manager);
        return manager;
    }

    public ActionResult open(Dockable dockable, DockTarget target) {
        requireFxThread();
        Objects.requireNonNull(dockable, "dockable");
        if (dockables.containsKey(dockable.getId())) return activate(dockable.getId());
        MoveCheck check = resolve(dockable, target);
        if (check instanceof MoveCheck.Rejected rejected) return new ActionResult.Rejected(rejected.failure());
        var destination = (ResolvedDockTarget.Insertion) ((MoveCheck.Allowed) check).destination();
        Group group = group(destination.groupId());
        if (dockable.getContent().getParent() != null || dockable.getContent().getScene() != null) {
            return rejected(DockFailure.Code.CONTENT_ALREADY_ATTACHED, dockable.getId(), "Content already belongs to a scene graph");
        }
        Tab tab = new Tab();
        tab.setClosable(false);
        tab.textProperty().bind(dockable.titleProperty());
        tab.graphicProperty().bind(dockable.iconProperty());
        tab.setContent(dockable.getContent());
        tab.setUserData(dockable.getId());
        drag.installTab(tab, dockable.getId());
        dockables.put(dockable.getId(), dockable);
        updating = true;
        try {
            group.tabs.getTabs().add(destination.index(), tab);
            group.tabs.getSelectionModel().select(tab);
        } finally {
            updating = false;
        }
        active.set(dockable);
        focus(dockable);
        publish();
        return new ActionResult.Applied();
    }

    /** Creates content only for a new identity; rejected prepared content is discarded. */
    public ActionResult open(DockableId id, java.util.function.Supplier<? extends Dockable> factory, DockTarget target) {
        requireFxThread();
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(factory, "factory");
        if (dockables.containsKey(id)) return activate(id);
        Dockable prepared;
        try {
            prepared = Objects.requireNonNull(factory.get(), "Factory returned no Dockable");
        } catch (RuntimeException failure) {
            return new ActionResult.Rejected(new DockFailure(DockFailure.Code.FACTORY_FAILED,
                    id, "Dockable creation failed", failure));
        }
        ActionResult result = id.equals(prepared.getId()) ? open(prepared, target)
                : rejected(DockFailure.Code.ID_MISMATCH, id, "Factory returned a different Dockable ID");
        if (result instanceof ActionResult.Rejected rejected && !dockables.containsValue(prepared)
                && prepared.getOnDiscarded() != null) {
            try {
                prepared.getOnDiscarded().run();
            } catch (RuntimeException cleanup) {
                DockFailure failure = rejected.failure();
                var cause = new IllegalStateException("Cleanup failed after rejected open", failure.cause());
                cause.addSuppressed(cleanup);
                return new ActionResult.Rejected(new DockFailure(failure.code(), failure.dockableId(), failure.message(), cause));
            }
        }
        return result;
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

    /** Resolves a move without mutating content, layout or identity allocation. */
    public MoveCheck checkMove(DockableId id, DockTarget target) {
        requireFxThread();
        if (!dockables.containsKey(id)) return moveRejected(DockFailure.Code.DOCKABLE_NOT_FOUND, id, "Dockable is not open");
        return resolve(dockables.get(id), target);
    }

    /** Moves the live content, selecting it only after the complete transfer. */
    public ActionResult move(DockableId id, DockTarget target) {
        MoveCheck check = checkMove(id, target);
        if (check instanceof MoveCheck.Rejected rejected) return new ActionResult.Rejected(rejected.failure());
        if (check instanceof MoveCheck.NoChange) return new ActionResult.NoChange();
        var destination = (ResolvedDockTarget.Insertion) ((MoveCheck.Allowed) check).destination();
        Group source = containing(id);
        Group receiver = group(destination.groupId());
        Tab tab = source.tabs.getTabs().get(tabIndex(source, id));
        updating = true;
        try {
            tab.setContent(null);
            source.tabs.getTabs().remove(tab);
            receiver.tabs.getTabs().add(destination.index(), tab);
            tab.setContent(dockables.get(id).getContent());
            receiver.tabs.getSelectionModel().select(tab);
        } finally {
            updating = false;
        }
        active.set(dockables.get(id));
        focus(active.get());
        publish();
        return new ActionResult.Applied();
    }

    private MoveCheck resolve(Dockable dockable, DockTarget target) {
        DockableId id = dockable.getId();
        Objects.requireNonNull(target, "target");
        Group receiver;
        TabPosition position;
        if (target instanceof DockTarget.Area area) {
            receiver = groups.get(area.id());
            position = TabPosition.append();
        } else {
            DockTarget.Group concrete = (DockTarget.Group) target;
            receiver = group(concrete.id());
            position = concrete.position();
        }
        if (receiver == null) return moveRejected(DockFailure.Code.TARGET_NOT_FOUND, id, "Destination does not exist");
        int index = receiver.tabs.getTabs().size();
        DockableId reference = switch (position) {
            case TabPosition.Before before -> before.id();
            case TabPosition.After after -> after.id();
            case TabPosition.Append ignored -> null;
        };
        if (reference != null) {
            index = tabIndex(receiver, reference);
            if (index < 0) return moveRejected(DockFailure.Code.REFERENCE_TAB_NOT_FOUND, id, "Reference tab is not in the destination");
            if (position instanceof TabPosition.After) index++;
        }
        Group source = containing(id);
        if (source == receiver) {
            int oldIndex = tabIndex(source, id);
            if (index > oldIndex) index--;
            if (index == oldIndex) return new MoveCheck.NoChange();
        }
        var resolved = new ResolvedDockTarget.Insertion(receiver.id, index);
        try {
            Optional<DockFailure> failure = Objects.requireNonNull(placementRule.check(dockable, resolved), "Placement rule result");
            if (failure.isPresent()) return new MoveCheck.Rejected(failure.get());
        } catch (RuntimeException failure) {
            return new MoveCheck.Rejected(new DockFailure(DockFailure.Code.PLACEMENT_CHECK_FAILED,
                    id, "Placement check failed", failure));
        }
        return new MoveCheck.Allowed(resolved);
    }

    private Group group(DockGroupId id) {
        return groups.values().stream().filter(group -> group.id.equals(id)).findFirst().orElse(null);
    }

    private Group containing(DockableId id) {
        return groups.values().stream().filter(group -> tabIndex(group, id) >= 0).findFirst().orElse(null);
    }

    private static int tabIndex(Group group, DockableId id) {
        for (int i = 0; i < group.tabs.getTabs().size(); i++) {
            if (id.equals(group.tabs.getTabs().get(i).getUserData())) return i;
        }
        return -1;
    }

    private static MoveCheck moveRejected(DockFailure.Code code, DockableId id, String message) {
        return new MoveCheck.Rejected(new DockFailure(code, id, message, null));
    }
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
