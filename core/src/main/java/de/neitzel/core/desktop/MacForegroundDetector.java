package de.neitzel.core.desktop;

import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;
import java.util.ArrayList;
import java.util.List;

/** AppKit application identity and optional Accessibility window observations, without prompting. */
final class MacForegroundDetector implements ForegroundDetector {
    private final NativeLibrary objc = NativeLibrary.getInstance("objc");
    @SuppressWarnings("unused") private final NativeLibrary appkit = NativeLibrary.getInstance("/System/Library/Frameworks/AppKit.framework/AppKit");
    private final Function message = objc.getFunction("objc_msgSend");
    private final CoreFoundation cf = Native.load("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", CoreFoundation.class);
    private final Accessibility ax = Native.load("/System/Library/Frameworks/ApplicationServices.framework/ApplicationServices", Accessibility.class);
    private static final int UTF8 = 0x08000100;
    private static final String PERMISSION = "Window details unavailable. To enable them, open System Settings > Privacy & Security > Accessibility and allow this application. Application rules remain available.";

    interface CoreFoundation extends Library {
        Pointer CFStringCreateWithCString(Pointer allocator, String value, int encoding);
        byte CFStringGetCString(Pointer value, byte[] buffer, long size, int encoding);
        long CFGetTypeID(Pointer value);
        long CFHash(Pointer value);
        long CFStringGetTypeID();
        long CFArrayGetCount(Pointer value);
        Pointer CFArrayGetValueAtIndex(Pointer value, long index);
        void CFRelease(Pointer value);
    }
    interface Accessibility extends Library {
        byte AXIsProcessTrusted();
        Pointer AXUIElementCreateApplication(int pid);
        int AXUIElementSetMessagingTimeout(Pointer value, float timeout);
        int AXUIElementCopyAttributeValue(Pointer value, Pointer attribute, PointerByReference result);
    }

    private Pointer cls(String name) { return objc.getFunction("objc_getClass").invokePointer(new Object[]{name}); }
    private Pointer selector(String name) { return objc.getFunction("sel_registerName").invokePointer(new Object[]{name}); }
    private Pointer send(Pointer target, String method, Object... args) { return message.invokePointer(arguments(target, method, args)); }
    private long number(Pointer target, String method) { return message.invokeLong(arguments(target, method)); }
    private Object[] arguments(Pointer target, String method, Object... args) {
        Object[] all = new Object[args.length + 2]; all[0] = target; all[1] = selector(method); System.arraycopy(args, 0, all, 2, args.length); return all;
    }
    private String string(Pointer value) {
        if (value == null) return null;
        Pointer bytes = send(value, "UTF8String"); return bytes == null ? null : bytes.getString(0, "UTF-8");
    }
    private Pointer pool() { return send(send(cls("NSAutoreleasePool"), "alloc"), "init"); }
    private void releasePool(Pointer pool) { message.invokeVoid(arguments(pool, "drain")); }
    private Application application(Pointer app) {
        if (app == null) return null;
        String identity = string(send(app, "bundleIdentifier"));
        if (identity == null || identity.isBlank()) return null;
        String name = string(send(app, "localizedName"));
        return new Application("macos.bundleId", identity, name == null ? identity : name, number(app, "processIdentifier"));
    }
    private Pointer attribute(Pointer element, String name) {
        Pointer key = cf.CFStringCreateWithCString(null, name, UTF8);
        try {
            PointerByReference result = new PointerByReference();
            return ax.AXUIElementCopyAttributeValue(element, key, result) == 0 ? result.getValue() : null;
        } finally { cf.CFRelease(key); }
    }
    private String title(Pointer element) {
        Pointer value = attribute(element, "AXTitle");
        if (value == null) return null;
        try {
            if (cf.CFGetTypeID(value) != cf.CFStringGetTypeID()) return null;
            byte[] bytes = new byte[16384];
            return cf.CFStringGetCString(value, bytes, bytes.length, UTF8) != 0 ? Native.toString(bytes, "UTF-8") : null;
        } finally { cf.CFRelease(value); }
    }
    private Window focusedWindow(Application app) {
        if (app == null || ax.AXIsProcessTrusted() == 0) return null;
        Pointer element = ax.AXUIElementCreateApplication((int)app.processId());
        try {
            ax.AXUIElementSetMessagingTimeout(element, .10f);
            Pointer window = attribute(element, "AXFocusedWindow");
            if (window == null) return null;
            try { return new Window("ax:" + app.processId() + ":" + cf.CFHash(window), app, title(window)); }
            finally { cf.CFRelease(window); }
        } finally { cf.CFRelease(element); }
    }

    public Snapshot snapshot() {
        Pointer pool = pool();
        try {
            Application app = application(send(send(cls("NSWorkspace"), "sharedWorkspace"), "frontmostApplication"));
            Window window = focusedWindow(app);
            String limitation = "";
            if (app != null && (window == null || window.title() == null))
                limitation = ax.AXIsProcessTrusted() == 0 ? PERMISSION : "The active application does not expose its focused window or title.";
            return new Snapshot(app, window, true, limitation);
        } finally { releasePool(pool); }
    }

    public Inventory inventory() {
        Pointer pool = pool();
        try {
            List<Application> apps = new ArrayList<>(); List<Window> windows = new ArrayList<>();
            Pointer running = send(send(cls("NSWorkspace"), "sharedWorkspace"), "runningApplications");
            long count = number(running, "count");
            boolean trusted = ax.AXIsProcessTrusted() != 0;
            for (long i = 0; i < count; i++) {
                Pointer nativeApp = send(running, "objectAtIndex:", new NativeLong(i));
                if (number(nativeApp, "activationPolicy") != 0) continue;
                Application app = application(nativeApp); if (app == null) continue; apps.add(app);
                if (!trusted) continue;
                Pointer element = ax.AXUIElementCreateApplication((int)app.processId());
                try {
                    ax.AXUIElementSetMessagingTimeout(element, .05f);
                    Pointer values = attribute(element, "AXWindows");
                    if (values == null) continue;
                    try {
                        long size = cf.CFArrayGetCount(values);
                        for (long j = 0; j < size; j++) {
                            Pointer window = cf.CFArrayGetValueAtIndex(values, j);
                            windows.add(new Window("ax:" + app.processId() + ":" + cf.CFHash(window), app, title(window)));
                        }
                    } finally { cf.CFRelease(values); }
                } finally { cf.CFRelease(element); }
            }
            return new Inventory(apps, windows, true, trusted ? "" : PERMISSION);
        } finally { releasePool(pool); }
    }
}
