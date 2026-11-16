package de.neitzel.fx.example.docking;

import de.neitzel.fx.control.StatusBar;
import de.neitzel.fx.docking.*;
import javafx.application.Application;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.ListResourceBundle;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Keyboard menus, icon names, translated policy failures and a shared application theme. */
public final class KeyboardThemeDockingApp extends Application {
    @Override public void start(Stage stage) {
        DockPane pane = new DockPane();
        pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
        DockGroupDefinition tools = new DockGroupDefinition("project");
        tools.setCollapsible(true);
        tools.setTabSide(Side.LEFT);
        tools.setPreferredWidth(220);
        pane.getInitialLayout().setLeft(tools);

        Button focusTabs = new Button("Focus active tab strip");
        Button checkFloat = new Button("Check Float");
        CheckBox blockFloat = new CheckBox("Block floating");
        CheckBox missingKey = new CheckBox("Missing translation key");
        CheckBox allowClose = new CheckBox("Allow Notes to close");
        Label message = new Label("Focus a tab strip, press Shift+F10, then use arrows and Enter.");
        message.setWrapText(true);
        Label active = new Label();
        StatusBar status = new StatusBar();
        status.getLeftItems().add(message);
        status.getRightItems().add(active);
        BorderPane shell = new BorderPane(pane);
        shell.setTop(new VBox(new ToolBar(focusTabs, checkFloat, blockFloat, missingKey),
                new ToolBar(allowClose, new Label("Notes initially vetoes Close. Project is an icon-only tab."))));
        shell.setBottom(status);
        String theme = getClass().getResource("KeyboardThemeDocking.css").toExternalForm();
        stage.setScene(new Scene(shell, 1000, 620));
        stage.getScene().getStylesheets().add(theme);
        stage.setTitle("NeitzelLib — keyboard, names and application styles");

        DockingOptions options = new DockingOptions();
        options.setResourceBundle(translations());
        options.setFloatingWindowCustomizer(configuration -> {
            configuration.setTitle("Themed floating editor — Shift+F10 to return");
            configuration.getStylesheets().add(theme);
        });
        options.setPlacementRule((dockable, target) -> blockFloat.isSelected()
                && target instanceof ResolvedDockTarget.Detachment
                ? Optional.of(new DockFailure(DockFailure.Code.PLACEMENT_REJECTED, dockable.getId(),
                    "Demo policy disabled detachment", null,
                    missingKey.isSelected() ? "demo.unknownKey" : "demo.floatingBlocked")) : Optional.empty());
        DockingManager manager = DockingManager.attach(stage, pane, options);
        Dockable notes = editor("notes", "Notes", "abcdef\nMove this tab with its keyboard menu, then keep typing.");
        Dockable readme = editor("readme", "Readme", "Move/Split/Float/Return use the same rules as dragging.\n"
                + "Toggle Block floating and Missing translation key, then compare Check Float with the tab menu.");
        Dockable project = editor("project", "", "Project files\nThis icon-only tab is named for assistive technology.");
        project.setIcon(new Label("▦"));
        project.setAccessibleName("Project files");
        project.setMainWindowOnly(true);
        notes.setCloseCheck(request -> CompletableFuture.completedFuture(
                allowClose.isSelected() ? CloseConsent.ALLOW : CloseConsent.VETO));
        notes.setOnClosed(() -> message.setText("Notes closed after consent."));
        manager.open(project, DockTarget.area("project"));
        manager.open(readme, DockTarget.area("editors"));
        manager.open(notes, DockTarget.area("editors"));

        focusTabs.setOnAction(event -> {
            Dockable current = manager.activeDockable();
            if (current == null) return;
            current.getContent().getScene().getRoot().lookupAll(".dock-group").stream()
                    .filter(node -> node instanceof TabPane tabs && tabs.getTabs().stream()
                            .anyMatch(tab -> tab.getContent() == current.getContent()))
                    .findFirst().ifPresent(node -> node.requestFocus());
        });
        checkFloat.setOnAction(event -> {
            Dockable current = manager.activeDockable();
            if (current == null) return;
            MoveCheck result = manager.checkMove(current.getId(), DockTarget.floating());
            message.setText(result instanceof MoveCheck.Rejected rejected
                    ? manager.describeFailure(rejected.failure()) + " [" + rejected.failure().code()
                        + "; key=" + rejected.failure().messageKey() + "]"
                    : "Float is available. Open the tab menu to execute it.");
        });
        Runnable refresh = () -> {
            Dockable current = manager.activeDockable();
            active.setText(current == null ? "No active tab" : current.getTitle().isBlank()
                    ? current.getAccessibleName() : current.getTitle());
        };
        manager.activeDockableProperty().addListener((observable, old, current) -> refresh.run());
        refresh.run();
        stage.setOnHidden(event -> {
            for (Dockable dockable : List.of(notes, readme, project)) {
                Scene scene = dockable.getContent().getScene();
                if (scene != null && scene.getWindow() != stage) scene.getWindow().hide();
            }
        });
        stage.show();
        manager.activate(notes.getId());
        ((TextArea) notes.getContent()).selectRange(2, 4);
    }

    private static Dockable editor(String id, String title, String text) {
        Dockable dockable = new Dockable(new DockableId(id), new TextArea(text));
        dockable.setTitle(title);
        return dockable;
    }

    private static ListResourceBundle translations() {
        return new ListResourceBundle() {
            @Override protected Object[][] getContents() {
                return new Object[][] {
                        {"dock.action.move", "Verschieben"}, {"dock.action.split", "Unterteilen"},
                        {"dock.action.close", "Schließen"}, {"dock.action.float", "Abkoppeln"},
                        {"dock.action.return", "Zurück ins Hauptfenster"}, {"dock.action.pin", "Gruppe anheften"},
                        {"dock.failure.PLACEMENT_REJECTED", "Diese Platzierung ist nicht erlaubt"},
                        {"demo.floatingBlocked", "Abkoppeln ist in dieser Ansicht gesperrt"}
                };
            }
        };
    }
}
