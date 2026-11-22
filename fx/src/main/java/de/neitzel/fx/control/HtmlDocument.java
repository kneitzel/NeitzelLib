package de.neitzel.fx.control;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.jsoup.Jsoup;

/** Immutable prepared HTML, shareable between viewers without sharing external-image permission.
 * Preparation performs no network requests and may be expensive; call it off the FX thread.
 */
@lombok.RequiredArgsConstructor(access = lombok.AccessLevel.PACKAGE)
public final class HtmlDocument {
    private static final int MAX_BYTES = 32 * 1024 * 1024;
    private final String offlineHtml;
    private final String externalImagesHtml;
    private final boolean hasExternalImages;

    /** Reads at most 32 MiB, detects the saved character encoding, and never modifies the file. */
    public static HtmlDocument read(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("HTML exceeds the 32 MiB preview limit.");
            return HtmlPresentation.prepare(Jsoup.parse(new ByteArrayInputStream(bytes), null, ""));
        }
    }

    public static HtmlDocument parse(String html) { return parse(html, null); }

    /** An optional HTTP(S) source address resolves relative images; it is never fetched. */
    public static HtmlDocument parse(String html, URI source) {
        Objects.requireNonNull(html, "html");
        if (source != null && httpUri(source.toString()) == null) {
            throw new IllegalArgumentException("HTML source must be an HTTP(S) address.");
        }
        if (html.length() > MAX_BYTES || html.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("HTML exceeds the 32 MiB preview limit.");
        }
        return HtmlPresentation.prepare(Jsoup.parse(html, source == null ? "" : source.toASCIIString()));
    }

    public boolean hasExternalImages() { return hasExternalImages; }
    String offlineHtml() { return offlineHtml; }
    String externalImagesHtml() { return externalImagesHtml; }

    static URI imageUri(String value, URI base) {
        if (value.isBlank()) return null;
        try {
            URI uri = new URI(value.startsWith("//") ? "https:" + value : value);
            if (!uri.isAbsolute() && base != null) uri = base.resolve(uri);
            return httpUri(uri.toString());
        } catch (URISyntaxException invalid) { return null; }
    }

    static URI httpUri(String value) {
        try {
            URI uri = new URI(value.strip());
            return uri.getHost() != null && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    ? uri : null;
        } catch (URISyntaxException invalid) { return null; }
    }
}
