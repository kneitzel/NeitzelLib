package de.neitzel.fx.view;

import de.neitzel.fx.docking.*;
import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ListResourceBundle;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Application and built-in menus share localization while diagnostics retain original data. */
class DockingLocalizationTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        FxThread.start();
        FxThread.onFx(() -> Platform.setImplicitExit(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void builtInMenusAndApplicationsResolveTheSamePlacementMessage(boolean knownKey) throws Exception {
        FxThread.onFx(() -> {
            DockPane pane = new DockPane();
            pane.getInitialLayout().setCenter(new DockGroupDefinition("editors"));
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 700, 450));
            DockingOptions options = new DockingOptions();
            options.setResourceBundle(new ListResourceBundle() {
                @Override protected Object[][] getContents() {
                    return new Object[][] {
                            {"dock.action.move", "Verschieben"}, {"dock.action.float", "Abkoppeln"},
                            {"dock.failure.PLACEMENT_REJECTED", "Platzierung nicht erlaubt"},
                            {"application.onlyMain", "Nur im Hauptbereich"}
                    };
                }
            });
            String key = knownKey ? "application.onlyMain" : "application.missing";
            RuntimeException cause = new IllegalStateException("Diagnostic context");
            DockFailure denial = new DockFailure(DockFailure.Code.PLACEMENT_REJECTED,
                    new DockableId("notes"), "Original diagnostic text", cause, key);
            options.setPlacementRule((dockable, target) -> target instanceof ResolvedDockTarget.Detachment
                    ? Optional.of(denial) : Optional.empty());
            DockingManager manager = DockingManager.attach(stage, pane, options);
            options.setResourceBundle(null);
            Dockable notes = new Dockable(new DockableId("notes"), new TextArea("Unsaved"));
            notes.setTitle("Notes");
            manager.open(notes, DockTarget.area("editors"));
            stage.show();
            ContextMenu menu = null;
            try {
                pane.applyCss();
                pane.layout();
                TabPane tabs = (TabPane) pane.lookup(".tab-pane");
                menu = tabs.getTabs().getFirst().getContextMenu();
                menu.show(tabs, Side.BOTTOM, 0, 0);
                String expected = knownKey ? "Nur im Hauptbereich" : "Platzierung nicht erlaubt";
                assertEquals("Verschieben", menu.getItems().getFirst().getText());
                assertEquals("Close", menu.getItems().get(2).getText(), "Unspecified labels retain their default");
                assertEquals("Abkoppeln (" + expected + ")", menu.getItems().get(3).getText());
                assertTrue(menu.getItems().get(3).isDisable());
                DockFailure checked = assertInstanceOf(MoveCheck.Rejected.class,
                        manager.checkMove(notes.getId(), DockTarget.floating())).failure();
                assertEquals(expected, manager.describeFailure(checked));
                assertSame(denial, checked);
                assertEquals(key, checked.messageKey());
                assertEquals("Original diagnostic text", checked.message());
                assertSame(cause, checked.cause());
                assertEquals(DockFailure.Code.PLACEMENT_REJECTED, checked.code());
            } finally {
                if (menu != null) menu.hide();
                stage.hide();
            }
        });
    }
}
