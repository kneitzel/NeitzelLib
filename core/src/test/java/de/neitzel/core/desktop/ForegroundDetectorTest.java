package de.neitzel.core.desktop;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

class ForegroundDetectorTest {
    @Test void waylandDoesNotClaimFullFocusSupportEvenWithAnXwaylandDisplay() {
        try (var detector = ForegroundDetector.forEnvironment("Linux", Map.of(
                "XDG_SESSION_TYPE", "wayland", "WAYLAND_DISPLAY", "wayland-0", "DISPLAY", ":0"))) {
            var snapshot = detector.snapshot();
            assertFalse(snapshot.focusAvailable());
            assertNull(snapshot.application());
            assertTrue(snapshot.limitation().contains("Wayland"));
            assertFalse(detector.inventory().available());
        }
    }

    @Test void aClosedNativeDetectorDoesNotStartOrResumeObservation() {
        var detector = ForegroundDetector.forEnvironment("Mac OS X", Map.of());
        detector.close();
        assertFalse(detector.snapshot().focusAvailable());
        assertTrue(detector.snapshot().limitation().contains("closed"));
        assertFalse(detector.inventory().available());
        detector.close();
    }

    @Test void unsupportedSessionsRemainUsableWithoutNativeLibraries() {
        try (var detector = ForegroundDetector.forEnvironment("Linux", Map.of())) {
            assertFalse(detector.snapshot().focusAvailable());
            assertTrue(detector.snapshot().limitation().contains("X11"));
        }
        try (var detector = ForegroundDetector.forEnvironment("Unknown", Map.of())) {
            assertFalse(detector.snapshot().focusAvailable());
            assertFalse(detector.inventory().available());
        }
    }

    @Test void windowDiagnosticsIgnoreTitlesEvenWhenTheyMatchApplicationMetadata() {
        var application = new ForegroundDetector.Application("test", "example.editor", "Editor", 42);
        var withoutTitle = new ForegroundDetector.Window("window-1", application, null);
        // A title can coincide with application metadata that diagnostics legitimately include.
        assertTrue(withoutTitle.toString().contains("Editor"));
        for (String title : new String[] {"Editor", "private-document-title"}) {
            var window = new ForegroundDetector.Window("window-1", application, title);
            assertEquals(withoutTitle.toString(), window.toString());
        }
    }

    @Test @EnabledIfSystemProperty(named = "desktop.native.tests", matches = "true")
    void nativeForegroundAndInventoryAreObservableWithoutLoggingTitles() {
        try (var detector = ForegroundDetector.system()) {
            var snapshot = detector.snapshot();
            assertTrue(snapshot.focusAvailable(), snapshot.limitation());
            assertNotNull(snapshot.application(), "Run this smoke check with an external application in the foreground");
            assertFalse(snapshot.application().identity().isBlank());
            var inventory = detector.inventory();
            assertTrue(inventory.available(), inventory.limitation());
            assertFalse(inventory.applications().isEmpty());
            if (snapshot.window() != null) {
                var window = snapshot.window();
                var withoutTitle = new ForegroundDetector.Window(window.id(), window.application(), null);
                // Compare independence from the title, not accidental substring overlap.
                // A boolean assertion keeps real desktop metadata out of failure messages.
                assertTrue(withoutTitle.toString().equals(window.toString()),
                        "Window diagnostics must not depend on the title");
            }
            System.out.println("Native desktop smoke: namespace=" + snapshot.application().namespace()
                    + ", appCount=" + inventory.applications().size() + ", windowCount=" + inventory.windows().size()
                    + ", titleAvailable=" + (snapshot.window() != null && snapshot.window().title() != null));
        }
    }
}
