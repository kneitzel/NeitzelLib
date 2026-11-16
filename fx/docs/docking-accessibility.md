# Accessible names and translated docking actions

A Dockable with a visible title uses that title as its accessible tab name.
For an icon-only tab, supply an application name:

```java
Dockable project = new Dockable(new DockableId("project"), projectView);
project.setIcon(projectIcon);
project.setAccessibleName("Project files");
```

`accessibleNameProperty()` is bindable. It names an icon-only tab, whose title
is empty or blank; it does not replace the spoken name of a visibly titled tab.
The application title and original icon remain unchanged when the tab moves or
floats. JavaFX exposes the accessible name as the header's text attribute while
the default stylesheet displays only its graphic. An application supplying an
icon-only tab must supply a meaningful name; an unnamed icon has no useful
spoken label.

## Translations and placement failures

Set `DockingOptions.resourceBundle` before attaching the manager. Its String
entries are captured at attachment, so replacing the options bundle afterward
does not alter an existing manager. Unspecified built-in keys use the library's
English defaults. The tab header's close button also uses `dock.action.close`
as its accessible action name, including after a transfer into a floating window.
Action keys include `dock.action.move`, `split`, `close`,
`float`, `return`, and `pin` with the same `dock.action.` prefix. Direction keys
are `dock.side.TOP`, `RIGHT`, `BOTTOM`, and `LEFT` with `dock.side.` as the prefix.

Placement rules can attach an application message key without losing technical
diagnostics:

```java
DockFailure failure = new DockFailure(DockFailure.Code.PLACEMENT_REJECTED,
        dockableId, "Workspace policy prohibits this destination", cause,
        "workspace.destination.locked");
String visibleText = manager.describeFailure(failure);
```

The built-in context menus use the same `describeFailure` method as application
commands. An available application key wins. A missing application key falls
back to the failure code's default text (`dock.failure.` followed by the enum
name), not the technical diagnostic string. Code, key, diagnostic message and
cause remain available unchanged on the failure. Existing failures without a
key retain their supplied message unless the bundle overrides the code text.

`dock.action.unavailable` and `dock.action.noChange` are MessageFormat patterns.
The former accepts the action name and reason; the latter accepts the action
name. Keep those placeholders in translations.

## Application styles

A DockPane installs the library's scoped `docking.css` before existing Scene
stylesheets. Add your application theme to the Scene as usual. Configure a
floating window's stylesheets through `floatingWindowCustomizer` when the same
theme should apply there. The floating drag-preview popup inherits its owner's
Scene styles; it does not open a separate unthemed application window.

The tab-header hook is `.dock-tab-header`. An icon-only header additionally has
`.dock-icon-only`; keep its graphic-only content display if the visible header
should remain an icon. `.dock-drop-indicator` styles the in-window preview;
its `:no-change` pseudo-class identifies a drop which retains the placement.
The outside-window preview uses `.dock-floating-preview-frame` for fill/stroke
and `.dock-floating-preview-label` for the action label.

```css
.dock-tab-header { -fx-background-color: #dce8f1; }
.dock-drop-indicator { -fx-fill: rgba(30, 100, 180, 0.2); -fx-stroke: #1e64b4; }
.dock-floating-preview-frame { -fx-fill: rgba(30, 100, 180, 0.2); -fx-stroke: #1e64b4; }
```

The application retains control over color contrast and focus visibility when
replacing JavaFX's control styles.

`.dock-divider` styles split dividers from their first layout, and
`.dock-area-divider` styles the draggable boundary of an outer area.
`.dock-edge-area` is the outer area container. `.dock-group` is its TabPane;
`.dock-group:collapsed` identifies a closed rail. `.dock-group-surface` is the
space reserved for a group, and `.dock-overlay-layer` contains overlay groups.
These hooks are available in both main and floating DockPanes. Default divider
colors use JavaFX's looked-up theme colors, so replacing the application's base
palette also affects them.

## Keyboard commands

Focus the group's TabPane or one of its tab headers, then press **Shift+F10**
or the keyboard's **Context Menu** key. Arrow keys select actions and navigate
Move/Split submenus; Enter executes the highlighted enabled action. Move,
Split, Float, Return, Pin and Close use the manager's normal placement rules and
close consent. A successful transfer restores the content focus and keeps its
selection. Pinning and a vetoed Close return focus to the surviving content.
If the user activates another Dockable while asynchronous consent is pending,
a later veto preserves that newer focus choice.
An editor's own keyboard context menu remains the editor's responsibility.

## Runnable exercise

Launch `de.neitzel.fx.example.docking.KeyboardThemeDockingMain` from `fx-example`.
It provides a shared application theme, a named icon-only Project tab and
German docking action labels. **Focus active tab strip** places focus at the
keyboard entry point; then use Shift+F10, arrows and Enter.

- Float Notes and immediately type over the selected `cd` in `abcdef`.
  Return it through the same menu and continue typing at its caret.
- Toggle **Block floating**. Compare **Check Float** with the disabled Float
  menu item. Toggle **Missing translation key** to see the failure-code fallback.
- Close Notes with **Allow Notes to close** unchecked to exercise a veto, then
  enable it and repeat to close the tab.
- Use the Project menu to pin/unpin its whole group. Its main-window-only flag
  keeps Float unavailable. Drag another tab to inspect the themed preview and
  split/outer dividers in both window types.

The native keyboard qualification uses the public `javafx.scene.robot.Robot`
for the real Shift+F10 chord and `java.awt.Robot` for arrow navigation, Enter
and typing. It selects actions
by their focused accessible names, then types `1` and `2` without repairing
content focus: `abcdef` with
`cd` selected becomes `ab1ef`, then `ab12ef`. Qualification on **2026-11-16**
used **macOS 27.2, Java 25 (25.0.5.0.3), and JavaFX 27.0.1**. The menu closes
before its workflow transfers focus, so a
later popup auto-hide cannot restore focus to a retired floating owner.

`NativeKeyboardDockingTest` permanently uses `@RepeatedTest(3)`: all three
independent executions must pass. Run this opt-in test from the NeitzelLib root
only with an exclusive desktop; other JavaFX tests or
manual app switching can take native focus:

```sh
./mvnw -pl fx -am -DskipVersions=true -Dversions.skip=true \
  -Dtest=NativeKeyboardDockingTest \
  -Dneitzellib.nativeKeyboardTest=true \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

`KeyboardDockActionsTest`, `AccessibleDockTabsTest`, `DockingLocalizationTest`
and `DockingThemeTest` exercise the public controls/API without native input.
The accessible-attribute checks verify JavaFX's exposed names, not a complete
screen-reader audit on every operating system.

During qualification, AWT sometimes delivered F10 without the held Shift flag
on this macOS/JDK combination; the public JavaFX event trace exposed that
missing modifier before the framework handled it. The test uses the public
[JavaFX Robot](https://openjfx.io/javadoc/27/javafx.graphics/javafx/scene/robot/Robot.html)
for the chord. Its [macOS backend](https://raw.githubusercontent.com/openjdk/jfx/jfx27/modules/javafx.graphics/src/main/native-glass/mac/GlassRobot.m)
posts native accessibility keyboard events, whereas the
[JDK 25 AWT backend](https://raw.githubusercontent.com/openjdk/jdk/jdk-25-ga/src/java.desktop/macosx/native/libawt_lwawt/awt/CRobot.m)
creates CoreGraphics keyboard events from HID source state. This changes only
the test driver; the framework still requires an actual Shift+F10 event. Three
independent repetitions must each complete both transfers and exact text
assertions. They are not retries after a failure.
