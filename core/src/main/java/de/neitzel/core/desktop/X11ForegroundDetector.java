package de.neitzel.core.desktop;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.NativeLongByReference;
import com.sun.jna.ptr.PointerByReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** EWMH on a private X connection. Never selected merely because XWayland exports DISPLAY. */
final class X11ForegroundDetector implements ForegroundDetector {
    interface XErrorHandler extends Callback { int invoke(Pointer display, Pointer event); }
    interface X11 extends Library {
        Pointer XOpenDisplay(String name);
        int XCloseDisplay(Pointer display);
        NativeLong XDefaultRootWindow(Pointer display);
        NativeLong XInternAtom(Pointer display, String name, boolean onlyIfExists);
        int XGetWindowProperty(Pointer display, NativeLong window, NativeLong property, NativeLong offset,
                NativeLong length, boolean delete, NativeLong type, NativeLongByReference actualType,
                IntByReference format, NativeLongByReference count, NativeLongByReference remaining, PointerByReference data);
        int XFree(Pointer value);
        Pointer XSetErrorHandler(XErrorHandler handler);
    }
    private final X11 x = Native.load("X11", X11.class);
    private final String displayName;
    private Pointer display;
    private static final Object ERROR_LOCK = new Object();

    X11ForegroundDetector(String displayName) { this.displayName = displayName; }

    private record Property(int format, byte[] bytes, long[] numbers) { }
    private Property property(long window, String name) {
        NativeLong atom = x.XInternAtom(display, name, true);
        if (atom.longValue() == 0) return null;
        NativeLongByReference actual = new NativeLongByReference(), count = new NativeLongByReference(), remaining = new NativeLongByReference();
        IntByReference format = new IntByReference(); PointerByReference data = new PointerByReference();
        int result = x.XGetWindowProperty(display, new NativeLong(window), atom, new NativeLong(0), new NativeLong(4096), false,
                new NativeLong(0), actual, format, count, remaining, data);
        Pointer bytes = data.getValue();
        if (bytes == null) return null;
        try {
            if (result != 0 || remaining.getValue().longValue() != 0) return null;
            int size = (int)Math.min(count.getValue().longValue(), 16384);
            if (format.getValue() == 8) return new Property(8, bytes.getByteArray(0, size), null);
            if (format.getValue() == 32) {
                long[] values = new long[size];
                for (int i = 0; i < size; i++) values[i] = bytes.getNativeLong((long)i * NativeLong.SIZE).longValue();
                return new Property(32, null, values);
            }
            return null;
        } finally { x.XFree(bytes); }
    }
    private String text(long window, String name, java.nio.charset.Charset encoding) {
        Property value = property(window, name);
        if (value == null || value.bytes == null) return null;
        int size = value.bytes.length; while (size > 0 && value.bytes[size - 1] == 0) size--;
        return new String(value.bytes, 0, size, encoding);
    }
    private long[] numbers(long window, String name) {
        Property value = property(window, name); return value == null || value.numbers == null ? new long[0] : value.numbers;
    }
    private Window window(long id) {
        if (id == 0) return null;
        String wmClass = text(id, "WM_CLASS", StandardCharsets.ISO_8859_1);
        if (wmClass == null || wmClass.isBlank()) return null;
        int split = wmClass.indexOf('\0');
        String identity = split >= 0 ? wmClass.substring(split + 1) : wmClass;
        if (identity.isBlank()) return null;
        long[] pid = numbers(id, "_NET_WM_PID");
        Application app = new Application("x11.wmClass", identity, identity, pid.length == 0 ? -1 : pid[0]);
        String title = text(id, "_NET_WM_NAME", StandardCharsets.UTF_8);
        if (title == null) title = text(id, "WM_NAME", StandardCharsets.ISO_8859_1);
        return new Window("x11:" + Long.toUnsignedString(id), app, title);
    }
    private <T> T connected(java.util.function.Supplier<T> operation, T unavailable) {
        synchronized (ERROR_LOCK) {
            display = x.XOpenDisplay(displayName);
            if (display == null) return unavailable;
            // A window can disappear between enumeration and a property request. Preserve the
            // process's handler for other displays and suppress errors only on our private display.
            Pointer[] previous = {null};
            XErrorHandler handler = (connection, event) -> {
                if (connection.equals(display) || previous[0] == null) return 0;
                return Function.getFunction(previous[0]).invokeInt(new Object[]{connection, event});
            };
            previous[0] = x.XSetErrorHandler(handler);
            try { return operation.get(); }
            finally {
                x.XCloseDisplay(display);
                XErrorHandler restore = previous[0] == null ? null : (XErrorHandler)CallbackReference.getCallback(XErrorHandler.class, previous[0]);
                x.XSetErrorHandler(restore); display = null;
                java.lang.ref.Reference.reachabilityFence(handler);
            }
        }
    }
    public Snapshot snapshot() {
        return connected(() -> {
            long root = x.XDefaultRootWindow(display).longValue();
            Property active = property(root, "_NET_ACTIVE_WINDOW");
            if (active == null || active.numbers == null) return new Snapshot(null, null, false, "The X11 window manager does not expose foreground context.");
            Window window = active.numbers.length == 0 ? null : window(active.numbers[0]);
            return new Snapshot(window == null ? null : window.application(), window, true, "");
        }, new Snapshot(null, null, false, "Cannot connect to the X11 display."));
    }
    public Inventory inventory() {
        return connected(() -> {
            long root = x.XDefaultRootWindow(display).longValue();
            Property clients = property(root, "_NET_CLIENT_LIST");
            if (clients == null || clients.numbers == null) return new Inventory(List.of(), List.of(), false, "The X11 window manager does not expose a window list.");
            List<Window> windows = new ArrayList<>(); var apps = new LinkedHashMap<String, Application>();
            for (long id : clients.numbers) {
                Window window = window(id);
                if (window != null) { windows.add(window); apps.putIfAbsent(window.application().identity(), window.application()); }
            }
            return new Inventory(new ArrayList<>(apps.values()), windows, true, "");
        }, new Inventory(List.of(), List.of(), false, "Cannot connect to the X11 display."));
    }
}
