package de.neitzel.fx.docking;

import lombok.extern.slf4j.Slf4j;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Locale;

/** Narrow, read-only native mouse state. Unknown input state never means release. */
@Slf4j
final class NativeMouseButtons {
    enum State { DOWN, UP, UNKNOWN }
    @FunctionalInterface private interface Query { State sample() throws Throwable; }
    private static boolean failed;

    private NativeMouseButtons() { }

    private static final class Backend {
        private static final Query QUERY = create();
    }

    /** Called on the JavaFX thread by floating-window geometry trackers. */
    static State state() {
        if (failed || Backend.QUERY == null) return State.UNKNOWN;
        try {
            return Backend.QUERY.sample();
        } catch (Throwable failure) {
            if (failure instanceof Error error) throw error;
            failed = true;
            log.warn("Native mouse state failed; floating-window geometry remains at its last committed bounds", failure);
            return State.UNKNOWN;
        }
    }

    private static Query create() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("mac")) return mac();
            if (os.contains("win")) return windows();
            if (os.contains("linux") && !present("WAYLAND_DISPLAY")
                    && !"wayland".equalsIgnoreCase(System.getenv("XDG_SESSION_TYPE"))
                    && !"wayland".equalsIgnoreCase(System.getenv("GDK_BACKEND")) && present("DISPLAY")) return x11();
            log.warn("Native mouse-state observation is unavailable for this desktop backend; floating-window bounds remain committed");
        } catch (RuntimeException | LinkageError failure) {
            log.warn("Native mouse-state observation is unavailable; enable native access for the library module. Floating-window bounds remain committed", failure);
        } catch (Throwable failure) {
            if (failure instanceof Error error) throw error;
            log.warn("Native mouse-state initialization failed; floating-window bounds remain committed", failure);
        }
        return null;
    }

    private static boolean present(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }

    private static Query mac() {
        Linker linker = Linker.nativeLinker();
        if (!ValueLayout.JAVA_BOOLEAN.equals(linker.canonicalLayouts().get("bool"))) {
            throw new IllegalStateException("Unsupported CoreGraphics bool ABI");
        }
        // The String overload also loads frameworks supplied only by the macOS dyld cache.
        SymbolLookup library = SymbolLookup.libraryLookup(
                "/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", Arena.global());
        MethodHandle button = linker.downcallHandle(library.findOrThrow("CGEventSourceButtonState"),
                FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        return () -> {
            // Combined-session state includes accessibility and posted input; check both primary mappings and middle.
            for (int number = 0; number <= 2; number++) {
                if ((boolean) button.invokeExact(0, number)) return State.DOWN;
            }
            return State.UP;
        };
    }

    private static Query windows() {
        Linker linker = Linker.nativeLinker();
        SymbolLookup library = SymbolLookup.libraryLookup("user32.dll", Arena.global());
        MethodHandle button = linker.downcallHandle(library.findOrThrow("GetAsyncKeyState"),
                FunctionDescriptor.of(ValueLayout.JAVA_SHORT, ValueLayout.JAVA_INT));
        MethodHandle foreground = linker.downcallHandle(library.findOrThrow("GetForegroundWindow"),
                FunctionDescriptor.of(ValueLayout.ADDRESS));
        MethodHandle owner = linker.downcallHandle(library.findOrThrow("GetWindowThreadProcessId"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        return () -> {
            // Physical buttons, including swapped primary buttons and the two extra buttons.
            for (int key : new int[] {1, 2, 4, 5, 6}) {
                if ((short) button.invokeExact(key) < 0) return State.DOWN;
            }
            // A zero key state can mean inaccessible input. Accept up only for our foreground process.
            MemorySegment window = (MemorySegment) foreground.invokeExact();
            if (window.equals(MemorySegment.NULL)) return State.UNKNOWN;
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment process = arena.allocate(ValueLayout.JAVA_INT);
                int thread = (int) owner.invokeExact(window, process);
                return thread != 0 && Integer.toUnsignedLong(process.get(ValueLayout.JAVA_INT, 0)) == ProcessHandle.current().pid()
                        ? State.UP : State.UNKNOWN;
            }
        };
    }

    /** Both shutdown and sampling hold this monitor before accessing the display. */
    private static final class X11ConnectionState {
        private boolean closed;
    }

    private static Query x11() throws Throwable {
        Linker linker = Linker.nativeLinker();
        if (!ValueLayout.JAVA_LONG.equals(linker.canonicalLayouts().get("long"))) {
            throw new IllegalStateException("Unsupported X11 Window ABI");
        }
        SymbolLookup library = SymbolLookup.libraryLookup("libX11.so.6", Arena.global());
        MethodHandle open = linker.downcallHandle(library.findOrThrow("XOpenDisplay"),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        MethodHandle root = linker.downcallHandle(library.findOrThrow("XDefaultRootWindow"),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
        MethodHandle query = linker.downcallHandle(library.findOrThrow("XQueryPointer"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        MethodHandle close = linker.downcallHandle(library.findOrThrow("XCloseDisplay"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        X11ConnectionState connection = new X11ConnectionState();
        MemorySegment display = (MemorySegment) open.invokeExact(MemorySegment.NULL);
        if (display.equals(MemorySegment.NULL)) throw new IllegalStateException("Cannot open the X11 display");
        try {
            long rootWindow = (long) root.invokeExact(display);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                synchronized (connection) {
                    if (connection.closed) return;
                    connection.closed = true;
                    try { int ignored = (int) close.invokeExact(display); }
                    catch (Throwable failure) {
                        if (failure instanceof Error error) throw error;
                        log.debug("Closing native mouse-state X11 connection failed", failure);
                    }
                }
            }, "docking-mouse-state-cleanup"));
            return () -> {
                synchronized (connection) {
                    if (connection.closed) return State.UNKNOWN;
                    try (Arena arena = Arena.ofConfined()) {
                        MemorySegment rootResult = arena.allocate(ValueLayout.JAVA_LONG);
                        MemorySegment child = arena.allocate(ValueLayout.JAVA_LONG);
                        MemorySegment rootX = arena.allocate(ValueLayout.JAVA_INT);
                        MemorySegment rootY = arena.allocate(ValueLayout.JAVA_INT);
                        MemorySegment windowX = arena.allocate(ValueLayout.JAVA_INT);
                        MemorySegment windowY = arena.allocate(ValueLayout.JAVA_INT);
                        MemorySegment mask = arena.allocate(ValueLayout.JAVA_INT);
                        int valid = (int) query.invokeExact(display, rootWindow, rootResult, child,
                                rootX, rootY, windowX, windowY, mask);
                        if (valid == 0) return State.UNKNOWN;
                        return (mask.get(ValueLayout.JAVA_INT, 0) & 0x1f00) != 0 ? State.DOWN : State.UP;
                    }
                }
            };
        } catch (Throwable failure) {
            synchronized (connection) {
                if (!connection.closed) {
                    connection.closed = true;
                    try { int ignored = (int) close.invokeExact(display); }
                    catch (Throwable cleanup) {
                        if (cleanup != failure) failure.addSuppressed(cleanup);
                    }
                }
            }
            throw failure;
        }
    }
}
