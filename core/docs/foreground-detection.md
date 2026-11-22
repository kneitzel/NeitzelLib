# Foreground desktop observations

`de.neitzel.core.desktop.ForegroundDetector.system()` supplies a local foreground
snapshot and a separate application/window inventory. It belongs to core and is
available transitively to NeitzelLib FX clients. The native bridge is JNA 5.13.0,
packaged as an ordinary runtime dependency; no project-specific native compiler
or helper binary is required.

Call `snapshot()` on a background polling worker (250 ms is an application policy,
not a deadline guarantee). Call `inventory()` when opening or refreshing a rule
picker rather than on every foreground poll. Close the detector on application
shutdown. The API does not select profiles, persist observations, prompt for
permissions, or log observed titles.

## Contract

- Application namespace plus identity is suitable for a platform-specific rule.
  Display names are descriptive. Process IDs are transient and allow a client to
  recognize its own application; they are not saved rule keys.
- Window IDs are transient; only explicitly chosen conditions such as title text
  should be saved. A null window or title represents unavailable details.
- A snapshot with `focusAvailable=true` and no application means no identifiable
  app currently has focus. `focusAvailable=false` means focus observation is
  unavailable; the limitation explains why. Do not retain an old observation as
  current after unavailability.
- Inventory availability is separate from focus and detail availability. A macOS
  app list remains useful even if window permission is absent.
- Lists are immutable snapshots. Window diagnostic strings intentionally omit
  titles, including when nested in snapshot/inventory diagnostic strings. Clients
  must likewise exclude titles from normal logs and device messages.
- A private daemon worker serializes native calls and bounds the caller's wait to
  1.5 seconds. If a native query does not return, no additional native queries are
  queued; later calls report unavailability until it finishes. Closing prevents
  any further observations and requests worker interruption. An OS call already
  blocked in native code cannot be forcibly cancelled, but cannot keep the JVM
  alive by itself. Window enumeration may take longer than foreground observation.

## Platforms and permissions

**macOS:** AppKit `NSWorkspace` supplies regular GUI applications and foreground
bundle identity (`macos.bundleId`). Application rules require no Accessibility
prompt. Accessibility supplies focused windows, optional titles, and window lists
when access is already granted. Missing details do not make app-level detection
unavailable. The limitation tells the user to open **System Settings > Privacy &
Security > Accessibility** and allow the packaged application. Initiate setup only
on an explicit user action; this library never calls the trust API with prompting.
Each Accessibility app query has a short native messaging timeout. AX window IDs
are observation-only identities. Apps without bundle identity are not guessed.

**Windows:** Win32 foreground and top-level-window enumeration supply executable
identity (`windows.exe`, case-normalized full path), optional titles, and grouped
visible windows. A process that cannot be queried has no guessed identity.
Generic ApplicationFrameHost windows are reported as unavailable rather than
incorrectly assigning the host executable to the visible packaged app. Protected
or hosted apps may require a future dedicated identity adapter. Win32 titles
are top-level captions, not arbitrary foreign control text.

**Linux/X11:** An independent X connection queries EWMH foreground/client-list
properties and WM_CLASS (`x11.wmClass`). UTF-8 titles fall back to legacy WM_NAME;
missing WM_CLASS is not replaced by a guessed process name. Window disappearance
is tolerated with an X error handler scoped to the private display, restoring the
previous process handler after each query. The runtime needs libX11 and an
accessible EWMH-compatible desktop session. Missing focus or window-list
properties are reported separately.

**Wayland:** Unsupported in this version. An actual Wayland session remains
unsupported even if DISPLAY points at XWayland. A caller should expose default
Automatic fallback and Manual selection rather than suggesting full-session
detection is working. No compositor extensions or bridges are installed.

## Tests and observed evidence

Public capability/lifecycle tests run with `mvn -pl core
-Dtest=ForegroundDetectorTest test`. Surefire fails the build when a test fails.

Native opt-in smoke check: add `-Ddesktop.native.tests=true` in a graphical
session with an identifiable external application foreground. It validates focus
identity and inventories without printing window titles. It is intentionally not
enabled on headless CI.

On 2026-11-08, macOS arm64/JDK 21 public tests and the native smoke check passed:
foreground bundle identity was available and 12 regular GUI applications were
listed. This command process had no Accessibility access: window inventory was
empty and title unavailable, while application detection remained available and
permission guidance was returned. Granted-access window/title changes, packaged
permission identity, deliberate focus switching, Windows native behavior, and
Linux/X11 native behavior have not been verified by that check. They require
their own native application acceptance checks; compilation is not evidence of
native support working.

## Primary API references

- [Apple NSWorkspace frontmostApplication](https://developer.apple.com/documentation/appkit/nsworkspace/frontmostapplication)
- [Apple Accessibility](https://developer.apple.com/documentation/applicationservices/axuielement_h)
- [Microsoft GetForegroundWindow](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-getforegroundwindow)
- [Microsoft QueryFullProcessImageNameW](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-queryfullprocessimagenamew)
- [Microsoft GetWindowTextW](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-getwindowtextw)
- [EWMH window-manager properties](https://specifications.freedesktop.org/wm/latest-single/)
- [JNA source and native mapping documentation](https://github.com/java-native-access/jna)
