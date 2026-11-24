package de.neitzel.fx.control;

import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HtmlViewerTest {
    @TempDir Path temporary;
    @BeforeAll static void toolkit() throws Exception {
        var started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); }
        catch (IllegalStateException running) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        fx(() -> Platform.setImplicitExit(false));
    }
    private static void fx(Runnable action) throws Exception {
        var task = new FutureTask<Void>(action, null);
        Platform.runLater(task); task.get(10, TimeUnit.SECONDS);
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        var matched = new AtomicBoolean();
        while (System.nanoTime() < deadline) {
            fx(() -> matched.set(condition.getAsBoolean()));
            if (matched.get()) return;
            Thread.sleep(20);
        }
        fail("Timed out waiting for HTML load");
    }
    private static WebView browser(HtmlViewer pane) { return (WebView) pane.lookup("WebView"); }
    private static boolean loaded(HtmlViewer pane) {
        return browser(pane).getEngine().getLoadWorker().getState() == Worker.State.SUCCEEDED;
    }
    private static String imageSource(HtmlViewer pane) {
        return browser(pane).getEngine().getDocument().getElementsByTagName("img").item(0)
                .getAttributes().getNamedItem("src") == null ? "" : browser(pane).getEngine().getDocument()
                .getElementsByTagName("img").item(0).getAttributes().getNamedItem("src").getNodeValue();
    }

    private static HtmlViewer viewer(HtmlDocument document) {
        var viewer = new HtmlViewer(); viewer.display(document); return viewer;
    }
    private static javafx.scene.control.Button media(HtmlViewer viewer) {
        return (javafx.scene.control.Button) viewer.lookup(".html-viewer-media-load");
    }

    @Test void boundLocalTextReplacesPendingWorkAndDetachesOnClose() throws Exception {
        var ref = new AtomicReference<HtmlViewer>();
        var source = new javafx.beans.property.SimpleStringProperty("<p>First</p>");
        var replacement = new javafx.beans.property.SimpleStringProperty("<p>Second</p>");
        fx(() -> {
            var viewer = new HtmlViewer();
            ref.set(viewer);
            viewer.htmlProperty().bind(source);
            viewer.htmlProperty().bind(replacement);
            source.set("<p>Detached</p>");
            replacement.set("<p>Third</p>");
        });
        var viewer = ref.get();
        try {
            await(() -> !viewer.isLoading() && loaded(viewer) && browser(viewer).getEngine().getDocument()
                    .getDocumentElement().getTextContent().contains("Third"));
            fx(() -> {
                viewer.clear();
                assertEquals("<p>Third</p>", replacement.get());
                replacement.set(null);
                replacement.set("   ");
                assertNull(viewer.getError());
                assertFalse(viewer.isExternalImagesAllowed());
                replacement.set("<p>Pending</p>");
                viewer.close();
                assertFalse(viewer.htmlProperty().isBound());
                replacement.set("<p>Too late</p>");
                assertFalse(viewer.isLoading());
                assertEquals("<p>Pending</p>", viewer.getHtml());
            });
        } finally { fx(viewer::close); }
    }

    @Test void ordinaryFxmlConstructsAnIdleLocalizableControl() throws Exception {
        fx(() -> {
            var loader = new javafx.fxml.FXMLLoader();
            String fxml = """
                    <?import de.neitzel.fx.control.HtmlViewer?>
                    <HtmlViewer xmlns:fx="http://javafx.com/fxml/1" mediaButtonVisible="false"
                        mediaLoadText="Bilder laden" mediaReloadText="Erneut laden"
                        mediaTooltipText="Nur diese Ansicht" noExternalMediaText="Keine externen Bilder"/>
                    """;
            try {
                HtmlViewer viewer = loader.load(new java.io.ByteArrayInputStream(fxml.getBytes(StandardCharsets.UTF_8)));
                try {
                    assertFalse(viewer.isLoading()); assertNull(viewer.getError());
                    assertFalse(viewer.isExternalImagesAvailable()); assertFalse(viewer.isExternalImagesAllowed());
                    assertFalse(viewer.isMediaButtonVisible()); assertFalse(viewer.getTop().isManaged());
                    assertEquals("Bilder laden", media(viewer).getText());
                    assertEquals("Keine externen Bilder", media(viewer).getTooltip().getText());
                    viewer.setMediaLoadText("Load images");
                    assertEquals("Load images", media(viewer).getText());
                } finally { viewer.close(); }
            } catch (java.io.IOException failure) { throw new RuntimeException(failure); }
        });
    }

    @Test void asynchronousFailureReplacementAndCloseDoNotPublishStaleContent() throws Exception {
        var ref = new AtomicReference<HtmlViewer>();
        fx(() -> { ref.set(new HtmlViewer()); ref.get().load(temporary.resolve("missing.html")); });
        try {
            await(() -> !ref.get().isLoading() && ref.get().getError() != null);
            fx(() -> {
                assertInstanceOf(java.io.IOException.class, ref.get().getError());
                ref.get().loadHtml("<p>Stale preparation</p>");
                assertTrue(ref.get().isLoading());
                ref.get().display(HtmlDocument.parse("<p>Current document</p>"));
            });
            await(() -> !ref.get().isLoading() && loaded(ref.get()));
            fx(() -> {
                assertNull(ref.get().getError());
                assertEquals("Current document", browser(ref.get()).getEngine().getDocument().getElementsByTagName("p").item(0).getTextContent());
                ref.get().clear();
                assertFalse(ref.get().isExternalImagesAvailable()); assertFalse(ref.get().isExternalImagesAllowed());
                ref.get().loadHtml("<p>Pending on close</p>");
                ref.get().close();
                assertFalse(ref.get().isLoading());
                assertThrows(IllegalStateException.class, () -> ref.get().loadHtml("<p>Closed</p>"));
            });
            for (int i = 0; i < 4; i++) fx(() -> {});
            fx(() -> {
                assertFalse(ref.get().isLoading()); assertNull(ref.get().getError());
                assertFalse(ref.get().isExternalImagesAvailable());
            });
        } finally { fx(() -> ref.get().close()); }
    }

    @Test void displaysEmbeddedSvgAndStylesheetAndInlineBackgroundImages() throws Exception {
        String red = svg("red"), blue = svg("blue"), green = svg("lime");
        var file = Files.writeString(temporary.resolve("embedded.html"), """
                <style>body{margin:0;background:white}img,.tile{width:40px;height:40px;display:block}
                .tile{background-image:url('%s')}</style>
                <img src="%s"><div class="tile"></div>
                <div style="width:40px;height:40px;background-image:url('%s')"></div>
                <img src="data:image/webp;base64,UklGRiIAAABXRUJQVlA4TBUAAAAvJ8AJAAcQ/Y/+B4CE8P+8GtH/1AAA">
                <div style="width:40px;height:40px;background-image:url('data:image/webp;base64,UklGRiIAAABXRUJQVlA4TBUAAAAvJ8AJAAcQ/Y/+B4CE8P+8GtH/1AAA')"></div>
                <svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="blue"/></svg>
                """.formatted(blue, red, green));
        var preview = HtmlDocument.read(file);
        assertFalse(preview.hasExternalImages());
        var pane = new AtomicReference<HtmlViewer>(); var stage = new AtomicReference<Stage>();
        try {
            fx(() -> {
                pane.set(viewer(preview));
                var window = new Stage(); stage.set(window);
                window.setScene(new Scene(pane.get(), 240, 300)); window.show();
                assertTrue(media(pane.get()).isDisabled());
            });
            await(() -> loaded(pane.get()));
            // Snapshot pixels exercise the actual WebKit SVG decoder and CSS layout, with JavaScript disabled.
            await(() -> {
                var pixels = browser(pane.get()).snapshot(null, null).getPixelReader();
                return pixels.getColor(20, 20).getRed() > .9 && pixels.getColor(20, 20).getGreen() < .1
                        && pixels.getColor(20, 60).getBlue() > .9 && pixels.getColor(20, 60).getRed() < .1
                        && pixels.getColor(20, 100).getGreen() > .9 && pixels.getColor(20, 100).getRed() < .1
                        && pixels.getColor(20, 140).getRed() > .9 && pixels.getColor(20, 140).getGreen() < .1
                        && pixels.getColor(20, 180).getRed() > .9 && pixels.getColor(20, 180).getGreen() < .1
                        && pixels.getColor(20, 220).getBlue() > .9 && pixels.getColor(20, 220).getRed() < .1;
            });
        } finally {
            fx(() -> { if (pane.get() != null) pane.get().close(); if (stage.get() != null) stage.get().hide(); });
        }
    }

    private static String svg(String color) {
        return "data:image/svg+xml," + URLEncoder.encode("<svg xmlns='http://www.w3.org/2000/svg' width='40' height='40'>"
                + "<rect width='40' height='40' fill='" + color + "'/></svg>", StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Test void networkImagesRequireExplicitPermissionPerViewAndReopeningStartsOffline() throws Exception {
        var images = new AtomicInteger(); var forbidden = new AtomicInteger();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", bytes);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/image.png", exchange -> {
            images.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, bytes.size());
            try (var output = exchange.getResponseBody()) { output.write(bytes.toByteArray()); }
        });
        server.createContext("/forbidden", exchange -> {
            forbidden.incrementAndGet(); exchange.sendResponseHeaders(204, -1); exchange.close();
        });
        server.createContext("/background.png", exchange -> {
            images.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, bytes.size());
            try (var output = exchange.getResponseBody()) { output.write(bytes.toByteArray()); }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        var first = new AtomicReference<HtmlViewer>(); var second = new AtomicReference<HtmlViewer>();
        var reopened = new AtomicReference<HtmlViewer>(); var stage = new AtomicReference<Stage>();
        try {
            var file = Files.writeString(temporary.resolve("page.html"), "<img src='" + base + "/image.png'>"
                    + "<script src='" + base + "/forbidden'></script><iframe src='" + base + "/forbidden'></iframe>"
                    + "<style>@import '" + base + "/forbidden'; @font-face{font-family:evil;src:url('" + base + "/forbidden')}"
                    + ".remote{width:40px;height:40px;background-image:url('" + base + "/background.png')}</style>"
                    + "<div class='remote'>Background</div><img src=\"data:image/svg+xml,"
                    + URLEncoder.encode("<svg xmlns='http://www.w3.org/2000/svg' width='10' height='10'>"
                    + "<script href='" + base + "/forbidden'/><image href='" + base + "/forbidden'/></svg>",
                    StandardCharsets.UTF_8).replace("+", "%20") + "\">");
            var preview = HtmlDocument.read(file);
            fx(() -> {
                first.set(viewer(preview)); second.set(viewer(preview));
                var window = new Stage(); stage.set(window);
                window.setScene(new Scene(new VBox(first.get(), second.get()), 600, 600)); window.show();
            });
            await(() -> loaded(first.get()) && loaded(second.get()));
            assertEquals(0, images.get(), "Opening HTML must never fetch remote images");
            fx(() -> {
                assertEquals("", imageSource(first.get())); assertEquals("", imageSource(second.get()));
                media(first.get()).fire();
            });
            await(() -> images.get() == 2 && loaded(first.get()));
            fx(() -> {
                assertEquals(base + "/image.png", imageSource(first.get()));
                assertEquals("", imageSource(second.get()), "Permission must not spread to another tab");
                assertFalse(browser(first.get()).getEngine().isJavaScriptEnabled());
                assertFalse(browser(second.get()).getEngine().isJavaScriptEnabled());
                assertTrue(first.get().isExternalImagesAllowed());
                first.get().display(HtmlDocument.parse("<p>Replacement</p>"));
                assertFalse(first.get().isExternalImagesAllowed(), "Replacing the document revokes permission");
                assertFalse(first.get().isExternalImagesAvailable());
                first.get().close();
                reopened.set(viewer(preview));
                ((VBox) stage.get().getScene().getRoot()).getChildren().set(0, reopened.get());
            });
            await(() -> loaded(reopened.get()));
            fx(() -> assertEquals("", imageSource(reopened.get())));
            assertEquals(2, images.get(), "Reopening must require a fresh grant");
            assertEquals(0, forbidden.get(), "Scripts and embedded pages must never load");
        } finally {
            fx(() -> {
                if (first.get() != null) first.get().close();
                if (second.get() != null) second.get().close();
                if (reopened.get() != null) reopened.get().close();
                if (stage.get() != null) stage.get().hide();
            });
            server.stop(0);
        }
    }
}
