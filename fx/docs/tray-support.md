# Tray lifecycle

`TraySupport.attach(stage, awtImage, tooltip, mayQuit, quit)` attaches one primary
JavaFX window to the native AWT system tray. Call attach and lifecycle methods on
the FX thread. The asynchronous backend keeps AWT initialization off that thread.
`availableProperty()` becomes true only after the icon is installed. While
available, window close hides the stage and implicit JavaFX exit is disabled.
Ordinary minimization is unchanged. Open shows, deiconifies and focuses the stage.

The tray menu offers Open and Quit. `requestQuit()` restores the window and calls
`mayQuit` on the FX thread; false cancels without removing the icon. On acceptance,
the registration is removed and `quit` runs (typically Platform::exit). Applications
own Save/Discard/Cancel prompts. `close()` removes handlers/icon and restores the
previous implicit-exit setting; call it during application shutdown as well.

On macOS, native application Quit requests (including Dock/menu Quit) use this
same confirmation path, including when the window is hidden or the tray is
unavailable. JavaFX normally translates these requests into window-close events;
AWT Desktop quit handlers do not intercept them. `MacApplicationQuit` isolates
the JavaFX 21 Glass hook, delegates other application events to the previous
handler, and restores it on close. One primary-window attachment owns this hook.
This uses an internal JavaFX API and needs rechecking when upgrading JavaFX.
Classpath applications need no extra flags. When JavaFX is on the module path,
add `--add-exports=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED` at runtime.

Unsupported, failed or still-pending tray installation never hides the only window.
Window close restores it instead. Applications must retain an accessible explicit
Quit action for that fallback. Native callbacks are marshalled onto FX. An install
that completes after disposal is immediately removed, including during shutdown.

An overload accepts a Backend for alternative tray implementations. Its future
resolves to a Registration, or null if unsupported; Registration.close must be
safe from any thread. This is also the public seam for lifecycle tests. The tests
exercise real JavaFX Stage close/open, cancelled/accepted quit, and unsupported
fallback with deterministic native registrations. Native platform behavior must
also be checked in the consuming application.


## Application integration

Keep the handle as application state and give users a Quit button/menu even if the tray is unavailable:

```java
// In Application.start(Stage stage); icon is a java.awt.Image.
tray = TraySupport.attach(stage, icon, "My application",
        () -> confirmUnsavedChanges(), Platform::exit);
quitButton.setOnAction(event -> tray.requestQuit());
```

```java
// Application field and shutdown hook:
private TraySupport tray;

@Override
public void stop() {
    if (tray != null) tray.close();
}
```

Import `de.neitzel.fx.tray.TraySupport` and `javafx.application.Platform`. Supply your own `java.awt.Image`, confirmation function and Button. This is an integration fragment, not a complete application. `mayQuit` must return whether quitting may proceed; the library does not save documents itself. `isAvailable()` reads current availability, while the read-only `availableProperty()` can drive a status label. A pending installation is not availability.

Use one owner for the application's primary tray lifecycle: JavaFX implicit exit is global, so independent handles are not a coordinated multi-window policy. This API does not intercept every operating-system application Quit action. `close()` is idempotent and releases only its own registration and handlers; ordinary application resources still need their own cleanup.
