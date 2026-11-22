package de.neitzel.fx.control;

import java.nio.file.Files;
import java.nio.file.Path;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HtmlDocumentTest {
    @TempDir Path temporary;

    @Test void stringSourceResolvesRelativeImagesWithoutFetchingItsAddress() {
        var document = HtmlDocument.parse("<img src='../image.png'>", java.net.URI.create("https://example.test/path/page.html"));
        assertTrue(document.hasExternalImages());
        assertEquals("https://example.test/image.png", Jsoup.parse(document.externalImagesHtml()).selectFirst("img").attr("src"));
        assertFalse(Jsoup.parse(document.offlineHtml()).selectFirst("img").hasAttr("src"));
        assertThrows(IllegalArgumentException.class, () -> HtmlDocument.parse("<p>Text</p>", java.net.URI.create("file:///tmp/page.html")));
    }

    @Test void resolvesOnlyWebImagesAndKeepsOfflineIndexingAndOriginalFileUnchanged() throws Exception {
        String source = """
                <base href="https://example.test/folder/page.html"><title>Example</title>
                <script src="https://example.test/script"></script><iframe src="https://example.test/frame"></iframe>
                <img src="picture.png" onerror="alert(1)"><img src="//cdn.example.test/image.jpg">
                <img src="file:///etc/passwd"><img src="javascript:alert(1)">
                <img src="data:image/svg+xml;base64,PHN2Zz4="><img src="data:image/png;base64,YWJj">
                <a href="https://example.test/link">Text</a>
                """;
        var file = Files.writeString(temporary.resolve("page.html"), source);
        var preview = HtmlDocument.read(file);
        assertTrue(preview.hasExternalImages());
        var online = Jsoup.parse(preview.externalImagesHtml());
        assertEquals(java.util.List.of("https://example.test/folder/picture.png", "https://cdn.example.test/image.jpg",
                "data:image/svg+xml;base64,PHN2Zz4=", "data:image/png;base64,YWJj"), online.select("img[src]").eachAttr("src"));
        assertTrue(online.select("script,iframe,base,[onerror],a[href]").isEmpty());
        assertEquals(java.util.List.of("data:image/svg+xml;base64,PHN2Zz4=", "data:image/png;base64,YWJj"),
                Jsoup.parse(preview.offlineHtml()).select("img[src]").eachAttr("src"));
        assertEquals(source, Files.readString(file));
    }

    @Test void relativeImagesWithoutWebBaseRemainUnavailable() throws Exception {
        var file = Files.writeString(temporary.resolve("local.html"), "<img src='local.png'><img src='file:///tmp/local.png'>");
        var preview = HtmlDocument.read(file);
        assertFalse(preview.hasExternalImages());
        assertTrue(Jsoup.parse(preview.externalImagesHtml()).select("img[src]").isEmpty());
    }

    @Test void keepsSavedLayoutAndEmbeddedBackgroundsWhileGatingRemoteImages() throws Exception {
        String svg = "data:image/svg+xml,%3Csvg%20xmlns='http://www.w3.org/2000/svg'%3E%3C/svg%3E";
        var file = Files.writeString(temporary.resolve("saved.html"), """
                <html class="saved"><head><base href="https://example.test/path/">
                <style>@import 'https://example.test/track.css';
                .hero { width: 120px; background-image:url("%s"); }
                @media (min-width: 200px) { .remote {background: url('../background.png')} }
                @font-face {font-family: saved;src:url('https://example.test/font.woff2')}
                </style></head><body class="page" style="margin:0">
                <div class="hero" style="height:80px;background-image:url('%s')">Article</div>
                <svg onload="alert(1)"></svg><script>alert(1)</script>
                </body></html>
                """.formatted(svg, svg.replace("'", "%27")));
        var preview = HtmlDocument.read(file);
        assertTrue(preview.hasExternalImages(), "CSS backgrounds must enable the per-tab media button");
        var offline = Jsoup.parse(preview.offlineHtml());
        var online = Jsoup.parse(preview.externalImagesHtml());
        assertEquals("saved", offline.selectFirst("html").className());
        assertEquals("page", offline.body().className());
        assertTrue(offline.selectFirst(".hero").attr("style").contains("data:image/svg+xml"));
        assertTrue(offline.selectFirst("style").data().contains("data:image/svg+xml"));
        assertFalse(offline.selectFirst("style").data().contains("background.png"));
        assertTrue(online.selectFirst("style").data().contains("https://example.test/background.png"));
        assertFalse(online.selectFirst("style").data().contains("track.css"));
        assertFalse(online.selectFirst("style").data().contains("https://example.test/font.woff2"));
        assertTrue(offline.select("script,iframe,svg,link,base,[onload]").isEmpty());
        assertTrue(offline.selectFirst("meta[http-equiv]").attr("content").contains("img-src data:;"));
    }

    @Test void preservesCustomWrappersAndCssThatTheParserDoesNotUnderstand() throws Exception {
        String css = ".before {color: blue} @supports selector(:focus-visible) {.after {color:red}}";
        var file = Files.writeString(temporary.resolve("modern.html"), "<html data-theme='light'><style>" + css
                + "</style><body><a-menu data-open='false' aria-hidden='true' class='menu'>Text</a-menu></body></html>");
        var document = Jsoup.parse(HtmlDocument.read(file).offlineHtml());
        assertEquals(css, document.selectFirst("style").data());
        assertEquals("false", document.selectFirst("a-menu").attr("data-open"));
        assertEquals("true", document.selectFirst("a-menu").attr("aria-hidden"));
        assertEquals("light", document.selectFirst("html").attr("data-theme"));
    }

    @Test void aBrokenEmbeddedWebpDoesNotPreventOpeningTheDocument() throws Exception {
        var file = Files.writeString(temporary.resolve("broken.html"), "<img src='data:image/webp;base64,abcde'><p>Readable text</p>");
        assertTrue(HtmlDocument.read(file).offlineHtml().contains("Readable text"));
    }
}
