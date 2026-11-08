package de.neitzel.core.desktop;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Win32 top-level windows; unavailable process identity is never replaced by a guessed name. */
final class WindowsForegroundDetector implements ForegroundDetector {
    interface WindowCallback extends StdCallLibrary.StdCallCallback { boolean invoke(Pointer window, Pointer data); }
    interface User32 extends StdCallLibrary {
        Pointer GetForegroundWindow();
        int GetWindowThreadProcessId(Pointer window, IntByReference pid);
        int GetWindowTextW(Pointer window, char[] text, int size);
        boolean IsWindowVisible(Pointer window);
        boolean EnumWindows(WindowCallback callback, Pointer data);
    }
    interface Kernel32 extends StdCallLibrary {
        Pointer OpenProcess(int access, boolean inherit, int pid);
        boolean QueryFullProcessImageNameW(Pointer process, int flags, char[] path, IntByReference size);
        boolean CloseHandle(Pointer handle);
    }
    private final User32 user = Native.load("user32", User32.class);
    private final Kernel32 kernel = Native.load("kernel32", Kernel32.class);

    private Window window(Pointer handle) {
        if (handle == null) return null;
        IntByReference pid = new IntByReference(); user.GetWindowThreadProcessId(handle, pid);
        Pointer process = kernel.OpenProcess(0x1000, false, pid.getValue());
        if (process == null) return null;
        String path;
        try {
            char[] value = new char[32768]; IntByReference size = new IntByReference(value.length);
            if (!kernel.QueryFullProcessImageNameW(process, 0, value, size)) return null;
            path = new String(value, 0, size.getValue());
        } finally { kernel.CloseHandle(process); }
        // A generic host executable does not identify the packaged application it is displaying.
        if (path.toLowerCase(Locale.ROOT).endsWith("\\applicationframehost.exe")) return null;
        int slash = path.lastIndexOf('\\');
        Application app = new Application("windows.exe", path.toLowerCase(Locale.ROOT), path.substring(slash + 1), Integer.toUnsignedLong(pid.getValue()));
        char[] text = new char[8192]; int count = user.GetWindowTextW(handle, text, text.length);
        return new Window("hwnd:" + Long.toUnsignedString(Pointer.nativeValue(handle)), app, count > 0 ? new String(text, 0, count) : null);
    }

    public Snapshot snapshot() {
        Pointer handle = user.GetForegroundWindow();
        Window window = window(handle);
        return new Snapshot(window == null ? null : window.application(), window, true,
                handle != null && window == null ? "Foreground application identity is unavailable or hosted; use a supported application or Manual mode." : "");
    }

    public Inventory inventory() {
        List<Window> windows = new ArrayList<>();
        var apps = new LinkedHashMap<String, Application>();
        boolean available = user.EnumWindows((handle, data) -> {
            if (user.IsWindowVisible(handle)) {
                Window window = window(handle);
                if (window != null) { windows.add(window); apps.putIfAbsent(window.application().identity(), window.application()); }
            }
            return true;
        }, null);
        return new Inventory(new ArrayList<>(apps.values()), windows, available,
                available ? "Some hosted or protected application identities may be unavailable." : "Window enumeration is unavailable.");
    }
}
