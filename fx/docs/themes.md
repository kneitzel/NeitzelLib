# Opt-in shared themes

`de.neitzel.fx.theme.ThemeManager` is application-owned and uses the JavaFX application thread. Existing applications, including legacy Table users, retain their appearance until they register a scene. The bundled themes are `light`, `dark` and `high-contrast` (dark polarity). They share typography, spacing and dimensions; no filter behavior, query, page setting, selection or explicit column width is changed by a theme switch.

```java
ThemeManager themes = new ThemeManager();
ThemeManager.Registration registration = themes.register(scene);
themes.failureProperty().addListener((observable, old, failure) -> {
    if (failure != null) showThemeFailure(failure.message());
});
themes.selectSaved(applicationPreference); // unavailable preference falls back to light with explanation
// Later:
themes.select("high-contrast");
// At application/view disposal:
registration.close();
themes.close();
```

Applications persist the theme ID separately from DataGrid setups. OS-following is not enabled. A second high-contrast polarity can be supplied through `addTheme(new Theme(id, stylesheetUrls))`.

## Windows and lifetime

Register application scenes explicitly. Owned popups/dialogs inherit the manager when shown; this includes filter editors, ComboBox/DatePicker nested windows, tooltips and DataGrid confirmation alerts. A dialog must have an owner to inherit automatically. `MessagePopup.showNewMessages(model, owner)` supplies an opt-in owned-message path; the original overload remains unchanged.

Docking floating windows carry an explicit link to the main scene without changing their native ownership or close semantics. `ThemeManager.link(childWindow, ownerScene)` provides the same hook for other library windows. Ordinary owned application stages also inherit. Windows created after selection receive the current theme. Automatically registered scenes release their registration when hidden and are registered again on reopening. Explicit registrations are application-owned and must be closed; the manager closes all remaining registrations and its window listener on disposal.

Standard JavaFX Dialogs bind their stylesheet list to the owner's list. The manager scopes and validates those scenes but does not mutate the bound target list. Popups have copied stylesheet lists and are updated separately. This distinction is covered by tests that fail on uncaught FX-thread errors.

Do not use one scene with two theme managers. SubScene and web/HTML content have separate styling domains and are not covered by this scene manager.

## Cascade and customization

Managed CSS order is component defaults, shared base/selected palette, then application styles. Application stylesheet ordering is preserved on changes and shutdown. Theme bundle URLs explicitly registered with the manager are reserved as manager-owned; keep application overrides in separate URLs. Filter popups copy application styles without copying a frozen palette.

JavaFX specificity and scope still apply: parent-level sheets can outrank scene-level sheets, and inline styles outrank ordinary stylesheet rules. A later sheet alone does not guarantee an override. Use matching documented selectors, or an intentional parent/inline override. For example, an application stylesheet appended after registration can set `.neitzel-theme { -nl-focus: #005fcc; }`. Custom palettes/overrides require their own contrast qualification.

CSS controls colors, fonts, padding, borders, backgrounds, focus/state indicators and icon appearance. APIs control source/filter behavior, dismissal policy, paging, selection and saved/explicit widths. No new styleable Java properties were needed; standard JavaFX CSS properties and looked-up colors express the presentation contract.

## Palette and public hooks

All bundled palette colors are opaque. Stable looked-up colors:

| Token | Meaning |
| --- | --- |
| `-nl-surface`, `-nl-raised`, `-nl-hover` | Main, raised and hovered surfaces |
| `-nl-text`, `-nl-muted` | Normal and readable disabled/secondary text |
| `-nl-selection`, `-nl-selection-text` | Selected surface and its text |
| `-nl-focus`, `-nl-border` | Focus and control boundaries |
| `-nl-error`, `-nl-warning` | Error and warning text/indicators |

The manager adds `neitzel-theme` only to registered roots. Public library hooks include:

- `data-grid`, `data-grid-status`, `data-grid-paging`; DataGrid publishes `:loading` and `:error`.
- `result-row` and `:selected`; the previous `selected` style class remains supported. Stale/disabled selected rows retain readable text.
- `filter-button`, `filter-icon`, `filter-popup`, `filter-editor`, `filter-actions`, `filter-validation`; active buttons expose `:filtered`, a dot, a dashed boundary and an accessible active description.
- Legacy `table`, `column-header-row`, `column-cell`, `column-header`, `column-opener.filtered`, `column-popup`, `table-scroll`; behavior is unchanged.
- Existing docking hooks, including `dock-divider`, `dock-area-divider`, `dock-drop-indicator` and `dock-floating-preview-frame`.
- `theme-error` and `theme-warning` for application messages. Supply explanatory text/symbols, not color alone. Applications can use `:invalid` on input controls alongside an explanation.

Standard-control coverage includes buttons/toggles, text inputs, checkboxes/radio buttons, combo/date controls, TableView/ListView/TreeView selection, menu/dialog/tooltip surfaces and progress indicators. Internal skin selectors used by the shipped sheets follow the pinned JavaFX baseline; applications should prefer the palette/public hooks rather than duplicating skin structure.

## Failure handling

A missing theme or unreadable stylesheet leaves the previous selection and managed sheets intact and sets `failureProperty`. `selectSaved` explicitly falls back to light and retains an explanation. `select` returns success/failure; failures are not silently ignored by the gallery.

An optional `setInstallationCheck(scene -> ...)` can perform application-specific post-CSS validation. Throwing rolls back managed sheets across registered scenes. Checks should be read-only and must not create/dispose windows or reenter the manager. JavaFX does not surface every CSS warning as an exception; the manager does not claim transactional detection/recovery for every parser diagnostic or visual defect.

## Qualification

The shared gallery tests measure resolved control foreground/background combinations after CSS application, including normal, hover, focus, disabled, invalid, selected TableView/DataGrid, selected disabled rows, errors/warnings, filter icons, checkbox boundaries, popup drafts, dialogs and floating windows. Light/dark normal text targets 4.5:1, high-contrast text 7:1, meaningful boundaries/icons/focus 3:1. These are desktop design targets, not a claim of full WCAG or accessibility conformance.

See the [runnable gallery](../../fx-example/docs/theme-gallery.md) for screenshots, measurements and scope. Focus/hover specimens deliberately expose CSS states; they are not evidence of native keyboard routing. The user's earlier popup-native-test waiver remains separate from theme-state rendering qualification.

DNSCheck owns file/live/removal palette entries and row/legend semantics. This library does not introduce DNS domain colors. Actual DNSCheck integration/selection combinations remain the dedicated downstream ticket; the gallery's FILE/LIVE/REMOVE text demonstrates non-color indicators only.
