# Reset a live workspace

`DockingManager.resetLayout()` restores the initial layout captured at attachment
while keeping every open Dockable and its content instance. Unsaved editor text,
selection and application resources remain live. Reset does not invoke close
consent, `onClosed` or `onDiscarded`.

The first successful open records the Dockable's intended initial area. Later
moves do not change that intention. Reset checks that area and then the configured
fallback for each live Dockable. A Dockable initially opened in a floating window
uses the main-window fallback. Changing the original Java/FXML configuration
objects after attachment does not change the captured starting layout.
Relative fallback positions are resolved after the intended home placements, so
their reference tab may have been opened later. Retired concrete group IDs stay
retired; use stable area IDs to address restored destinations.

All destinations are checked before any live content moves. A rejection names the
affected Dockable and leaves windows, selection and layout intact. Success removes
the emptied floating windows, restores the starting edge settings and publishes
one completed layout. A pending close protects its contents from a global reset.

The runnable example loads the ordinary `Docking.fxml` definition. Build and run
from the repository root with Java 25 without installing changed runtime artifacts:

```sh
./mvnw -pl fx-example -am package
java --enable-native-access=ALL-UNNAMED \
  -cp 'fx-example/target/classes:fx/target/classes:fx-example/target/libs/*' \
  de.neitzel.fx.example.docking.ResetDockingMain
```

1. Edit Notes and select some text.
2. Choose **Rearrange** to float Notes, split Readme below the editor area and move Project into that area.
3. Clear **Allow Project placement**, then choose **Reset layout**. The status
   explains the rejection; the floating window and current arrangement remain.
4. Enable Project placement and reset again. Both editors return to the editor
   area and Project returns to its starting side group, retaining the edits.

Use reset for an already populated manager. Snapshot restoration is the separate
startup operation that resolves new content for an empty manager. Applications
retain control of file saving and storage.
