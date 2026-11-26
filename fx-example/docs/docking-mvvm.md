# MVVM content and local/global bars

The docking API accepts an ordinary Node. This example passes the root of one
`LoadedView` to a `Dockable`; the manager never loads that View again. Its Note
JavaBean, ViewModel, TextArea, local toolbar and local status bar remain the same
objects when the tab moves, floats or returns. A second editor uses a plain
TextArea without a ViewModel.

Build from the repository root with Java 25, without replacing installed runtime
artifacts used by the Designer:

```sh
./mvnw -pl fx-example -am package
java --enable-native-access=ALL-UNNAMED \
  -cp 'fx-example/target/classes:fx/target/classes:fx-example/target/libs/*' \
  de.neitzel.fx.example.docking.mvvm.MvvmDockingMain
```

In the example:

1. Edit Notes. Its tab title gains an asterisk and the local character count changes.
2. Choose **Move Notes to tools**, **Float Notes**, then **Return Notes**. The
   local Clear note button and status bar travel with the editor; the global
   command and status bars stay in the main window.
3. While Notes is floating, click **Checkpoint active** in the main window.
   Notes keeps its command target when focus enters that toolbar. The command
   records a checkpoint in memory and clears the asterisk; it does not save a file.
4. Select Plain editor. The checkpoint command is disabled for this content, and
   the global status identifies the newly active Dockable.
5. Close Notes with its tab close button to run the application's close hook.

`MvvmEditor.fxml` declares a bidirectional DirectBinding between the TextArea and
the Note's `text` attribute. Application updates use `LoadedView.property("text")`;
calling a JavaBean setter directly does not notify an already loaded ViewModel.
`MvvmEditor` binds the Dockable title and local status to presentation properties.
`MvvmDockingWorkspace` binds the global command availability and status to
`activeDockableProperty()`. These are application bindings, not a docking-specific
MVVM service.

The application adapter owns its change listener and derived bindings. Its
`onClosed` and `onDiscarded` hooks release those resources explicitly. Moves,
collapse and veto do not run those hooks. A rejected factory-created editor is
discarded; rejection of a directly supplied Dockable leaves cleanup to its caller.
The same hooks close the editor's `ViewModelContext`, which closes the
`LoadedView` and clears its Model-to-ViewModel registrations. See the
[MVVM lifetime contract](../../fx/docs/architecture.md).

`MvvmDockingTest` loads the actual example FXML and checks model/property/control
edits before and after moves, object identity, bar ownership, global toolbar
focus and resource hooks. Its Scene-focus assertions complement the separately
qualified [native keyboard transfer tests](../../fx/docs/docking-floating.md);
this integration test does not claim to inject native keystrokes.

For entirely independent docking and bars without initializing MVVM or injection,
use the [plain docking example](../../fx/docs/docking.md) and
[standalone bars example](../../fx/docs/bars.md).
