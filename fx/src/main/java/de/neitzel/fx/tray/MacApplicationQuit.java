package de.neitzel.fx.tray;

import com.sun.glass.ui.Application;

import java.util.Map;

/** Isolates the JavaFX native Quit hook, which has no public JavaFX equivalent. */
final class MacApplicationQuit {
    private MacApplicationQuit() {}

    static Runnable install(Runnable quit) {
        if (!System.getProperty("os.name", "").startsWith("Mac")) return () -> {};
        Application app = Application.GetApplication();
        Application.EventHandler previous = app.getEventHandler();
        Application.EventHandler handler = new Application.EventHandler() {
            @Override public void handleQuitAction(Application a, long time) { quit.run(); }
            @Override public void handleWillFinishLaunchingAction(Application a, long t) { if (previous != null) previous.handleWillFinishLaunchingAction(a,t); }
            @Override public void handleDidFinishLaunchingAction(Application a, long t) { if (previous != null) previous.handleDidFinishLaunchingAction(a,t); }
            @Override public void handleWillBecomeActiveAction(Application a, long t) { if (previous != null) previous.handleWillBecomeActiveAction(a,t); }
            @Override public void handleDidBecomeActiveAction(Application a, long t) { if (previous != null) previous.handleDidBecomeActiveAction(a,t); }
            @Override public void handleWillResignActiveAction(Application a, long t) { if (previous != null) previous.handleWillResignActiveAction(a,t); }
            @Override public void handleDidResignActiveAction(Application a, long t) { if (previous != null) previous.handleDidResignActiveAction(a,t); }
            @Override public void handleDidReceiveMemoryWarning(Application a, long t) { if (previous != null) previous.handleDidReceiveMemoryWarning(a,t); }
            @Override public void handleWillHideAction(Application a, long t) { if (previous != null) previous.handleWillHideAction(a,t); }
            @Override public void handleDidHideAction(Application a, long t) { if (previous != null) previous.handleDidHideAction(a,t); }
            @Override public void handleWillUnhideAction(Application a, long t) { if (previous != null) previous.handleWillUnhideAction(a,t); }
            @Override public void handleDidUnhideAction(Application a, long t) { if (previous != null) previous.handleDidUnhideAction(a,t); }
            @Override public void handleOpenFilesAction(Application a, long t, String[] files) { if (previous != null) previous.handleOpenFilesAction(a,t,files); }
            @Override
            public void handlePreferencesChanged(Map<String, Object> preferences) {
                if (previous != null) {
                    previous.handlePreferencesChanged(preferences);
                }
            }
        };
        app.setEventHandler(handler);
        return () -> {
            if (app.getEventHandler() == handler) app.setEventHandler(previous);
        };
    }
}
