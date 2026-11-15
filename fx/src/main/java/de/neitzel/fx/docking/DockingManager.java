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
    private final DockTarget fallbackTarget;
    private final java.util.function.Consumer<FloatingWindowConfiguration> floatingWindowCustomizer;
    private final DockDragController drag;
    private final DockCloseCoordinator closing;
    private boolean updating;
    private final Map<DockGroupId, Group> groups = new LinkedHashMap<>();
    private final Map<DockGroupId, Long> groupActivationOrder = new LinkedHashMap<>();
    private final DockLayoutTree tree = new DockLayoutTree(this::publish, id -> !groupClosing(group(id)));
    private final Map<DockAreaId, javafx.geometry.Side> edgeAreas = new LinkedHashMap<>();
    private final java.util.Set<DockGroupId> allocatedGroupIds = new java.util.HashSet<>();
    private final java.util.Set<DockAreaId> allocatedAreaIds = new java.util.HashSet<>();
    private long nextGroupId;
    private long nextWindowId;
    private final Map<DockWindowId, FloatingWindow> floatingWindows = new LinkedHashMap<>();
    private final Map<DockGroupId, DockWindowId> groupWindows = new LinkedHashMap<>();
    private final Map<DockableId, ReturnLocation> returnTargets = new LinkedHashMap<>();
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
            @Override public javafx.stage.Window owner(DockableId id) { return DockingManager.this.owner(containing(id)); }
            @Override public void remove(DockableId id) { removeDockable(id); }
        });
        this.placementRule = Objects.requireNonNull(options.getPlacementRule(), "placementRule");
        this.fallbackTarget = options.getFallbackTarget();
        if (fallbackTarget != null && !(fallbackTarget instanceof DockTarget.Area) && !(fallbackTarget instanceof DockTarget.Group)) {
            throw new IllegalArgumentException("The return fallback must identify an existing main-window area or group");
        }
        this.floatingWindowCustomizer = Objects.requireNonNull(options.getFloatingWindowCustomizer(), "floatingWindowCustomizer");
        DockLayout initial = Objects.requireNonNull(pane.getInitialLayout(), "initialLayout");
        validate(initial);
        addGroup(initial.getCenter(), pane.surface::setCenter);
        addEdgeGroup(initial.getLeft(), javafx.geometry.Side.LEFT);
        addEdgeGroup(initial.getRight(), javafx.geometry.Side.RIGHT);
        addEdgeGroup(initial.getTop(), javafx.geometry.Side.TOP);
        addEdgeGroup(initial.getBottom(), javafx.geometry.Side.BOTTOM);
        tree.view().forEach(area -> allocatedAreaIds.add(area.id()));
        drag = new DockDragController(this, pane, () -> groups.values().stream()
                .filter(group -> !groupWindows.containsKey(group.id))
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
        Group group;
        try {
            group = receiver(destination);
        } catch (FloatingPreparationFailure failure) {
            return new ActionResult.Rejected(new DockFailure(DockFailure.Code.WINDOW_CREATION_FAILED,
                    dockable.getId(), "Floating window configuration failed", failure.getCause()));
        }
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
        dragFor(group).installTab(tab, dockable.getId());
        dockables.put(dockable.getId(), dockable);
        updating = true;
        try {
            group.tabs.getTabs().add(insertionIndex, tab);
            group.tabs.getSelectionModel().select(tab);
            refreshEmptyGroups();
            revealGroup(group);
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
        if (((DockGroupPane) containing(id).tabs).isCollapsed() && !canChangeGroupGeometry(containing(id))) {
            return rejected(DockFailure.Code.CLOSE_IN_PROGRESS, id, "Opening this group would resize a group awaiting close consent");
        }
        boolean revealed = revealGroup(containing(id));
        Node target = focusTarget(dockable);
        Stage window = owner(containing(id));
        boolean changed = revealed || active.get() != dockable || window.getScene().getFocusOwner() != target
                || (window.isShowing() && !window.isFocused());
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
        if (window.isShowing()) window.toFront();
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
            dragFor(group).uninstallTab(tab);
            tab.textProperty().unbind();
            tab.graphicProperty().unbind();
            tab.setGraphic(null);
            tab.setContent(null);
            group.tabs.getTabs().remove(tab);
            dockables.remove(id);
            previousFocus.remove(id);
            activationOrder.remove(id);
            returnTargets.remove(id);
            normalizeLayout(group.id);
        } finally {
            updating = false;
        }
        if (wasActive) {
            Tab selected = group.tabs.getSelectionModel().getSelectedItem();
            if (selected == null) {
                selected = groups.values().stream().filter(this::isGroupVisible)
                        .map(candidate -> candidate.tabs.getSelectionModel().getSelectedItem())
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
                case ResolvedDockTarget.Detachment ignored -> null;
            };
            if (treeFor(containing(id)).normalizationAffects(containing(id).id, destination, this::groupClosing)) {
                return moveRejected(DockFailure.Code.CLOSE_IN_PROGRESS, id,
                        "Removing this tab would resize a group covered by a close request");
            }
            Group source = containing(id);
            if (source.tabs.getTabs().size() == 1 && !source.id.equals(destination)
                    && (source.definition.isReleaseSpaceWhenEmpty() || !source.definition.isPersistent())
                    && !canChangeGroupGeometry(source)) {
                return moveRejected(DockFailure.Code.CLOSE_IN_PROGRESS, id,
                        "Emptying this group would resize a group awaiting close consent");
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
        Group receiver;
        try {
            receiver = receiver(destination);
        } catch (FloatingPreparationFailure failure) {
            return new ActionResult.Rejected(new DockFailure(DockFailure.Code.WINDOW_CREATION_FAILED,
                    id, "Floating window configuration failed", failure.getCause()));
        }
        if (!groupWindows.containsKey(source.id) && groupWindows.containsKey(receiver.id)) {
            int index = tabIndex(source, id);
            TabPosition position = index + 1 < source.tabs.getTabs().size()
                    ? TabPosition.before((DockableId) source.tabs.getTabs().get(index + 1).getUserData())
                    : index > 0 ? TabPosition.after((DockableId) source.tabs.getTabs().get(index - 1).getUserData())
                    : TabPosition.append();
            returnTargets.put(id, new ReturnLocation(DockTarget.group(source.id, position),
                    new DockAreaId(source.definition.getDestinationId())));
        }
        DockDragController sourceDrag = dragFor(source);
        DockDragController receiverDrag = dragFor(receiver);
        int insertionIndex = destination instanceof ResolvedDockTarget.Insertion insertion ? insertion.index() : 0;
        Tab tab = source.tabs.getTabs().get(tabIndex(source, id));
        updating = true;
        try {
            tab.setContent(null);
            if (sourceDrag != receiverDrag) sourceDrag.uninstallTab(tab);
            source.tabs.getTabs().remove(tab);
            receiver.tabs.getTabs().add(insertionIndex, tab);
            if (sourceDrag != receiverDrag) receiverDrag.installTab(tab, id);
            tab.setContent(dockables.get(id).getContent());
            receiver.tabs.getSelectionModel().select(tab);
            normalizeLayout(source.id);
            revealGroup(receiver);
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
        if (target instanceof DockTarget.Floating floating) {
            javafx.geometry.Rectangle2D bounds = floating.bounds() == null
                    ? DockWindowBounds.defaults(stage)
                    : floating.bounds();
            if (!DockWindowBounds.valid(bounds)) return moveRejected(DockFailure.Code.INVALID_WINDOW_BOUNDS, id,
                    "Floating bounds must be finite with positive dimensions");
            return checkPlacement(dockable, new ResolvedDockTarget.Detachment(DockWindowBounds.fit(bounds)));
        }
        if (target instanceof DockTarget.ReturnToMain) {
            Group source = containing(id);
            if (source != null && !groupWindows.containsKey(source.id)) return new MoveCheck.NoChange();
            ReturnLocation remembered = returnTargets.get(id);
            if (remembered != null) {
                MoveCheck result = resolveMainTarget(dockable, remembered.target());
                if (!(result instanceof MoveCheck.Rejected)) return result;
                if (((MoveCheck.Rejected) result).failure().code() == DockFailure.Code.TARGET_NOT_FOUND) {
                    MoveCheck areaResult = resolveMainTarget(dockable, DockTarget.area(remembered.areaId()));
                    if (!(areaResult instanceof MoveCheck.Rejected)) return areaResult;
                }
            }
            if (fallbackTarget != null) return resolveMainTarget(dockable, fallbackTarget);
            List<DockLayoutView.Area> mainAreas = tree.view();
            return mainAreas.isEmpty() ? moveRejected(DockFailure.Code.TARGET_NOT_FOUND, id, "No main-window destination")
                    : resolveMainTarget(dockable, DockTarget.area(mainAreas.getFirst().id()));
        }
        if (target instanceof DockTarget.Split split) {
            Group receiver = group(split.id());
            if (receiver == null) return moveRejected(DockFailure.Code.TARGET_NOT_FOUND, id, "Destination does not exist");
            if (groupClosing(receiver)) return moveRejected(DockFailure.Code.CLOSE_IN_PROGRESS, id, "A close request covers this group");
            if (containing(id) == receiver && receiver.tabs.getTabs().size() == 1) return new MoveCheck.NoChange();
            if (((DockGroupPane) receiver.tabs).isCollapsed() && !canChangeGroupGeometry(receiver)) {
                return moveRejected(DockFailure.Code.CLOSE_IN_PROGRESS, id,
                        "Opening this group would resize a group awaiting close consent");
            }
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
            List<Group> candidates = java.util.stream.Stream.concat(java.util.stream.Stream.of(tree),
                    floatingWindows.values().stream().map(FloatingWindow::tree))
                    .flatMap(candidate -> candidate.groups(area.id()).stream()).sorted(java.util.Comparator
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
        if (((DockGroupPane) receiver.tabs).isCollapsed() && !canChangeGroupGeometry(receiver)) {
            return moveRejected(DockFailure.Code.CLOSE_IN_PROGRESS, id,
                    "Opening this group would resize a group awaiting close consent");
        }
        var resolved = new ResolvedDockTarget.Insertion(receiver.id, index);
        return checkPlacement(dockable, resolved);
    }

    private MoveCheck resolveMainTarget(Dockable dockable, DockTarget target) {
        MoveCheck result = resolve(dockable, target);
        if (result instanceof MoveCheck.NoChange && groupWindows.containsKey(containing(dockable.getId()).id)) {
            return moveRejected(DockFailure.Code.TARGET_NOT_FOUND, dockable.getId(), "The return destination is not in the main window");
        }
        if (result instanceof MoveCheck.Allowed allowed
                && allowed.destination() instanceof ResolvedDockTarget.Insertion insertion
                && groupWindows.containsKey(insertion.groupId())) {
            return moveRejected(DockFailure.Code.TARGET_NOT_FOUND, dockable.getId(), "The return destination is not in the main window");
        }
        return result;
    }

    private MoveCheck checkPlacement(Dockable dockable, ResolvedDockTarget resolved) {
        DockableId id = dockable.getId();
        boolean floating = switch (resolved) {
            case ResolvedDockTarget.Detachment ignored -> true;
            case ResolvedDockTarget.Insertion insertion -> groupWindows.containsKey(insertion.groupId());
            case ResolvedDockTarget.Split split -> groupWindows.containsKey(split.groupId());
        };
        if (dockable.isMainWindowOnly() && floating) {
            return moveRejected(DockFailure.Code.MAIN_WINDOW_ONLY, id, "This Dockable must remain in the main window");
        }
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
        DockWindowId ownerId = groupWindows.get(source);
        for (DockGroupId id : treeFor(group(source)).normalize(source)) {
            groups.remove(id);
            groupWindows.remove(id);
            groupActivationOrder.remove(id);
        }
        refreshEmptyGroups();
        if (ownerId != null && groups.values().stream().filter(group -> ownerId.equals(groupWindows.get(group.id)))
                .allMatch(group -> group.tabs.getTabs().isEmpty())) {
            FloatingWindow empty = floatingWindows.remove(ownerId);
            empty.drag.dispose();
            empty.geometry.dispose();
            empty.stage.getScene().focusOwnerProperty().removeListener(focusListener);
            empty.stage.hide();
            empty.pane.manager = null;
            empty.stage.getProperties().remove(ATTACHMENT_KEY);
            var remaining = groups.keySet().stream().filter(id -> ownerId.equals(groupWindows.get(id))).toList();
            remaining.forEach(id -> { groups.remove(id); groupWindows.remove(id); groupActivationOrder.remove(id); });
        }
    }

    /** Applies explicitly supplied settings as one validated group change. */
    public ActionResult updateGroup(DockGroupId id, DockGroupChanges changes) {
        requireFxThread();
        Objects.requireNonNull(changes, "changes");
        Group group = group(Objects.requireNonNull(id, "id"));
        if (group == null) return rejected(DockFailure.Code.TARGET_NOT_FOUND, null, "Group does not exist");
        if (groupClosing(group)) return rejected(DockFailure.Code.CLOSE_IN_PROGRESS, null, "A close request covers this group");
        DockGroupChanges resolved = changes.resolved(group.definition);
        DockGroupDefinition candidate = new DockGroupDefinition(group.definition.getDestinationId());
        resolved.applyTo(candidate);
        try {
            validate(candidate, new java.util.HashSet<>());
        } catch (IllegalArgumentException failure) {
            return rejected(DockFailure.Code.INVALID_GROUP_CONFIGURATION, null, failure.getMessage());
        }
        DockEdgeRegion extentOwner = null;
        for (Node ancestor = group.tabs.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor instanceof DockEdgeRegion edge) {
                extentOwner = edge;
                break;
            }
        }
        boolean changesExtent = extentOwner != null
                && extentOwner.changesPreferredExtent(changes.getPreferredWidth(), changes.getPreferredHeight());
        if (!changesExtent && resolved.equals(DockGroupChanges.builder().build().resolved(group.definition))) {
            return new ActionResult.NoChange();
        }
        DockGroupDefinition existing = group.definition;
        boolean changesGeometry = changesExtent || existing.getTabSide() != candidate.getTabSide()
                || existing.getPreferredWidth() != candidate.getPreferredWidth()
                || existing.getPreferredHeight() != candidate.getPreferredHeight()
                || existing.getMinimumWidth() != candidate.getMinimumWidth()
                || existing.getMinimumHeight() != candidate.getMinimumHeight()
                || (group.tabs.getTabs().isEmpty() && (existing.isVisibleWhenEmpty() != candidate.isVisibleWhenEmpty()
                    || existing.isReleaseSpaceWhenEmpty() != candidate.isReleaseSpaceWhenEmpty()))
                || (((DockGroupPane) group.tabs).isCollapsed() && !candidate.isCollapsible());
        if (changesGeometry && !canChangeGroupGeometry(group, true)) {
            return rejected(DockFailure.Code.CLOSE_IN_PROGRESS, null, "The group change would resize content covered by a close request");
        }
        boolean wasUpdating = updating;
        updating = true;
        try {
            resolved.applyTo(group.definition);
            group.tabs.setSide(candidate.getTabSide());
            group.tabs.requestLayout();
            if (extentOwner != null) {
                extentOwner.updatePreferredExtent(changes.getPreferredWidth(), changes.getPreferredHeight());
            }
            if (!candidate.isCollapsible() && revealGroup(group)) {
                Tab selected = group.tabs.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    setActive(dockables.get((DockableId) selected.getUserData()));
                    focus(active.get());
                }
            }
            refreshEmptyGroups();
        } finally {
            updating = wasUpdating;
        }
        publish();
        return new ActionResult.Applied();
    }

    public ActionResult setGroupCollapsed(DockGroupId id, boolean collapsed) {
        requireFxThread();
        Group group = group(Objects.requireNonNull(id, "id"));
        if (group == null) return rejected(DockFailure.Code.TARGET_NOT_FOUND, null, "Group does not exist");
        if (groupClosing(group)) return rejected(DockFailure.Code.CLOSE_IN_PROGRESS, null, "A close request covers this group");
        if (collapsed && !group.definition.isCollapsible()) {
            return rejected(DockFailure.Code.COLLAPSE_DISABLED, null, "This group cannot be collapsed");
        }
        DockGroupPane tabs = (DockGroupPane) group.tabs;
        if (tabs.isCollapsed() == collapsed) return new ActionResult.NoChange();
        if (!canChangeGroupGeometry(group)) {
            return rejected(DockFailure.Code.CLOSE_IN_PROGRESS, null, "The group change would resize content covered by a close request");
        }
        tabs.setCollapsed(collapsed);
        if (collapsed) {
            if (active.get() != null && containing(active.get().getId()) == group) setActive(selectedVisibleFallback());
            tabs.focusSelectedHeader();
        } else {
            Tab selected = tabs.getSelectionModel().getSelectedItem();
            if (selected != null) {
                setActive(dockables.get((DockableId) selected.getUserData()));
                focus(active.get());
            }
        }
        publish();
        return new ActionResult.Applied();
    }

    private boolean revealGroup(Group group) {
        DockGroupPane tabs = (DockGroupPane) group.tabs;
        if (!tabs.isCollapsed()) return false;
        tabs.setCollapsed(false);
        return true;
    }

    private boolean isGroupVisible(Group group) {
        return group.tabs.isVisible() && !((DockGroupPane) group.tabs).isCollapsed();
    }

    private Dockable selectedVisibleFallback() {
        return groups.values().stream().filter(this::isGroupVisible)
                .map(candidate -> candidate.tabs.getSelectionModel().getSelectedItem()).filter(Objects::nonNull)
                .max(java.util.Comparator.comparingLong(tab -> activationOrder.getOrDefault((DockableId) tab.getUserData(), 0L)))
                .map(tab -> dockables.get((DockableId) tab.getUserData())).orElse(null);
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
                () -> canResizeEdge(areaId, side), this::publish));
    }

    private boolean canResizeEdge(DockAreaId areaId, javafx.geometry.Side side) {
        if (tree.groups(areaId).stream().anyMatch(this::groupClosing)) return false;
        for (Group candidate : groups.values()) {
            if (groupWindows.containsKey(candidate.id) || !groupClosing(candidate)) continue;
            if (contains(pane.surface.getCenter(), candidate.tabs)) return false;
            javafx.geometry.Side otherSide = pane.edgeOf(candidate.tabs);
            if (side.isHorizontal() && otherSide != null && otherSide.isVertical()) return false;
        }
        return true;
    }

    private boolean canChangeGroupGeometry(Group group) {
        return canChangeGroupGeometry(group, false);
    }

    private boolean canChangeGroupGeometry(Group group, boolean changingOuterExtent) {
        if (treeFor(group).geometryAffects(group.id, this::groupClosing)) return false;
        if (groupWindows.containsKey(group.id)) return true;
        for (var entry : edgeAreas.entrySet()) {
            List<Group> areaGroups = tree.groups(entry.getKey());
            if (!areaGroups.contains(group)) continue;
            boolean onlyExpandedGroup = areaGroups.stream().filter(candidate -> candidate != group)
                    .allMatch(candidate -> !candidate.tabs.isManaged() || ((DockGroupPane) candidate.tabs).isCollapsed());
            if (changingOuterExtent || onlyExpandedGroup) return canResizeEdge(entry.getKey(), entry.getValue());
        }
        return true;
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
        captured.setCollapsible(definition.isCollapsible());
        captured.setVisibleWhenEmpty(definition.isVisibleWhenEmpty());
        captured.setReleaseSpaceWhenEmpty(definition.isReleaseSpaceWhenEmpty());
        TabPane tabs = new DockGroupPane(captured);
        tabs.setSide(captured.getTabSide());
        Group group = new Group(id, tabs, captured);
        allocatedGroupIds.add(id);
        groups.put(id, group);
        ((DockGroupPane) tabs).installHeaderActions(tab -> activate((DockableId) tab.getUserData()),
                () -> setGroupCollapsed(id, true));
        tabs.getSelectionModel().selectedItemProperty().addListener((observable, old, selected) -> {
            if (!updating && selected != null && isGroupVisible(group)) {
                setActive(dockables.get((DockableId) selected.getUserData()));
                focus(active.get());
                publish();
            }
        });
        return group;
    }

    private Group receiver(ResolvedDockTarget destination) {
        if (destination instanceof ResolvedDockTarget.Insertion insertion) return group(insertion.groupId());
        if (destination instanceof ResolvedDockTarget.Detachment detachment) return createFloatingGroup(detachment.bounds());
        ResolvedDockTarget.Split split = (ResolvedDockTarget.Split) destination;
        Group target = group(split.groupId());
        DockGroupId id;
        do { id = new DockGroupId("group-" + ++nextGroupId); } while (allocatedGroupIds.contains(id));
        Group added = createGroup(id, target.definition);
        DockWindowId window = groupWindows.get(target.id);
        if (window != null) groupWindows.put(added.id, window);
        treeFor(target).split(target, added, split.side());
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
            if (contains(dockable.getContent(), current) && isGroupVisible(containing(dockable.getId()))) {
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
        Stage window = owner(containing(dockable.getId()));
        window.getScene().getRoot().applyCss();
        window.getScene().getRoot().layout();
        if (window != stage && !window.isShowing()) window.show();
        if (window.isShowing()) {
            window.toFront();
            window.requestFocus();
        }
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
            for (FloatingWindow window : floatingWindows.values()) {
                window.pane.applyCss();
                window.pane.layout();
                window.geometry.captureIfReady();
                if (!window.geometry.hasPendingChanges()) window.tree.captureGeometry();
            }
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
        var windows = new java.util.ArrayList<DockLayoutView.Window>();
        windows.add(new DockLayoutView.Window(new DockWindowId("main"), true, areas));
        floatingWindows.values().forEach(window -> windows.add(new DockLayoutView.Window(
                window.id, false, window.tree.view(), window.geometry.bounds())));
        layout.set(new DockLayoutView(windows));
    }

    private Stage owner(Group group) {
        DockWindowId id = group == null ? null : groupWindows.get(group.id);
        return id == null ? stage : floatingWindows.get(id).stage;
    }

    private DockLayoutTree treeFor(Group group) {
        DockWindowId id = groupWindows.get(group.id);
        return id == null ? tree : floatingWindows.get(id).tree;
    }

    private DockDragController dragFor(Group group) {
        DockWindowId id = groupWindows.get(group.id);
        return id == null ? drag : floatingWindows.get(id).drag;
    }

    private Group createFloatingGroup(javafx.geometry.Rectangle2D bounds) {
        FloatingWindowConfiguration configuration = new FloatingWindowConfiguration();
        try {
            floatingWindowCustomizer.accept(configuration);
            configuration.getIcons().forEach(Objects::requireNonNull);
            configuration.getStylesheets().forEach(Objects::requireNonNull);
        } catch (RuntimeException failure) {
            throw new FloatingPreparationFailure(failure);
        }
        DockWindowId candidateId;
        do { candidateId = new DockWindowId("floating-" + ++nextWindowId); }
        while (!allocatedAreaIds.add(new DockAreaId(candidateId.value())));
        DockWindowId windowId = candidateId;
        DockPane floatingPane = new DockPane();
        Stage floatingStage = new Stage();
        floatingStage.setScene(new javafx.scene.Scene(floatingPane));
        floatingStage.setTitle(configuration.getTitle());
        floatingStage.getIcons().setAll(configuration.getIcons());
        floatingStage.getScene().getStylesheets().setAll(configuration.getStylesheets());
        floatingStage.setX(bounds.getMinX());
        floatingStage.setY(bounds.getMinY());
        floatingStage.setWidth(bounds.getWidth());
        floatingStage.setHeight(bounds.getHeight());
        DockLayoutTree floatingTree = new DockLayoutTree(this::publish, id -> !groupClosing(group(id)));
        DockGroupId groupId;
        do { groupId = new DockGroupId("group-" + ++nextGroupId); } while (allocatedGroupIds.contains(groupId));
        DockGroupDefinition definition = new DockGroupDefinition(windowId.value());
        definition.setPersistent(false);
        Group group = createGroup(groupId, definition);
        groupWindows.put(group.id, windowId);
        floatingTree.addArea(definition, floatingPane.surface::setCenter, ignored -> group);
        DockDragController floatingDrag = new DockDragController(this, floatingPane, () -> groups.values().stream()
                .filter(candidate -> windowId.equals(groupWindows.get(candidate.id)))
                .map(candidate -> new DockDragController.GroupSurface(candidate.id, candidate.tabs)).toList());
        DockWindowGeometry geometry = new DockWindowGeometry(floatingStage, this::publish);
        floatingWindows.put(windowId, new FloatingWindow(windowId, floatingStage, floatingPane, floatingTree, floatingDrag, geometry));
        floatingPane.manager = this;
        floatingStage.getProperties().put(ATTACHMENT_KEY, this);
        floatingStage.getScene().focusOwnerProperty().addListener(focusListener);
        return group;
    }

    private static final class FloatingPreparationFailure extends RuntimeException {
        FloatingPreparationFailure(RuntimeException cause) { super(cause); }
    }

    private record FloatingWindow(DockWindowId id, Stage stage, DockPane pane, DockLayoutTree tree,
                                  DockDragController drag, DockWindowGeometry geometry) { }
    private record ReturnLocation(DockTarget target, DockAreaId areaId) { }

    private static ActionResult rejected(DockFailure.Code code, DockableId id, String message) {
        return new ActionResult.Rejected(new DockFailure(code, id, message, null));
    }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Docking operations require the JavaFX Application Thread");
    }

    record Group(DockGroupId id, TabPane tabs, DockGroupDefinition definition) { }
}
