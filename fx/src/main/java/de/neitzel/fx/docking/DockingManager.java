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
    private final javafx.scene.Scene mainScene;
    private final MainWindowCloseMode mainWindowCloseMode;
    private final java.util.function.Function<ShutdownRequest, java.util.concurrent.CompletionStage<ShutdownDecision>> beforeShutdown;
    private boolean disposed;
    private final PlacementRule placementRule;
    private final DockMessages messages;
    private final DockTarget fallbackTarget;
    private final java.util.function.Consumer<FloatingWindowConfiguration> floatingWindowCustomizer;
    private final DockDragController drag;
    private final DockCloseCoordinator closing;
    private boolean updating;
    private boolean layoutChanging;
    private final Map<DockGroupId, Group> groups = new LinkedHashMap<>();
    private final Map<DockGroupId, Long> groupActivationOrder = new LinkedHashMap<>();
    private final DockLayoutTree tree = new DockLayoutTree(this::publish, id -> !groupClosing(group(id)));
    private final Map<DockAreaId, javafx.geometry.Side> edgeAreas = new LinkedHashMap<>();
    private final java.util.Set<DockGroupId> allocatedGroupIds = new java.util.HashSet<>();
    private final java.util.Set<DockAreaId> allocatedAreaIds = new java.util.HashSet<>();
    private final java.util.Set<DockAreaId> initialAreaIds = new java.util.HashSet<>();
    private final LayoutSnapshot.Window initialWindow;
    private long nextGroupId;
    private long nextWindowId;
    private long nextWindowFocusOrder;
    private final Map<DockWindowId, FloatingWindow> floatingWindows = new LinkedHashMap<>();
    private final Map<DockGroupId, DockWindowId> groupWindows = new LinkedHashMap<>();
    private final Map<DockableId, ReturnLocation> returnTargets = new LinkedHashMap<>();
    private final Map<DockableId, DockAreaId> initialIntents = new LinkedHashMap<>();
    private final Map<DockableId, Dockable> dockables = new LinkedHashMap<>();
    private final Map<DockableId, Node> previousFocus = new LinkedHashMap<>();
    private final Map<DockableId, Long> activationOrder = new LinkedHashMap<>();
    private long nextActivation;
    private final javafx.beans.value.ChangeListener<Node> focusListener = (observable, old, current) -> rememberFocus(current);
    private final ReadOnlyObjectWrapper<Dockable> active = new ReadOnlyObjectWrapper<>(this, "activeDockable");
    private final ReadOnlyObjectWrapper<DockLayoutView> layout = new ReadOnlyObjectWrapper<>(this, "layout");
    private LayoutSnapshot publishedSnapshot;
    private final javafx.event.EventHandler<javafx.stage.WindowEvent> mainCloseHandler = event -> {
        event.consume();
        requestMainWindowClose();
    };
    private final javafx.event.EventHandler<javafx.stage.WindowEvent> mainShownHandler = event -> {
        boolean wasUpdating = updating;
        updating = true;
        try {
            floatingWindows.values().forEach(window -> window.stage.show());
            focus(active.get());
        } finally { updating = wasUpdating; }
    };

    private DockingManager(Stage stage, DockPane pane, DockingOptions options) {
        this.stage = stage;
        this.pane = pane;
        this.mainScene = stage.getScene();
        this.mainWindowCloseMode = Objects.requireNonNull(options.getMainWindowCloseMode(), "mainWindowCloseMode");
        this.beforeShutdown = Objects.requireNonNull(options.getBeforeShutdown(), "beforeShutdown");
        this.messages = new DockMessages(options.getResourceBundle());
        closing = new DockCloseCoordinator(new DockCloseCoordinator.Host() {
            @Override public Optional<Dockable> find(DockableId id) { return findDockable(id); }
            @Override public List<DockableId> allDockables() { return List.copyOf(dockables.keySet()); }
            @Override public javafx.stage.Window owner(DockableId id) { return DockingManager.this.owner(containing(id)); }
            @Override public Optional<List<DockableId>> floatingDockables(DockWindowId id) {
                FloatingWindow window = floatingWindows.get(id);
                if (window == null) return Optional.empty();
                return Optional.of(window.tree.view().stream().flatMap(area -> area.groups().stream())
                        .map(DockLayoutView.Group::id).distinct().map(DockingManager.this::group)
                        .flatMap(group -> group.tabs.getTabs().stream())
                        .map(tab -> (DockableId) tab.getUserData()).toList());
            }
            @Override public void remove(List<DockableId> ids) { removeDockables(ids); }
            @Override public java.util.concurrent.CompletionStage<ShutdownDecision> beforeShutdown(CloseScope.Kind kind) {
                return DockingManager.this.beforeShutdown.apply(new ShutdownRequest(snapshot(),
                        kind == CloseScope.Kind.MAIN_WINDOW ? ShutdownReason.MAIN_WINDOW : ShutdownReason.MANAGER_ONLY));
            }
            @Override public void endManager(CloseScope.Kind kind) { releaseManager(kind); }
        });
        this.placementRule = Objects.requireNonNull(options.getPlacementRule(), "placementRule");
        this.fallbackTarget = options.getFallbackTarget();
        if (fallbackTarget != null && !(fallbackTarget instanceof DockTarget.Area) && !(fallbackTarget instanceof DockTarget.Group)) {
            throw new IllegalArgumentException("The return fallback must identify an existing main-window area or group");
        }
        this.floatingWindowCustomizer = Objects.requireNonNull(options.getFloatingWindowCustomizer(), "floatingWindowCustomizer");
        DockLayout initial = Objects.requireNonNull(pane.getInitialLayout(), "initialLayout");
        validate(initial);
        pane.overlays.resume();
        addGroup(initial.getCenter(), pane.surface::setCenter);
        addEdgeGroup(initial.getLeft(), javafx.geometry.Side.LEFT);
        addEdgeGroup(initial.getRight(), javafx.geometry.Side.RIGHT);
        addEdgeGroup(initial.getTop(), javafx.geometry.Side.TOP);
        addEdgeGroup(initial.getBottom(), javafx.geometry.Side.BOTTOM);
        tree.view().forEach(area -> allocatedAreaIds.add(area.id()));
        initialAreaIds.addAll(allocatedAreaIds);
        drag = new DockDragController(this, pane, () -> groups.values().stream()
                .filter(group -> !groupWindows.containsKey(group.id))
                .map(group -> new DockDragController.GroupSurface(group.id, group.tabs, pane.edgeOf(group.tabs))).toList());
        refreshEmptyGroups();
        publish();
        initialWindow = snapshot().windows().getFirst();
        mainScene.focusOwnerProperty().addListener(focusListener);
        stage.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWN, mainShownHandler);
        if (mainWindowCloseMode == MainWindowCloseMode.AUTOMATIC) {
            stage.addEventFilter(javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST, mainCloseHandler);
        }
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
        if (disposed) return new ActionResult.Rejected(managerClosedFailure());
        if (globalClosePending()) return rejected(DockFailure.Code.CLOSE_IN_PROGRESS, null, "A manager-wide close is pending");
        if (layoutChanging) return new ActionResult.Rejected(layoutChangeFailure());
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
        Tab tab = prepareTab(dockable);
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
        DockAreaId initialArea = target instanceof DockTarget.Area area && initialAreaIds.contains(area.id())
                ? area.id() : new DockAreaId(group.definition.getDestinationId());
        if (initialAreaIds.contains(initialArea)) initialIntents.put(dockable.getId(), initialArea);
        publish();
        return new ActionResult.Applied();
    }

    private Tab prepareTab(Dockable dockable) {
        Tab tab = new Tab();
        tab.setClosable(true);
        tab.setOnCloseRequest(event -> {
            event.consume();
            requestTabClose(dockable.getId());
        });
        DockTabPresentation.bind(tab, dockable, message("dock.action.close"));
        tab.graphicProperty().bind(dockable.iconProperty());
        tab.setContent(dockable.getContent());
        tab.setUserData(dockable.getId());
        return tab;
    }

    /** Creates content only for a new identity; rejected prepared content is discarded. */
    public ActionResult open(DockableId id, java.util.function.Supplier<? extends Dockable> factory, DockTarget target) {
        requireFxThread();
        if (disposed) return new ActionResult.Rejected(managerClosedFailure());
        if (globalClosePending()) return rejected(DockFailure.Code.CLOSE_IN_PROGRESS, null, "A manager-wide close is pending");
        if (layoutChanging) return new ActionResult.Rejected(layoutChangeFailure());
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
                && prepared.getContent().getParent() == null && prepared.getContent().getScene() == null
                && prepared.getOnDiscarded() != null) {
            try {
                prepared.getOnDiscarded().run();
            } catch (RuntimeException cleanup) {
                DockFailure failure = rejected.failure();
                var cause = new IllegalStateException("Cleanup failed after rejected open", failure.cause());
                cause.addSuppressed(cleanup);
                return new ActionResult.Rejected(new DockFailure(failure.code(), failure.dockableId(), failure.message(), cause, failure.messageKey()));
            }
        }
        return result;
    }

    /** Restores the captured starting arrangement while retaining every live Dockable. */
    public ActionResult resetLayout() {
        requireFxThread();
        if (disposed) return new ActionResult.Rejected(managerClosedFailure());
        if (layoutChanging) return rejected(DockFailure.Code.LAYOUT_CHANGE_IN_PROGRESS, null,
                "A layout replacement is already being prepared");
        if (closing.scope() != null) return rejected(DockFailure.Code.CLOSE_IN_PROGRESS, null,
                "Cannot reset while close consent is pending");
        layoutChanging = true;
        try {
            LayoutSnapshot.Window reset = LayoutSnapshot.remapGroups(initialWindow, original -> {
                if (groups.containsKey(original)) return original;
                DockGroupId fresh;
                do { fresh = new DockGroupId("group-" + ++nextGroupId); } while (allocatedGroupIds.contains(fresh));
                return fresh;
            });
            DockLayoutCandidate candidate = prepareLayoutCandidate(reset);
            Map<DockableId, Dockable> retained = new LinkedHashMap<>(dockables);
            DockFailure failure = DockResetPlan.prepare(candidate, List.copyOf(retained.values()),
                    initialIntents, fallbackTarget, this::checkPlacement);
            if (failure != null) return new ActionResult.Rejected(failure);
            candidate.normalize();
            DockableId activeId = active.get() == null ? null : active.get().getId();
            if (activeId != null) {
                candidate.groups().stream().filter(group -> candidate.tabs(group.id).contains(activeId))
                        .findFirst().ifPresent(group -> candidate.select(group.id, activeId));
            }
            returnTargets.clear();
            installLayoutCandidate(candidate, retained, activeId);
            return new ActionResult.Applied();
        } finally {
            layoutChanging = false;
        }
    }

    public ActionResult activate(DockableId id) {
        requireFxThread();
        if (disposed) return new ActionResult.Rejected(managerClosedFailure());
        if (layoutChanging) return new ActionResult.Rejected(layoutChangeFailure());
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

    /** Uses the same captured language settings and fallback as built-in menu rejection text. */
    public String describeFailure(DockFailure failure) { return messages.failure(failure); }

    String message(String key) { return messages.text(key); }
    String message(String key, Object... values) { return messages.format(key, values); }

    /** Requests consent without closing or releasing content while the answer is pending. */
    public java.util.concurrent.CompletionStage<CloseResult> requestTabClose(DockableId id) {
        requireFxThread();
        if (disposed && closing.scope() == null) return java.util.concurrent.CompletableFuture.completedFuture(new CloseResult.NoChange());
        if (layoutChanging) return java.util.concurrent.CompletableFuture.completedFuture(new CloseResult.Rejected(layoutChangeFailure()));
        return closing.requestTabClose(id);
    }

    /** Requests every contained Dockable's consent before closing a floating window. */
    public java.util.concurrent.CompletionStage<CloseResult> requestFloatingWindowClose(DockWindowId id) {
        requireFxThread();
        if (disposed && closing.scope() == null) return java.util.concurrent.CompletableFuture.completedFuture(new CloseResult.NoChange());
        if (layoutChanging) return java.util.concurrent.CompletableFuture.completedFuture(new CloseResult.Rejected(layoutChangeFailure()));
        Objects.requireNonNull(id, "id");
        if (layout.get().windows().getFirst().id().equals(id)) {
            return java.util.concurrent.CompletableFuture.completedFuture(new CloseResult.Rejected(
                    new DockFailure(DockFailure.Code.TARGET_NOT_FOUND, null, "The requested window is not floating", null)));
        }
        return closing.requestFloatingWindowClose(id);
    }

    /** Collects consent and saves the complete layout before closing this manager's windows. */
    public java.util.concurrent.CompletionStage<CloseResult> requestMainWindowClose() {
        requireFxThread();
        if (disposed && closing.scope() == null) return java.util.concurrent.CompletableFuture.completedFuture(new CloseResult.NoChange());
        if (layoutChanging) return java.util.concurrent.CompletableFuture.completedFuture(new CloseResult.Rejected(layoutChangeFailure()));
        return closing.requestShutdown(CloseScope.Kind.MAIN_WINDOW);
    }

    /** Ends this embedded manager while preserving the application's main Stage. */
    public java.util.concurrent.CompletionStage<CloseResult> requestManagerClose() {
        requireFxThread();
        if (disposed && closing.scope() == null) return java.util.concurrent.CompletableFuture.completedFuture(new CloseResult.NoChange());
        if (layoutChanging) return java.util.concurrent.CompletableFuture.completedFuture(new CloseResult.Rejected(layoutChangeFailure()));
        return closing.requestShutdown(CloseScope.Kind.MANAGER_ONLY);
    }

    /** Scope remains available until checks and resource release have finished. */
    public ReadOnlyObjectProperty<CloseScope> closingScopeProperty() {
        return closing.closingScopeProperty();
    }

    private boolean globalClosePending() {
        CloseScope scope = closing.scope();
        return scope != null && (scope.kind() == CloseScope.Kind.MAIN_WINDOW || scope.kind() == CloseScope.Kind.MANAGER_ONLY);
    }

    private DockFailure managerClosedFailure() {
        return new DockFailure(DockFailure.Code.MANAGER_CLOSED, null, "The docking manager has ended", null);
    }

    private void releaseManager(CloseScope.Kind kind) {
        if (disposed) return;
        disposed = true;
        boolean wasUpdating = updating;
        updating = true;
        try {
            drag.dispose();
            mainScene.focusOwnerProperty().removeListener(focusListener);
            stage.removeEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWN, mainShownHandler);
            stage.removeEventFilter(javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST, mainCloseHandler);
            floatingWindows.values().forEach(this::disposeFloatingWindow);
            floatingWindows.clear();
            groupWindows.clear();
            tree.clear();
            pane.clearAreas();
            pane.overlays.dispose();
            groups.clear();
            edgeAreas.clear();
            groupActivationOrder.clear();
            previousFocus.clear();
            activationOrder.clear();
            pane.manager = null;
            if (stage.getProperties().get(ATTACHMENT_KEY) == this) stage.getProperties().remove(ATTACHMENT_KEY);
        } finally { updating = wasUpdating; }
        publish();
        if (kind == CloseScope.Kind.MAIN_WINDOW && mainWindowCloseMode == MainWindowCloseMode.AUTOMATIC) stage.hide();
    }

    private void detachTab(Group group, Tab tab) {
        dragFor(group).uninstallTab(tab);
        DockTabPresentation.unbind(tab);
        tab.textProperty().unbind();
        tab.graphicProperty().unbind();
        tab.setGraphic(null);
        tab.setContent(null);
    }

    private void removeDockables(List<DockableId> ids) {
        Dockable previousActive = active.get();
        Group previousGroup = previousActive == null ? null : containing(previousActive.getId());
        updating = true;
        try {
            for (DockableId id : ids) {
                Group group = containing(id);
                if (group == null) continue;
                Tab tab = group.tabs.getTabs().get(tabIndex(group, id));
                detachTab(group, tab);
                group.tabs.getTabs().remove(tab);
                dockables.remove(id);
                previousFocus.remove(id);
                activationOrder.remove(id);
                returnTargets.remove(id);
                initialIntents.remove(id);
                normalizeLayout(group.id);
            }
        } finally {
            updating = false;
        }
        if (previousActive != null && ids.contains(previousActive.getId())) {
            Tab selected = previousGroup == null ? null : previousGroup.tabs.getSelectionModel().getSelectedItem();
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
        if (layoutChanging) return true;
        CloseScope scope = closing.scope();
        if (scope == null) return false;
        if (scope.kind() == CloseScope.Kind.MAIN_WINDOW || scope.kind() == CloseScope.Kind.MANAGER_ONLY) return true;
        if (scope.kind() == CloseScope.Kind.FLOATING_WINDOW) {
            return group != null && closing.coversWindow(groupWindows.get(group.id));
        }
        for (DockableId id : scope.dockableIds()) {
            if (!dockables.containsKey(id) || (group != null && tabIndex(group, id) >= 0)) return true;
        }
        return false;
    }

    /** Resolves a move without mutating content, layout or identity allocation. */
    public MoveCheck checkMove(DockableId id, DockTarget target) {
        requireFxThread();
        if (disposed) return new MoveCheck.Rejected(managerClosedFailure());
        if (layoutChanging) return new MoveCheck.Rejected(layoutChangeFailure());
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
            if (sourceDrag != receiverDrag) receiverDrag.installTab(tab, id);
            receiver.tabs.getTabs().add(insertionIndex, tab);
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
            MoveCheck refusal = moveRejected(DockFailure.Code.TARGET_NOT_FOUND, id,
                    "No remembered main-window destination or designated fallback");
            if (remembered != null) {
                MoveCheck result = resolveMainTarget(dockable, remembered.target());
                if (!(result instanceof MoveCheck.Rejected)) return result;
                refusal = result;
                DockFailure.Code code = ((MoveCheck.Rejected) result).failure().code();
                if (code == DockFailure.Code.TARGET_NOT_FOUND || code == DockFailure.Code.REFERENCE_TAB_NOT_FOUND) {
                    MoveCheck areaResult = resolveMainTarget(dockable, DockTarget.area(remembered.areaId()));
                    if (!(areaResult instanceof MoveCheck.Rejected)) return areaResult;
                    refusal = areaResult;
                }
            }
            return fallbackTarget == null ? refusal : resolveMainTarget(dockable, fallbackTarget);
        }
        if (target instanceof DockTarget.Split split) {
            Group receiver = group(split.id());
            if (receiver == null) return moveRejected(DockFailure.Code.TARGET_NOT_FOUND, id, "Destination does not exist");
            if (groupClosing(receiver)) return moveRejected(DockFailure.Code.CLOSE_IN_PROGRESS, id, "A close request covers this group");
            if (containing(id) == receiver && receiver.tabs.getTabs().size() == 1) return new MoveCheck.NoChange();
            if ((((DockGroupPane) receiver.tabs).isCollapsed() || ((DockGroupPane) receiver.tabs).isOverlay())
                    && !canChangeGroupGeometry(receiver)) {
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
        if ((((DockGroupPane) receiver.tabs).isCollapsed() || ((DockGroupPane) receiver.tabs).isOverlay())
                    && !canChangeGroupGeometry(receiver)) {
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
            disposeFloatingWindow(empty);
            var remaining = groups.keySet().stream().filter(id -> ownerId.equals(groupWindows.get(id))).toList();
            remaining.forEach(id -> { groups.remove(id); groupWindows.remove(id); groupActivationOrder.remove(id); });
        }
    }

    private void disposeFloatingWindow(FloatingWindow window) {
        window.stage.removeEventFilter(javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST, window.closeHandler);
        window.drag.dispose();
        window.pane.overlays.dispose();
        window.geometry.dispose();
        window.stage.getScene().focusOwnerProperty().removeListener(focusListener);
        window.stage.hide();
        window.pane.manager = null;
        window.stage.getProperties().remove(ATTACHMENT_KEY);
    }

    /** Applies explicitly supplied settings as one validated group change. */
    public ActionResult updateGroup(DockGroupId id, DockGroupChanges changes) {
        requireFxThread();
        if (disposed) return new ActionResult.Rejected(managerClosedFailure());
        if (layoutChanging) return new ActionResult.Rejected(layoutChangeFailure());
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
        for (Node ancestor = ((DockGroupPane) group.tabs).surface().getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor instanceof DockEdgeRegion edge) {
                extentOwner = edge;
                break;
            }
        }
        DockGroupSurface groupSurface = ((DockGroupPane) group.tabs).surface();
        if (extentOwner != null && !groupSurface.spansEdgeExtent(extentOwner.side())) extentOwner = null;
        boolean changesExtent = (extentOwner != null
                && extentOwner.changesPreferredExtent(changes.getPreferredWidth(), changes.getPreferredHeight()))
                || groupSurface.changesPreferredSize(changes.getPreferredWidth(), changes.getPreferredHeight());
        if (!changesExtent && resolved.equals(DockGroupChanges.builder().build().resolved(group.definition))) {
            return new ActionResult.NoChange();
        }
        DockGroupDefinition existing = group.definition;
        boolean changesGeometry = changesExtent || existing.getMode() != candidate.getMode()
                || existing.getTabSide() != candidate.getTabSide()
                || existing.getPreferredWidth() != candidate.getPreferredWidth()
                || existing.getPreferredHeight() != candidate.getPreferredHeight()
                || existing.getMinimumWidth() != candidate.getMinimumWidth()
                || existing.getMinimumHeight() != candidate.getMinimumHeight()
                || (group.tabs.getTabs().isEmpty() && (existing.isVisibleWhenEmpty() != candidate.isVisibleWhenEmpty()
                    || existing.isReleaseSpaceWhenEmpty() != candidate.isReleaseSpaceWhenEmpty()))
                || (((DockGroupPane) group.tabs).isCollapsed() && !candidate.isCollapsible());
        if (candidate.getMode() == DockGroupMode.OVERLAY && !((DockGroupPane) group.tabs).isCollapsed()
                && openOverlays(group).stream().anyMatch(this::groupClosing)) {
            return rejected(DockFailure.Code.CLOSE_IN_PROGRESS, null, "Another overlay is covered by a close request");
        }
        if (changesGeometry && !canChangeGroupGeometry(group, true)) {
            return rejected(DockFailure.Code.CLOSE_IN_PROGRESS, null, "The group change would resize content covered by a close request");
        }
        boolean pinning = existing.getMode() == DockGroupMode.OVERLAY && candidate.getMode() == DockGroupMode.DOCKED;
        boolean wasUpdating = updating;
        updating = true;
        try {
            resolved.applyTo(group.definition);
            groupSurface.updatePreferredSize(changes.getPreferredWidth(), changes.getPreferredHeight());
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
            if (candidate.getMode() == DockGroupMode.OVERLAY && !((DockGroupPane) group.tabs).isCollapsed()) revealGroup(group);
            refreshEmptyGroups();
            if (pinning) treeFor(group).restoreGroupSize(group.id, groupSurface.expandedWidth(), groupSurface.expandedHeight());
            if (active.get() != null && !isGroupVisible(containing(active.get().getId()))) {
                Tab selected = group.tabs.getSelectionModel().getSelectedItem();
                setActive(isGroupVisible(group) && selected != null
                        ? dockables.get((DockableId) selected.getUserData()) : selectedVisibleFallback());
                focus(active.get());
            }
        } finally {
            updating = wasUpdating;
        }
        publish();
        return new ActionResult.Applied();
    }

    boolean canToggleGroupMode(DockGroupId id) {
        Group group = group(id);
        return group != null && group.definition.isCollapsible() && !groupClosing(group)
                && canChangeGroupGeometry(group, true)
                && openOverlays(group).stream().noneMatch(this::groupClosing);
    }

    public ActionResult setGroupCollapsed(DockGroupId id, boolean collapsed) {
        return setGroupCollapsed(id, collapsed, true);
    }

    private ActionResult setGroupCollapsed(DockGroupId id, boolean collapsed, boolean focusHeader) {
        requireFxThread();
        if (disposed) return new ActionResult.Rejected(managerClosedFailure());
        if (layoutChanging) return new ActionResult.Rejected(layoutChangeFailure());
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
        if (collapsed) tabs.setCollapsed(true);
        else revealGroup(group);
        if (collapsed) {
            if (active.get() != null && containing(active.get().getId()) == group) setActive(selectedVisibleFallback());
            if (focusHeader) tabs.focusSelectedHeader();
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
        boolean changed = tabs.isCollapsed();
        if (tabs.isOverlay()) {
            for (Group other : openOverlays(group)) {
                ((DockGroupPane) other.tabs).setCollapsed(true);
                changed = true;
            }
        }
        tabs.setCollapsed(false);
        return changed;
    }

    private List<Group> openOverlays(Group group) {
        return groups.values().stream().filter(other -> other != group && owner(other) == owner(group))
                .filter(other -> ((DockGroupPane) other.tabs).isOverlay() && isGroupVisible(other)).toList();
    }

    void dismissOutsideOverlays(Node target) {
        Group headerGroup = groups.values().stream()
                .filter(group -> ((DockGroupPane) group.tabs).isOverlay()
                        && ((DockGroupPane) group.tabs).isHeader(target)).findFirst().orElse(null);
        for (Group group : List.copyOf(groups.values())) {
            DockGroupPane tabs = (DockGroupPane) group.tabs;
            if (!tabs.isOverlay() || tabs.isCollapsed() || !tabs.isVisible() || contains(tabs, target)) continue;
            // The header action will preflight and publish same-window arbitration as one transition.
            if (headerGroup != null && owner(headerGroup) == owner(group)) continue;
            setGroupCollapsed(group.id, true, false);
        }
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
            ((DockGroupPane) group.tabs).surface().synchronize();
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
    /** Prepares fresh content for an empty manager, then installs the validated layout as one change. */
    public RestoreResult restore(LayoutSnapshot snapshot, DockableResolver resolver) {
        requireFxThread();
        if (disposed) return new RestoreResult.Rejected(managerClosedFailure(), List.of());
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(resolver, "resolver");
        if (layoutChanging) return new RestoreResult.Rejected(layoutChangeFailure(), List.of());
        if (closing.scope() != null) return new RestoreResult.Rejected(new DockFailure(
                DockFailure.Code.CLOSE_IN_PROGRESS, null, "Cannot restore during close consent", null), List.of());
        if (!dockables.isEmpty()) return new RestoreResult.Rejected(new DockFailure(
                DockFailure.Code.RESTORE_REQUIRES_EMPTY_MANAGER, null, "Restore requires a manager without open Dockables", null), List.of());
        DockFailure invalid = DockSnapshotValidation.validate(snapshot,
                group -> validate(group.settings().definition(group.originArea()), new java.util.HashSet<>()));
        if (invalid != null) return new RestoreResult.Rejected(invalid, List.of());
        layoutChanging = true;
        try {
            List<DockLayoutCandidate> candidates = snapshot.windows().stream().map(this::prepareLayoutCandidate).toList();
            java.util.Set<DockGroupId> floatingGroups = candidates.stream().filter(candidate -> !candidate.window.main())
                    .flatMap(candidate -> candidate.groups().stream()).map(Group::id).collect(java.util.stream.Collectors.toSet());
            DockRestorePreparation prepared = DockRestorePreparation.resolve(candidates, resolver, fallbackTarget, (dockable, target) -> {
                if (dockable.isMainWindowOnly() && target instanceof ResolvedDockTarget.Insertion insertion
                        && floatingGroups.contains(insertion.groupId())) {
                    return moveRejected(DockFailure.Code.MAIN_WINDOW_ONLY, dockable.getId(), "This Dockable must remain in the main window");
                }
                return checkPlacement(dockable, target);
            });
            if (prepared.rejection() != null) return prepared.rejection();
            if (prepared.layoutAdjusted()) candidates.forEach(DockLayoutCandidate::normalize);
            candidates = candidates.stream().filter(candidate -> candidate.window.main()
                    || candidate.groups().stream().anyMatch(group -> !candidate.tabs(group.id).isEmpty())).toList();
            if (snapshot.activeDockableId() != null) for (DockLayoutCandidate candidate : candidates) {
                candidate.groups().stream().filter(group -> candidate.tabs(group.id).contains(snapshot.activeDockableId())).findFirst()
                        .ifPresent(group -> candidate.select(group.id, snapshot.activeDockableId()));
            }
            Map<DockWindowId, FloatingWindow> hosts = new LinkedHashMap<>();
            try {
                for (DockLayoutCandidate candidate : candidates) if (!candidate.window.main()) {
                    hosts.put(candidate.window.id(), prepareFloatingWindow(candidate.window.id(),
                            DockWindowBounds.fit(candidate.window.bounds())));
                }
            } catch (FloatingPreparationFailure failure) {
                hosts.values().forEach(this::disposeFloatingWindow);
                return DockRestorePreparation.rejected(prepared.contents(), new DockFailure(
                        DockFailure.Code.WINDOW_CREATION_FAILED, null, "Floating window preparation failed", failure.getCause())).rejection();
            }
            initialIntents.clear();
            snapshot.initialIntents().forEach((id, area) -> {
                if (prepared.contents().containsKey(id)) initialIntents.put(id, area);
            });
            returnTargets.clear();
            snapshot.returnPositions().forEach((id, position) -> {
                if (prepared.contents().containsKey(id)) returnTargets.put(id,
                        new ReturnLocation(position.target(), position.areaId()));
            });
            installLayoutCandidates(candidates, hosts, prepared.contents(), snapshot.activeDockableId());
            return new RestoreResult.Applied(prepared.missingIds());
        } finally {
            layoutChanging = false;
        }
    }

    private DockFailure layoutChangeFailure() {
        return new DockFailure(DockFailure.Code.LAYOUT_CHANGE_IN_PROGRESS, null,
                "A layout replacement is already being prepared", null);
    }

    private DockLayoutCandidate prepareLayoutCandidate(LayoutSnapshot.Window window) {
        return new DockLayoutCandidate(window, state -> {
            Group group = createDetachedGroup(state.id(), state.settings().definition(state.originArea()));
            DockGroupPane tabs = (DockGroupPane) group.tabs;
            tabs.setCollapsed(state.settings().mode() == DockGroupMode.OVERLAY || state.collapsed());
            tabs.surface().updatePreferredSize(state.expandedWidth(), state.expandedHeight());
            return group;
        }, this::publish, id -> !groupClosing(group(id)));
    }

    private void installLayoutCandidate(DockLayoutCandidate candidate, Map<DockableId, Dockable> contents,
                                        DockableId activeId) {
        installLayoutCandidates(List.of(candidate), Map.of(), contents, activeId);
    }

    private void installLayoutCandidates(List<DockLayoutCandidate> candidates, Map<DockWindowId, FloatingWindow> hosts,
                                         Map<DockableId, Dockable> contents, DockableId activeId) {
        Map<DockableId, Dockable> installed = new LinkedHashMap<>(contents);
        boolean wasUpdating = updating;
        updating = true;
        try {
            for (Group old : groups.values()) {
                for (Tab tab : List.copyOf(old.tabs.getTabs())) {
                    detachTab(old, tab);
                }
                old.tabs.getTabs().clear();
            }
            tree.clear();
            pane.clearAreas();
            floatingWindows.values().forEach(this::disposeFloatingWindow);
            floatingWindows.clear();
            groupWindows.clear();
            groups.clear();
            groupActivationOrder.clear();
            edgeAreas.clear();
            dockables.clear();
            dockables.putAll(installed);
            previousFocus.keySet().retainAll(installed.keySet());
            activationOrder.keySet().retainAll(installed.keySet());
            floatingWindows.putAll(hosts);
            for (DockLayoutCandidate candidate : candidates) {
                FloatingWindow host = hosts.get(candidate.window.id());
                DockPane destinationPane = host == null ? pane : host.pane;
                DockLayoutTree destinationTree = host == null ? tree : host.tree;
                DockDragController destinationDrag = host == null ? drag : host.drag;
                for (Group group : candidate.groups()) {
                    groups.put(group.id, group);
                    if (host != null) groupWindows.put(group.id, host.id);
                    allocatedGroupIds.add(group.id);
                    for (DockableId id : candidate.tabs(group.id)) {
                        Tab tab = prepareTab(installed.get(id));
                        destinationDrag.installTab(tab, id);
                        group.tabs.getTabs().add(tab);
                    }
                    DockableId selected = candidate.selected(group.id);
                    int index = selected == null ? -1 : tabIndex(group, selected);
                    if (index >= 0) group.tabs.getSelectionModel().select(index);
                    else if (!group.tabs.getTabs().isEmpty()) group.tabs.getSelectionModel().selectFirst();
                }
                List<java.util.function.Consumer<Node>> placements = new java.util.ArrayList<>();
                for (LayoutSnapshot.Root root : candidate.roots()) {
                    if (root.placement() == LayoutSnapshot.Placement.CENTER) placements.add(destinationPane.surface::setCenter);
                    else {
                        javafx.geometry.Side side = javafx.geometry.Side.valueOf(root.placement().name());
                        DockAreaId areaId = root.branch().aliases().getFirst();
                        Map<DockAreaId, javafx.geometry.Side> destinationEdges = host == null ? edgeAreas : host.edgeAreas;
                        root.branch().aliases().forEach(alias -> destinationEdges.put(alias, side));
                        placements.add(node -> {
                            destinationPane.setArea(side, node, () -> canResizeEdge(destinationPane, destinationTree,
                                    host == null ? null : host.id, areaId, side), this::publish);
                            destinationPane.setAreaExtent(side, root.preferredWidth(), root.preferredHeight());
                        });
                    }
                }
                destinationTree.installFrom(candidate.tree, placements);
                destinationTree.view().forEach(area -> allocatedAreaIds.add(area.id()));
            }
            refreshEmptyGroups();
            Dockable desired = activeId == null ? null : installed.get(activeId);
            if (desired != null && !isGroupVisible(containing(activeId))) desired = null;
            setActive(desired == null ? selectedVisibleFallback() : desired);
            if (stage.isShowing()) hosts.values().forEach(host -> host.stage.show());
            focus(active.get());
        } finally {
            updating = wasUpdating;
        }
        publish();
    }

    /** Returns the last completed layout geometry, including during live geometry gestures. */
    public LayoutSnapshot snapshot() { return publishedSnapshot; }
    public ReadOnlyObjectProperty<DockLayoutView> layoutProperty() { return layout.getReadOnlyProperty(); }

    private void addEdgeGroup(DockLayoutNode definition, javafx.geometry.Side side) {
        if (definition == null) return;
        edgeAreas.put(new DockAreaId(definition.getDestinationId()), side);
        DockAreaId areaId = new DockAreaId(definition.getDestinationId());
        addGroup(definition, node -> pane.setArea(side, node,
                () -> canResizeEdge(areaId, side), this::publish));
    }

    private boolean canResizeEdge(DockAreaId areaId, javafx.geometry.Side side) {
        return canResizeEdge(pane, tree, null, areaId, side);
    }

    private boolean canResizeEdge(DockPane ownerPane, DockLayoutTree ownerTree, DockWindowId ownerId,
                                  DockAreaId areaId, javafx.geometry.Side side) {
        if (ownerTree.groups(areaId).stream().anyMatch(this::groupClosing)) return false;
        for (Group candidate : groups.values()) {
            if (!Objects.equals(groupWindows.get(candidate.id), ownerId) || !groupClosing(candidate)) continue;
            if (contains(ownerPane.surface.getCenter(), ((DockGroupPane) candidate.tabs).surface())) return false;
            javafx.geometry.Side otherSide = ownerPane.edgeOf(candidate.tabs);
            if (side.isHorizontal() && otherSide != null && otherSide.isVertical()) return false;
        }
        return true;
    }

    private boolean canChangeGroupGeometry(Group group) {
        return canChangeGroupGeometry(group, false);
    }

    private boolean canChangeGroupGeometry(Group group, boolean changingOuterExtent) {
        if (((DockGroupPane) group.tabs).isOverlay()) {
            if (openOverlays(group).stream().anyMatch(this::groupClosing)) return false;
            if (!changingOuterExtent) return !groupClosing(group);
        }
        if (treeFor(group).geometryAffects(group.id, this::groupClosing)) return false;
        DockWindowId windowId = groupWindows.get(group.id);
        FloatingWindow host = windowId == null ? null : floatingWindows.get(windowId);
        DockLayoutTree ownerTree = host == null ? tree : host.tree;
        DockPane ownerPane = host == null ? pane : host.pane;
        Map<DockAreaId, javafx.geometry.Side> ownerEdges = host == null ? edgeAreas : host.edgeAreas;
        for (var entry : ownerEdges.entrySet()) {
            List<Group> areaGroups = ownerTree.groups(entry.getKey());
            if (!areaGroups.contains(group)) continue;
            boolean onlyExpandedGroup = areaGroups.stream().filter(candidate -> candidate != group)
                    .allMatch(candidate -> !candidate.tabs.isManaged() || ((DockGroupPane) candidate.tabs).isCollapsed());
            if (changingOuterExtent || onlyExpandedGroup) {
                return canResizeEdge(ownerPane, ownerTree, windowId, entry.getKey(), entry.getValue());
            }
        }
        return true;
    }

    private void addGroup(DockLayoutNode definition, java.util.function.Consumer<javafx.scene.Node> place) {
        if (definition == null) return;
        tree.addArea(definition, place,
                group -> createGroup(new DockGroupId(group.getDestinationId()), group));
    }

    private Group createGroup(DockGroupId id, DockGroupDefinition definition) {
        Group group = createDetachedGroup(id, definition);
        allocatedGroupIds.add(id);
        groups.put(id, group);
        return group;
    }

    private Group createDetachedGroup(DockGroupId id, DockGroupDefinition definition) {
        DockGroupDefinition captured = LayoutSnapshot.GroupSettings.capture(definition)
                .definition(new DockAreaId(definition.getDestinationId()));
        TabPane tabs = new DockGroupPane(captured);
        tabs.setSide(captured.getTabSide());
        ((DockGroupPane) tabs).setCollapsed(captured.getMode() == DockGroupMode.OVERLAY);
        Group group = new Group(id, tabs, captured);
        ((DockGroupPane) tabs).installHeaderActions(tab -> activate((DockableId) tab.getUserData()),
                () -> setGroupCollapsed(id, true));
        tabs.getSelectionModel().selectedItemProperty().addListener((observable, old, selected) -> {
            if (!updating && groups.get(id) == group && selected != null && isGroupVisible(group)) {
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
                if (active.get() != dockable && !updating && !layoutChanging && publishedSnapshot != null
                        && layout.get().windows().stream().flatMap(window -> window.areas().stream())
                        .flatMap(area -> area.groups().stream()).anyMatch(group -> dockable.getId().equals(group.selectedDockableId()))) {
                    publishedSnapshot = new LayoutSnapshot(publishedSnapshot.version(), publishedSnapshot.windows(),
                            dockable.getId(), publishedSnapshot.returnPositions(), publishedSnapshot.initialIntents());
                }
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
        if (window != stage && stage.isShowing() && !window.isShowing()) window.show();
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
            if (group.getMode() == null || (group.getMode() == DockGroupMode.OVERLAY && !group.isCollapsible())) {
                throw new IllegalArgumentException("Overlay mode requires a collapsible group");
            }
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
        var windows = new java.util.ArrayList<DockLayoutView.Window>();
        windows.add(new DockLayoutView.Window(new DockWindowId("main"), true, layoutAreas(tree, pane, edgeAreas)));
        floatingWindows.values().forEach(window -> windows.add(new DockLayoutView.Window(
                window.id, false, layoutAreas(window.tree, window.pane, window.edgeAreas), window.geometry.bounds())));
        List<LayoutSnapshot.Root> mainRoots = snapshotRoots(tree, pane, edgeAreas, false);
        var snapshotWindows = new java.util.ArrayList<LayoutSnapshot.Window>();
        snapshotWindows.add(new LayoutSnapshot.Window(new DockWindowId("main"), true, null, mainRoots));
        floatingWindows.values().forEach(window -> snapshotWindows.add(new LayoutSnapshot.Window(
                window.id, false, window.geometry.bounds(), snapshotRoots(window.tree, window.pane, window.edgeAreas,
                        window.geometry.hasPendingChanges()))));
        Map<DockableId, LayoutSnapshot.ReturnPosition> positions = new LinkedHashMap<>();
        returnTargets.forEach((id, position) -> positions.put(id,
                new LayoutSnapshot.ReturnPosition(position.target, position.areaId)));
        publishedSnapshot = new LayoutSnapshot(LayoutSnapshot.CURRENT_VERSION, snapshotWindows,
                active.get() == null ? null : active.get().getId(), positions, initialIntents);
        layout.set(new DockLayoutView(windows));
    }

    private static List<DockLayoutView.Area> layoutAreas(DockLayoutTree tree, DockPane pane,
                                                        Map<DockAreaId, javafx.geometry.Side> edges) {
        return tree.view().stream().map(area -> {
            javafx.geometry.Side side = edges.get(area.id());
            if (side == null) return area;
            double extent = pane.committedExtent(side);
            return new DockLayoutView.Area(area.id(), area.groups(), area.root(),
                    side.isVertical() ? extent : -1, side.isHorizontal() ? extent : -1);
        }).toList();
    }

    private static List<LayoutSnapshot.Root> snapshotRoots(DockLayoutTree tree, DockPane pane,
                                                          Map<DockAreaId, javafx.geometry.Side> edges,
                                                          boolean windowResizing) {
        return tree.snapshotRoots(edges, windowResizing || tree.isResizing() || pane.isResizing()).stream().map(root -> {
            if (root.placement() == LayoutSnapshot.Placement.CENTER) return root;
            javafx.geometry.Side side = javafx.geometry.Side.valueOf(root.placement().name());
            double extent = pane.committedExtent(side);
            return new LayoutSnapshot.Root(root.placement(), side.isVertical() ? extent : -1,
                    side.isHorizontal() ? extent : -1, root.branch());
        }).toList();
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

    long nextWindowFocusOrder() { return ++nextWindowFocusOrder; }

    void restoreDragFocus(DockableId id) {
        Dockable dockable = dockables.get(id);
        Group group = containing(id);
        if (dockable == null || group == null) return;
        if (isGroupVisible(group)) focus(dockable);
        else ((DockGroupPane) group.tabs).focusSelectedHeader();
    }

    List<DockDragController> dragControllers() {
        var controllers = new java.util.ArrayList<DockDragController>();
        if (drag != null) controllers.add(drag);
        floatingWindows.values().forEach(window -> controllers.add(window.drag));
        controllers.sort(java.util.Comparator.comparingLong(DockDragController::frontOrder).reversed());
        return controllers;
    }

    private Group createFloatingGroup(javafx.geometry.Rectangle2D bounds) {
        DockWindowId candidateId;
        do { candidateId = new DockWindowId("floating-" + ++nextWindowId); }
        while (floatingWindows.containsKey(candidateId) || allocatedAreaIds.contains(new DockAreaId(candidateId.value())));
        DockWindowId windowId = candidateId;
        FloatingWindow host = prepareFloatingWindow(windowId, bounds);
        allocatedAreaIds.add(new DockAreaId(windowId.value()));
        DockGroupId groupId;
        do { groupId = new DockGroupId("group-" + ++nextGroupId); } while (allocatedGroupIds.contains(groupId));
        DockGroupDefinition definition = new DockGroupDefinition(windowId.value());
        definition.setPersistent(false);
        Group group = createGroup(groupId, definition);
        groupWindows.put(group.id, windowId);
        host.tree.addArea(definition, host.pane.surface::setCenter, ignored -> group);
        floatingWindows.put(windowId, host);
        return group;
    }

    private FloatingWindow prepareFloatingWindow(DockWindowId windowId, javafx.geometry.Rectangle2D bounds) {
        FloatingWindowConfiguration configuration = new FloatingWindowConfiguration();
        try {
            floatingWindowCustomizer.accept(configuration);
            configuration.getIcons().forEach(Objects::requireNonNull);
            configuration.getStylesheets().forEach(Objects::requireNonNull);
        } catch (RuntimeException failure) {
            throw new FloatingPreparationFailure(failure);
        }
        DockPane floatingPane = new DockPane();
        Stage floatingStage = new Stage();
        floatingStage.setScene(new javafx.scene.Scene(floatingPane));
        de.neitzel.fx.theme.ThemeManager.link(floatingStage, stage.getScene());
        javafx.event.EventHandler<javafx.stage.WindowEvent> closeHandler = event -> {
            event.consume();
            requestFloatingWindowClose(windowId);
        };
        floatingStage.addEventFilter(javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST, closeHandler);
        floatingStage.setTitle(configuration.getTitle());
        floatingStage.getIcons().setAll(configuration.getIcons());
        floatingStage.getScene().getStylesheets().setAll(configuration.getStylesheets());
        DockStyles.install(floatingStage.getScene());
        floatingStage.setX(bounds.getMinX());
        floatingStage.setY(bounds.getMinY());
        floatingStage.setWidth(bounds.getWidth());
        floatingStage.setHeight(bounds.getHeight());
        DockLayoutTree floatingTree = new DockLayoutTree(this::publish, id -> !groupClosing(group(id)));
        DockDragController floatingDrag = new DockDragController(this, floatingPane, () -> groups.values().stream()
                .filter(candidate -> windowId.equals(groupWindows.get(candidate.id)))
                .map(candidate -> new DockDragController.GroupSurface(candidate.id, candidate.tabs,
                        floatingPane.edgeOf(candidate.tabs))).toList());
        DockWindowGeometry geometry = new DockWindowGeometry(floatingStage, this::publish);
        floatingPane.manager = this;
        floatingStage.getProperties().put(ATTACHMENT_KEY, this);
        floatingStage.getScene().focusOwnerProperty().addListener(focusListener);
        return new FloatingWindow(windowId, floatingStage, floatingPane, floatingTree, floatingDrag, geometry, closeHandler, new LinkedHashMap<>());
    }

    private static final class FloatingPreparationFailure extends RuntimeException {
        FloatingPreparationFailure(RuntimeException cause) { super(cause); }
    }

    private record FloatingWindow(DockWindowId id, Stage stage, DockPane pane, DockLayoutTree tree,
                                  DockDragController drag, DockWindowGeometry geometry,
                                  javafx.event.EventHandler<javafx.stage.WindowEvent> closeHandler,
                                  Map<DockAreaId, javafx.geometry.Side> edgeAreas) { }
    private record ReturnLocation(DockTarget target, DockAreaId areaId) { }

    private static ActionResult rejected(DockFailure.Code code, DockableId id, String message) {
        return new ActionResult.Rejected(new DockFailure(code, id, message, null));
    }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Docking operations require the JavaFX Application Thread");
    }

    record Group(DockGroupId id, TabPane tabs, DockGroupDefinition definition) { }
}
