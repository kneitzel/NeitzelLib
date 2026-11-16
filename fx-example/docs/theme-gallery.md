# Shared theme gallery

`de.neitzel.fx.example.theme.ThemeGalleryMain` opens the opt-in Light/Dark/High contrast gallery. It includes normal/hover/focus/disabled buttons, text edits, invalid/error/warning messages, check/radio/combo/date controls, a selected standard TableView, a filtered/selected DataGrid, a loading indicator, dialogs and floating editors.

After the reactor `verify` or `package` build, from the repository root (no local artifact install required):

```sh
java -cp 'fx-example/target/fx-example-1.0-SNAPSHOT.jar:fx-example/target/libs/*' \
  de.neitzel.fx.example.theme.ThemeGalleryMain
```

Choose a theme in the top selector. Open a filter editor through the funnel button, a dialog or floating editor, and edit text. Theme changes preserve model/query/selection/layout and text drafts; an ordinary outside click still follows the filter popup's dismissal policy. API-driven switching while a draft is open is tested independently of that policy. The hover/focus buttons are labelled CSS-state specimens; they do not pretend to be native-input tests.

FILE/LIVE/REMOVE are example textual indicators which remain readable under selection. They are not a qualification of DNSCheck's actual domain palette, legends or workflows; that work remains in its integration ticket.

## Automated rendered evidence

`ThemeGalleryTest` writes `target/theme-gallery/light.png`, `dark.png`, `high-contrast.png`, matching popup images, `contrast.txt` and `window-contrast.txt`. The tests read actual JavaFX paints/backgrounds after CSS resolution, enforce the agreed contrast targets, check stable button dimensions and preserved grid/draft state, and fail on uncaught FX-thread errors. Main and popup snapshots are visually reviewed for clipping, focus/state distinctions and readability. The workspace implementation ticket stores durable copies and the qualified runtime baseline.

`ThemeManagerTest` covers opt-in behavior, application overrides, scene-root replacement, rollback across scenes, unavailable saved themes, live filter/nested-popup/dialog propagation, new-window registration, docking floating windows and cleanup. Existing legacy Table/docking tests remain in verification.

See [theme API and customization](../../fx/docs/themes.md) for ownership, CSS precedence, public palette/hooks and failure limitations. Application overrides, other fonts/scales/platforms, SubScene and HTML content need separate qualification.
