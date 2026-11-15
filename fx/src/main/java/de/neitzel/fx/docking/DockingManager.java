package de.neitzel.fx.docking;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Owns the live tab groups of one embedded docking area. */
@Slf4j
public final class DockingManager {
    private static final Object ATTACHMENT_KEY = new Object();
    private final Stage stage;
    private final DockPane pane;
    private final PlacementRule placementRule;
    private final DockDragController drag;
    private final DockCloseCoordinator closing;
    private boolean updating;
    private final Map<DockGroupId, Group> groups = new LinkedHashMap<>();
    private final Map<DockGroupId, Long> groupActivationOrder = new LinkedHashMap<>();
    private final DockLayoutTree tree = new DockLayoutTree(this::publish, id -> !groupClosing(group(id)));
    private final Map<DockAreaId, javafx.geometry.Side> edgeAreas = new LinkedHashMap<>();
    private long nextGroupId;
    private final Map<DockableId, Dockable> dockables = new LinkedHashMap<>();
    private final Map<DockableId, Node> previousFocus = new LinkedHashMap<>();
    private final Map<DockableId, Long> activationOrder = new LinkedHashMap<>();
    private long nextActivation;
    private final javafx.beans.value.ChangeListener<Node> focusListener = (observable, old, current) -> rememberFocus(current);
    private final ReadOnlyObjectWrapper<Dockable> active = new ReadOnlyObjectWrapper<>(this, "activeDockable");
    private final ReadOnlyObjectWrapper<DockLayoutView> layout = new ReadOnlyObjectWrapper<>(this, "layout");

    private DockingManager(Stage stage, DockPane pane, DockingOptions options) {
        this.stage = stage;
        this.pane = pane;
        closing = new DockCloseCoordinator(new DockCloseCoordinator.Host() {
            @Override public Optional<Dockable> find(DockableId id) { return findDockable(id); }
            @Override public javafx.stage.Window owner(DockableId id) { return stage; }
            @Override public void remove(DockableId id) { removeDockable(id); }
        });
        this.placementRule = Objects.requireNonNull(options.getPlacementRule(), "placementRule");
        DockLayout initial = Objects.requireNonNull(pane.getInitialLayout(), "initialLayout");
        validate(initial);
        addGroup(initial.getCenter(), pane.surface::setCenter);
        addEdgeGroup(initial.getLeft(), javafx.geometry.Side.LEFT);
        addEdgeGroup(initial.getRight(), javafx.geometry.Side.RIGHT);
        addEdgeGroup(initial.getTop(), javafx.geometry.Side.TOP);
        addEdgeGroup(initial.getBottom(), javafx.geometry.Side.BOTTOM);
        drag = new DockDragController(this, pane, () -> groups.values().stream()
                .map(group -> new DockDragController.GroupSurface(group.id, group.tabs, pane.edgeOf(group.tabs))).toList());
        refreshEmptyGroups();
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
        var destination = ((MoveCheck.Allowed) check).destination();
        if (dockable.getContent().getParent() != null || dockable.getContent().getScene() != null) {
            return rejected(DockFailure.Code.CONTENT_ALREADY_ATTACHED, dockable.getId(), "Content already belongs to a scene graph");
        }
        Group group = receiver(destination);
        int insertionIndex = destination instanceof ResolvedDockTarget.Insertion insertion ? insertion.index() : 0;
        Tab tab = new Tab();
        tab.setClosable(true);
        tab.setOnCloseRequest(event -> {
            event.consume();
            requestTabClose(dockable.getId());
        });
        tab.textProperty().bind(dockable.titleProperty());
        tab.graphicProperty().bind(dockable.iconProperty());
        tab.setContent(dockable.getContent());
        tab.setUserData(dockable.getId());
        drag.installTab(tab, dockable.getId());
        dockables.put(dockable.getId(), dockable);
        updating = true;
        try {
            group.tabs.getTabs().add(insertionIndex, tab);
            group.tabs.getSelectionModel().select(tab);
            refreshEmptyGroups();
        } finally {
            updating = false;
        }
        setActive(dockable);
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
        setActive(dockable);
        if (stage.isShowing()) stage.toFront();
        focus(dockable);
        if (changed) publish();
        return changed ? new ActionResult.Applied() : new ActionResult.NoChange();
    }

    public Optional<Dockable> findDockable(DockableId id) { return Optional.ofNullable(dockables.get(id)); }

    /** Requests consent without closing or releasing content while the answer is pending. */
    public java.util.concurrent.CompletionStage<CloseResult> requestTabClose(DockableId id) {
        return closing.requestTabClose(id);
    }

    /** Scope remains available until checks and resource release have finished. */
    public ReadOnlyObjectProperty<CloseScope> closingScopeProperty() {
        return closing.closingScopeProperty();
    }

    private void removeDockable(DockableId id) {
        Group group = containing(id);
        if (group == null) return;
        Tab tab = group.tabs.getTabs().get(tabIndex(group, id));
        boolean wasActive = active.get() == dockables.get(id);
        updating = true;
        try {
            drag.uninstallTab(tab);
            tab.textProperty().unbind();
            tab.graphicProperty().unbind();
            tab.setGraphic(null);
            tab.setContent(null);
            group.tabs.getTabs().remove(tab);
            dockables.remove(id);
            previousFocus.remove(id);
            activationOrder.remove(id);
            normalizeLayout(group.id);
        } finally {
            updating = false;
        }
        if (wasActive) {
            Tab selected = group.tabs.getSelectionModel().getSelectedItem();
            if (selected == null) {
                selected = groups.values().stream().map(candidate -> candidate.tabs.getSelectionModel().getSelectedItem())
                        .filter(Objects::nonNull)
                        .max(java.util.Comparator.comparingLong(candidate ->
                                activationOrder.getOrDefault((DockableId) candidate.getUserData(), 0L)))
                        .orElse(null);
            }
            setActive(selected == null ? null : dockables.get((DockableId) selected.getUserData()));
            focus(active.get());
        }
        publish();
    }

    private boolean groupClosing(Group group) {
        CloseScope scope = closing.scope();
        if (scope == null) return false;
        for (DockableId id : scope.dockableIds()) {
            if (!dockables.containsKey(id) || (group != null && tabIndex(group, id) >= 0)) return true;
        }
        return false;
    }

    /** Resolves a move without mutating content, layout or identity allocation. */
    public MoveCheck checkMove(DockableId id, DockTarget target) {
        requireFxThread();
        if (!dockables.containsKey(id)) return moveRejected(DockFailure.Code.DOCKABLE_NOT_FOUND, id, "Dockable is not open");
        if (groupClosing(containing(id))) return moveRejected(DockFailure.Code.CLOSE_IN_PROGRESS, id, "A close request covers this group");
        MoveCheck check = resolve(dockables.get(id), target);
        if (check instanceof MoveCheck.Allowed allowed) {
            DockGroupId destination = switch (allowed.destination()) {
                case ResolvedDockTarget.Insertion insertion -> insertion.groupId();
                case ResolvedDockTarget.Split split -> split.groupId();
            };
            if (tree.normalizationAffects(containing(id).id, destination, this::groupClosing)) {
                return moveRejected(DockFailure.Code.CLOSE_IN_PROGRESS, id,
                        "Removing this tab would resize a group covered by a close request");
            }
        }
        return check;
    }

    /** Moves the live content, selecting it only after the complete transfer. */
    public ActionResult move(DockableId id, DockTarget target) {
        MoveCheck check = checkMove(id, target);
        if (check instanceof MoveCheck.Rejected rejected) return new ActionResult.Rejected(rejected.failure());
        if (check instanceof MoveCheck.NoChange) return new ActionResult.NoChange();
        var destination = ((MoveCheck.Allowed) check).destination();
        Group source = containing(id);
        Group receiver = receiver(destination);
        int insertionIndex = destination instanceof ResolvedDockTarget.Insertion insertion ? insertion.index() : 0;
        Tab tab = source.tabs.getTabs().get(tabIndex(source, id));
        updating = true;
        try {
            tab.setContent(null);
            source.tabs.getTabs().remove(tab);
            receiver.tabs.getTabs().add(insertionIndex, tab);
            tab.setContent(dockables.get(id).getContent());
            receiver.tabs.getSelectionModel().select(tab);
            normalizeLayout(source.id);
        } finally {
            updating = false;
        }
        setActive(dockables.get(id));
        focus(active.get());
        publish();
        return new ActionResult.Applied();
    }

    private MoveCheck resolve(Dockable dockable, DockTarget target) {
        DockableId id = dockable.getId();
        Objects.requireNonNull(target, "target");
        if (target instanceof DockTarget.Split split) {
            Group receiver = group(split.id());
            if (receiver == null) return moveRejected(DockFailure.Code.TARGET_NOT_FOUND, id, "Destination does not exist");
            if (groupClosing(receiver)) return moveRejected(DockFailure.Code.CLOSE_IN_PROGRESS, id, "A close request covers this group");
            if (containing(id) == receiver && receiver.tabs.getTabs().size() == 1) return new MoveCheck.NoChange();
            if (!receiver.definition.getSplitDirections().allows(split.side())) {
                return moveRejected(DockFailure.Code.SPLIT_DISABLED, id, "Splitting is disabled in this direction");
            }
            double available = split.side().isVertical() ? receiver.tabs.getWidth() : receiver.tabs.getHeight();
            if (available <= 0) available = split.side().isVertical()
                    ? receiver.definition.getPreferredWidth() : receiver.definition.getPreferredHeight();
            double minimum = split.side().isVertical()
                    ? receiver.definition.getMinimumWidth() : receiver.definition.getMinimumHeight();
            if (available - 8 < minimum * 2) {
                return moveRejected(DockFailure.Code.SPLIT_TOO_SMALL, id, "Both group minima do not fit beside the divider");
            }
            return checkPlacement(dockable, new ResolvedDockTarget.Split(receiver.id, split.side()));
        }
        if (target instanceof DockTarget.Area area) {
            List<Group> candidates = tree.groups(area.id()).stream().sorted(java.util.Comparator
                    .comparingLong((Group candidate) -> groupActivationOrder.getOrDefault(candidate.id, 0L)).reversed()).toList();
            MoveCheck rejected = moveRejected(DockFailure.Code.TARGET_NOT_FOUND, id, "Destination does not exist");
            for (Group candidate : candidates) {
                MoveCheck result = resolve(dockable, DockTarget.group(candidate.id));
                if (!(result instanceof MoveCheck.Rejected)) return result;
                if (candidate == candidates.getFirst()) rejected = result;
            }
            return rejected;
        }
        DockTarget.Group concrete = (DockTarget.Group) target;
        Group receiver = group(concrete.id());
        TabPosition position = concrete.position();
        if (receiver == null) return moveRejected(DockFailure.Code.TARGET_NOT_FOUND, id, "Destination does not exist");
        if (groupClosing(receiver)) return moveRejected(DockFailure.Code.CLOSE_IN_PROGRESS, id, "A close request covers this group");
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
        return checkPlacement(dockable, resolved);
    }

    private MoveCheck checkPlacement(Dockable dockable, ResolvedDockTarget resolved) {
        DockableId id = dockable.getId();
        try {
            Optional<DockFailure> failure = Objects.requireNonNull(placementRule.check(dockable, resolved), "Placement rule result");
            if (failure.isPresent()) return new MoveCheck.Rejected(failure.get());
        } catch (RuntimeException failure) {
            return new MoveCheck.Rejected(new DockFailure(DockFailure.Code.PLACEMENT_CHECK_FAILED,
                    id, "Placement check failed", failure));
        }
        return new MoveCheck.Allowed(resolved);
    }

    private void normalizeLayout(DockGroupId source) {
        for (DockGroupId id : tree.normalize(source)) {
            groups.remove(id);
            groupActivationOrder.remove(id);
        }
        refreshEmptyGroups();
    }

    private void refreshEmptyGroups() {
        for (Group group : groups.values()) {
            boolean occupied = !group.tabs.getTabs().isEmpty();
            group.tabs.setVisible(occupied || (group.definition.isVisibleWhenEmpty()
                    && !group.definition.isReleaseSpaceWhenEmpty()));
            group.tabs.setManaged(occupied || !group.definition.isReleaseSpaceWhenEmpty());
        }
    }

    private Group group(DockGroupId id) {
        return groups.get(id);
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

    private void addEdgeGroup(DockLayoutNode definition, javafx.geometry.Side side) {
        if (definition == null) return;
        edgeAreas.put(new DockAreaId(definition.getDestinationId()), side);
        DockAreaId areaId = new DockAreaId(definition.getDestinationId());
        addGroup(definition, node -> pane.setArea(side, node,
                () -> tree.groups(areaId).stream().noneMatch(this::groupClosing), this::publish));
    }

    private void addGroup(DockLayoutNode definition, java.util.function.Consumer<javafx.scene.Node> place) {
        if (definition == null) return;
        tree.addArea(definition, place,
                group -> createGroup(new DockGroupId(group.getDestinationId()), group));
    }

    private Group createGroup(DockGroupId id, DockGroupDefinition definition) {
        DockGroupDefinition captured = new DockGroupDefinition(definition.getDestinationId());
        captured.setTabSide(definition.getTabSide());
        captured.setPreferredWidth(definition.getPreferredWidth());
        captured.setPreferredHeight(definition.getPreferredHeight());
        captured.setMinimumWidth(definition.getMinimumWidth());
        captured.setMinimumHeight(definition.getMinimumHeight());
        captured.setSplitDirections(definition.getSplitDirections());
        captured.setPersistent(definition.isPersistent());
        captured.setVisibleWhenEmpty(definition.isVisibleWhenEmpty());
        captured.setReleaseSpaceWhenEmpty(definition.isReleaseSpaceWhenEmpty());
        TabPane tabs = new TabPane();
        tabs.setSide(captured.getTabSide());
        tabs.setPrefSize(captured.getPreferredWidth(), captured.getPreferredHeight());
        tabs.setMinSize(captured.getMinimumWidth(), captured.getMinimumHeight());
        Group group = new Group(id, tabs, captured);
        groups.put(id, group);
        tabs.getSelectionModel().selectedItemProperty().addListener((observable, old, selected) -> {
            if (!updating && selected != null) {
                setActive(dockables.get((DockableId) selected.getUserData()));
                focus(active.get());
                publish();
            }
        });
        return group;
    }

    private Group receiver(ResolvedDockTarget destination) {
        if (destination instanceof ResolvedDockTarget.Insertion insertion) return group(insertion.groupId());
        ResolvedDockTarget.Split split = (ResolvedDockTarget.Split) destination;
        Group target = group(split.groupId());
        DockGroupId id;
        do { id = new DockGroupId("group-" + ++nextGroupId); } while (groups.containsKey(id));
        Group added = createGroup(id, target.definition);
        tree.split(target, added, split.side());
        return added;
    }

    private void setActive(Dockable dockable) {
        if (dockable != null) {
            long order = ++nextActivation;
            activationOrder.put(dockable.getId(), order);
            Group group = containing(dockable.getId());
            if (group != null) groupActivationOrder.put(group.id, order);
        }
        active.set(dockable);
    }

    private void rememberFocus(Node current) {
        if (current == null) return;
        for (Dockable dockable : dockables.values()) {
            if (contains(dockable.getContent(), current)) {
                previousFocus.put(dockable.getId(), current);
                setActive(dockable);
                return;
            }
        }
    }

    private Node focusTarget(Dockable dockable) {
        Node target = previousFocus.get(dockable.getId());
        if (dockable.getFocusTarget() != null) {
            try {
                target = dockable.getFocusTarget().get();
            } catch (RuntimeException failure) {
                log.warn("Cannot resolve focus target for Dockable {}; using its content focus", dockable.getId(), failure);
            }
        }
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
        for (DockLayoutNode definition : new DockLayoutNode[]{initial.getCenter(), initial.getLeft(),
                initial.getRight(), initial.getTop(), initial.getBottom()}) {
            if (definition != null) validate(definition, destinations);
        }
    }

    private static void validate(DockLayoutNode definition, java.util.Set<DockAreaId> destinations) {
        if (definition == null) throw new IllegalArgumentException("A split needs two branches");
        DockAreaId id = new DockAreaId(definition.getDestinationId());
        if (!destinations.add(id)) throw new IllegalArgumentException("Duplicate destination: " + id.value());
        if (definition instanceof DockSplitDefinition split) {
            if (split.getOrientation() == null || !Double.isFinite(split.getPosition())
                    || split.getPosition() <= 0 || split.getPosition() >= 1) {
                throw new IllegalArgumentException("A split needs an orientation and a position between zero and one");
            }
            validate(split.getFirst(), destinations);
            validate(split.getSecond(), destinations);
        } else {
            DockGroupDefinition group = (DockGroupDefinition) definition;
            if (group.getSplitDirections() == null || !Double.isFinite(group.getMinimumWidth())
                    || group.getMinimumWidth() < 0 || !Double.isFinite(group.getMinimumHeight()) || group.getMinimumHeight() < 0) {
                throw new IllegalArgumentException("Group minimum dimensions must be nonnegative and finite, with split directions");
            }
            if (group.getTabSide() == null) throw new IllegalArgumentException("Tab side is required");
            if (!Double.isFinite(group.getPreferredWidth()) || group.getPreferredWidth() <= 0
                    || !Double.isFinite(group.getPreferredHeight()) || group.getPreferredHeight() <= 0) {
                throw new IllegalArgumentException("Group preferred dimensions must be positive and finite");
            }
        }
    }

    private void publish() {
        boolean wasUpdating = updating;
        updating = true;
        try {
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            tree.captureGeometry();
        } finally {
            updating = wasUpdating;
        }
        var areas = tree.view().stream().map(area -> {
            javafx.geometry.Side side = edgeAreas.get(area.id());
            if (side == null) return area;
            double extent = pane.committedExtent(side);
            return new DockLayoutView.Area(area.id(), area.groups(), area.root(),
                    side.isVertical() ? extent : -1, side.isHorizontal() ? extent : -1);
        }).toList();
        layout.set(new DockLayoutView(List.of(new DockLayoutView.Window(new DockWindowId("main"), true, areas))));
    }

    private static ActionResult rejected(DockFailure.Code code, DockableId id, String message) {
        return new ActionResult.Rejected(new DockFailure(code, id, message, null));
    }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Docking operations require the JavaFX Application Thread");
    }

    record Group(DockGroupId id, TabPane tabs, DockGroupDefinition definition) { }
}
