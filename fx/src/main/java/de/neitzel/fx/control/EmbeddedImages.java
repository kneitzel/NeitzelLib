package de.neitzel.fx.control;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** WebView does not decode WebP. Convert embedded images once per preparation, without changing the file. */
final class EmbeddedImages {
    private static final Pattern WEBP = Pattern.compile("data:image/webp;base64,[A-Za-z0-9+/=]+", Pattern.CASE_INSENSITIVE);
    private static final long MAX_PIXELS = 16 * 1024 * 1024;
    private final Map<String, String> converted = new HashMap<>();
    private int remainingBytes = 64 * 1024 * 1024;

    String adapt(String value) {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        return WEBP.matcher(value).replaceAll(match -> java.util.regex.Matcher.quoteReplacement(
                converted.computeIfAbsent(match.group(), this::png)));
    }

    private String png(String value) {
        if (remainingBytes <= 0) return value;
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(
                Base64.getDecoder().decode(value.substring(value.indexOf(',') + 1))))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return value;
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) return value;
                var image = reader.read(0);
                var output = new ByteArrayOutputStream();
                if (!ImageIO.write(image, "png", output)) return value;
                if ((long) output.size() * 4 / 3 > remainingBytes) return value;
                remainingBytes -= (output.size() + 2) / 3 * 4;
                return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException invalid) { return value; }
    }
}
