package de.neitzel.core.desktop;

import java.util.List;
import java.util.Map;

/** Local desktop observations; callers own polling and application-specific selection policy. */
public interface ForegroundDetector extends AutoCloseable {
    /** A persistent application identity plus transient process information. */
    record Application(String namespace, String identity, String name, long processId) { }

    /** A transient window handle. A null title means that the title is unavailable. */
    record Window(String id, Application application, String title) {
        @Override public String toString() { return "Window[id=" + id + ", application=" + application + "]"; }
    }

    /** focusAvailable=true with a null application means no identifiable app is foreground. */
    record Snapshot(Application application, Window window, boolean focusAvailable, String limitation) { }

    /** Availability is independent of foreground detection; lists are immutable snapshots. */
    record Inventory(List<Application> applications, List<Window> windows, boolean available, String limitation) {
        public Inventory { applications = List.copyOf(applications); windows = List.copyOf(windows); }
    }

    Snapshot snapshot();
    Inventory inventory();
    @Override default void close() { }

    static ForegroundDetector system() {
        return forEnvironment(System.getProperty("os.name", ""), System.getenv());
    }

    /** Explicit environment selection also allows capability checks without probing unsupported desktops. */
    static ForegroundDetector forEnvironment(String osName, Map<String, String> environment) {
        String os = osName.toLowerCase(java.util.Locale.ROOT);
        if (os.contains("mac")) return new BoundedForegroundDetector(MacForegroundDetector::new);
        if (os.contains("windows")) return new BoundedForegroundDetector(WindowsForegroundDetector::new);
        if (os.contains("linux")) {
            if ("wayland".equalsIgnoreCase(environment.get("XDG_SESSION_TYPE"))
                    || environment.containsKey("WAYLAND_DISPLAY")) return unavailable("Wayland foreground detection is not supported; use Manual mode.");
            if (!environment.containsKey("DISPLAY")) return unavailable("No X11 display is available; use Manual mode.");
            String display = environment.get("DISPLAY");
            return new BoundedForegroundDetector(() -> new X11ForegroundDetector(display));
        }
        return unavailable("Foreground detection is unavailable on this operating system; use Manual mode.");
    }

    private static ForegroundDetector unavailable(String reason) {
        return new ForegroundDetector() {
            public Snapshot snapshot() { return new Snapshot(null, null, false, reason); }
            public Inventory inventory() { return new Inventory(List.of(), List.of(), false, reason); }
        };
    }
}
