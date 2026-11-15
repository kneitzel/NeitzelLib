package de.neitzel.fx.docking;

import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;

/** Keeps the same tab headers and live content while exposing only the header rail. */
final class DockGroupPane extends TabPane {
    private final DockGroupDefinition definition;
    private boolean collapsed;
    private final DockGroupSurface surface;

    DockGroupPane(DockGroupDefinition definition) {
        this.definition = definition;
        surface = new DockGroupSurface(this, definition);
        getStyleClass().add("dock-group");
    }

    DockGroupSurface surface() { return surface; }
    double railWidth() { return getSide().isVertical() ? headerExtent() : headerLength(); }
    double railHeight() { return getSide().isHorizontal() ? headerExtent() : headerLength(); }
    boolean isOverlay() { return definition.getMode() == DockGroupMode.OVERLAY; }
    boolean isCollapsed() { return collapsed; }
    boolean isHeader(Node node) { return headerTab(node) != null; }

    void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("collapsed"), collapsed);
        requestLayout();
    }

    void installHeaderActions(java.util.function.Consumer<Tab> activate, Runnable collapse) {
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE && !collapsed && definition.isCollapsible()) {
                collapse.run();
                event.consume();
            }
        });
        Tab[] pressed = {null};
        boolean[] selected = {false};
        boolean[] wasCollapsed = {false};
        addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            pressed[0] = headerTab(event.getPickResult().getIntersectedNode());
            selected[0] = pressed[0] != null && getSelectionModel().getSelectedItem() == pressed[0];
            wasCollapsed[0] = collapsed;
        });
        addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() != MouseButton.PRIMARY || !event.isStillSincePress()) return;
            Tab clicked = headerTab(event.getPickResult().getIntersectedNode());
            if (clicked == null || clicked != pressed[0]) return;
            pressed[0] = null;
            if (wasCollapsed[0]) activate.accept(clicked);
            else if (selected[0] && definition.isCollapsible()) collapse.run();
        });
    }

    private Tab headerTab(Node target) {
        for (Node current = target; current != null && current != this; current = current.getParent()) {
            if (current.getStyleClass().contains("tab-close-button")) return null;
            if (current instanceof TabPane) return null;
            for (Tab tab : getTabs()) {
                if (tab.getId() != null && tab.getId().equals(current.getId())) return tab;
            }
        }
        return null;
    }

    void focusSelectedHeader() {
        Tab selected = getSelectionModel().getSelectedItem();
        Node strip = ownedSkinChild("tab-header-area");
        Node header = selected == null || strip == null ? null : strip.lookup("#" + selected.getId());
        if (header != null) {
            header.setFocusTraversable(true);
            header.requestFocus();
        }
    }

    static boolean allCollapsed(Node node) {
        if (node instanceof DockGroupSurface surface) return surface.unreserved();
        if (node instanceof DockGroupPane group) return group.isCollapsed() || group.isOverlay();
        return node instanceof SplitPane split && !split.getItems().isEmpty()
                && split.getItems().stream().allMatch(DockGroupPane::allCollapsed);
    }

    private double headerExtent() {
        Node header = ownedSkinChild("tab-header-area");
        return header instanceof Region region ? Math.max(24, region.prefHeight(-1)) : 28;
    }

    private double headerLength() {
        Node header = ownedSkinChild("tab-header-area");
        return header instanceof Region region ? Math.max(24, region.prefWidth(-1)) : Math.max(24, getTabs().size() * 32);
    }

    @Override protected double computePrefWidth(double height) {
        return collapsed ? getSide().isVertical() ? headerExtent() : headerLength() : definition.getPreferredWidth();
    }
    @Override protected double computePrefHeight(double width) {
        return collapsed ? getSide().isHorizontal() ? headerExtent() : headerLength() : definition.getPreferredHeight();
    }
    @Override protected double computeMinWidth(double height) {
        return collapsed ? getSide().isVertical() ? headerExtent() : headerLength() : definition.getMinimumWidth();
    }
    @Override protected double computeMinHeight(double width) {
        return collapsed ? getSide().isHorizontal() ? headerExtent() : headerLength() : definition.getMinimumHeight();
    }
    private Node ownedSkinChild(String styleClass) {
        return getChildrenUnmodifiable().stream().filter(node -> node.getStyleClass().contains(styleClass))
                .findFirst().orElse(null);
    }

    private boolean containsSelectedContent(Node wrapper, Node selectedContent) {
        for (Node current = selectedContent; current != null && current != this; current = current.getParent()) {
            if (current == wrapper) return true;
        }
        return false;
    }

    @Override protected void layoutChildren() {
        super.layoutChildren();
        Tab selected = getSelectionModel().getSelectedItem();
        Node selectedContent = selected == null ? null : selected.getContent();
        for (Node wrapper : getChildrenUnmodifiable()) {
            if (wrapper.getStyleClass().contains("tab-content-area")) {
                wrapper.setVisible(!collapsed && containsSelectedContent(wrapper, selectedContent));
            }
        }
    }
}
