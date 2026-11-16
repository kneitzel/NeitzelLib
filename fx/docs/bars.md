# Standalone toolbar and status bar

Use JavaFX's `ToolBar` for toolbar content and NeitzelLib's
`de.neitzel.fx.control.StatusBar` for left/right status content. Both accept
ordinary application Nodes. They work in Java and ordinary FXML without a
DockingManager, ViewLoader, ViewModel or initialized injection context.

The toolbar deliberately reuses JavaFX's existing public control: its item
list, orientation, overflow handling, accessibility and CSS already provide
the required behavior. There is no second library toolbar API to learn.

## Java

```java
var save = new Button("Save");
var toolbar = new ToolBar(save);

var status = new Label("Ready");
var cursor = new Label();
cursor.textProperty().bind(editor.caretPositionProperty().asString("Offset %d"));
var progress = new ProgressBar();
progress.progressProperty().bind(completedFraction);

var statusBar = new StatusBar();
statusBar.getLeftItems().add(status);
statusBar.getRightItems().addAll(cursor, progress);

shell.setTop(toolbar);
shell.setBottom(statusBar);
```

`getLeftItems()` and `getRightItems()` return live observable lists. Items
retain their identity, properties and event handlers. Add, remove and reorder
items on the JavaFX Application Thread. Ordinary JavaFX scene-graph ownership
rules apply: a Node can appear in only one place at a time. Removing an item
from its list detaches it; the application owns its resources and bindings.

The left group stays at the left edge and the right group at the right edge
while there is available space between them. A status bar's preferred size
comes from its content. Parent layouts can stretch it to the window width.

## FXML

```xml
<?import de.neitzel.fx.control.StatusBar?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.ToolBar?>
<?import javafx.scene.layout.BorderPane?>

<BorderPane xmlns:fx="http://javafx.com/fxml/1">
    <top>
        <ToolBar>
            <items><Button text="Save"/></items>
        </ToolBar>
    </top>
    <bottom>
        <StatusBar>
            <leftItems><Label text="Ready"/></leftItems>
            <rightItems><Label text="Line 1, column 1"/></rightItems>
        </StatusBar>
    </bottom>
</BorderPane>
```

Load this with ordinary `FXMLLoader`. Bind application state after loading,
or use an application controller. No NeitzelLib loading mechanism is required.

## Styling

The status container has CSS class `status-bar`; its item groups have classes
`status-bar-left` and `status-bar-right`. Use ordinary Region CSS for padding
and background, and HBox CSS for item spacing. JavaFX ToolBar retains its
standard `tool-bar` CSS class and skin.

## Example

The standalone bars example in `fx-example` provides a Java-built screen and
an equivalent FXML-built screen. Both have an editor, Clear and Append actions,
status text, a bound cursor position and a demonstration progress value derived
from the editor's current length. Switch the startup parameter to `fxml` to
choose the FXML screen. The example performs no file writes and uses no MVVM.

From the repository root with JDK 25, package the reactor and run either screen
without installing changed artifacts into the local Maven repository:

```bash
./mvnw -pl fx-example -am package
java --enable-native-access=ALL-UNNAMED \
  -cp 'fx-example/target/classes:fx/target/classes:fx-example/target/libs/*' \
  de.neitzel.fx.example.bars.BarsMain
```

Append `fxml` to the launch command for the FXML screen. The classpath separator
shown is for macOS/Linux; use `;` on Windows. A graphical desktop session is
required. The launch class is ordinary Java and delegates to the Application.

The [MVVM docking editor](../../fx-example/docs/docking-mvvm.md) demonstrates
Dockable-owned bars and optional MVVM bindings alongside a plain JavaFX editor.
The standalone controls also support ordinary JavaFX property bindings.
