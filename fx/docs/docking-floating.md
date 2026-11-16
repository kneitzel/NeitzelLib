# Floating editors

A tab's **Float** command moves its live content into a manager-owned window.
**Return to main** returns it to its last eligible main-window position, or the
configured fallback. Both commands also appear on floating tabs. The tab's
text, selection and content instance survive the transfer, and the destination
receives keyboard focus.
If neither destination is eligible, Return is rejected and the editor stays
in its floating window.

Application commands use the same path:

```java
manager.move(editorId, DockTarget.floating());
manager.move(editorId, DockTarget.floating(new Rectangle2D(100, 100, 640, 480)));
manager.move(editorId, DockTarget.returnToMain());
```

Use `checkMove` to enable a command and explain a rejected destination. The tab
menu does this whenever it opens; checking a destination does not create a
window. Set `dockable.setMainWindowOnly(true)` for content that must remain in
the main window. Its Float command is disabled with the rejection reason.

A floating window's groups remain destinations in the **Move to** menu, so
editors can share a window. Moving its final tab elsewhere removes the empty
window without closing that Dockable. Floating windows also support the
ordinary split commands.

`DockingOptions.setFloatingWindowCustomizer` supplies a configuration bean for
the title, icons and stylesheets before display. The manager continues to own
window placement and lifecycle.

Run `de.neitzel.fx.example.docking.FloatingDockingMain` from the `fx-example`
module for a desktop demonstration:

1. Edit Notes and select a word.
2. Float Notes, then Float Readme to create two windows.
3. Choose Join Readme to move Notes into the second window; the empty first
   window disappears. Continue typing without clicking the editor again.
4. In that shared floating window, open Notes' tab menu and choose **Split →
   Right** to place the editors beside each other. Resize their divider.
5. Return Notes and Readme to the main window.
6. Open Project's tab menu to inspect the disabled Float command.

The buttons and tab menus use the same manager operations. This example covers
command-based transfers. The [dragging guide](docking-drag.md) covers native
mouse gestures between windows and their keyboard-focus qualification.
The [floating-window close guide](docking-close.md#closing-a-floating-window)
describes shared consent for all tabs in a window.

## Native keyboard qualification

`FloatingNativeFocusTest` sends actual AWT Robot key presses to the focused OS
window. It types into Notes in the main window, uses **Float**, moves Notes
through **Move to** into another floating window, then uses **Return to main**.
After those transfers it never clicks or explicitly repairs focus. It observes
the destination's native window and Scene focus, then verifies the exact text
sequence `1` → `12` → `123` → `1234`; the other editor remains unchanged.

This sequence passed on macOS 27.2 with Java 25 and JavaFX 27.0.1 on 2026-11-15
(one native test, zero failures, errors or skips). It qualifies command/menu
transfers on that environment. Cross-window pointer dragging has its separate
[desktop qualification](docking-drag.md#window-routing-and-input-delivery);
other OS environments have not been qualified.

Run only with an exclusive desktop slot, since the test posts real keystrokes:

```sh
./mvnw -pl fx -am -Dtest=FloatingNativeFocusTest \
  -Dneitzellib.nativeFocusTest=true \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Without the opt-in property this test is explicitly skipped. Its fixture uses
digit keys on the active keyboard layout. It releases the keys and hides its
windows in `finally`; it does not move the pointer.

## Committed native window geometry

A floating `DockLayoutView.Window` exposes its last committed screen rectangle
as `bounds()`. Main-window bounds are `null`: the application owns its geometry.
Geometry property changes are collected while a native mouse button remains
down, including a pause in a titlebar drag or border resize. Publication resumes
when the native query observes release. A timeout alone cannot identify this
boundary because the user can pause with the button held.
The same boundary covers nested split ratios: a selection or other unrelated
publication during native resizing retains both the previous frame and its
committed split geometry. Native regression coverage shrinks a floating window
with unequal group minima, verifies that the live divider clamps while the
published ratio stays unchanged, then verifies the new frame and ratio after
release.

The implementation uses Java 25's Foreign Function and Memory API and existing
OS libraries, without an additional native-access dependency. Enable native
access for the module containing this library; for classpath applications use
`--enable-native-access=ALL-UNNAMED` (and `javafx.graphics` when JavaFX is on the
module path). A denied native call is treated as unknown state, never as release.

Backend boundaries are explicit:

- macOS uses combined-session CoreGraphics state for left, right and middle
  buttons, covering either primary-button mapping. The native paused-gesture
  behavior has been observed on macOS with Java 25 / JavaFX 27.
- Windows queries the five physical mouse buttons. An all-up result is accepted
  only while this process owns the foreground window, because the API can also
  return zero for inaccessible input. This backend requires native validation
  on Windows before claiming equivalent coverage.
- X11 queries the pointer mask on a separate display connection. Its logical
  state can lag frozen device processing; this backend requires X11 validation.
- Wayland sessions, unavailable libraries, denied native access and unrecognized
  backends retain the last committed bounds and log the limitation. They do not
  silently use a timer to claim a completed native gesture. XWayland sessions
  are also conservatively excluded until independently verified.

This is mouse-state observation, not a general native window-manager gesture
API. Touch/keyboard-driven native geometry and delayed OS notifications need
separate validation. Global button state may defer publication when an
unrelated mouse button is held.

The opt-in native regression moves the real pointer and must run with an
exclusive desktop slot:

```sh
./mvnw -pl fx -am -Dtest=FloatingWindowGeometryTest \
  -Dneitzellib.nativeGeometryTest=true \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Without that property, the native pointer case is explicitly skipped. Ordinary
public initial-bounds verification still runs. The native case includes a
watchdog and restores the pointer in `finally`.

The final native fixtures explicitly establish their initial desktop state with
one real click in the initial editor, then restore its selected range. No such
setup helper runs after a tested transfer, invalid drop or cancellation. This
avoids assuming that programmatic Stage display alone established OS focus; it
does not claim a specific operating-system defect.
