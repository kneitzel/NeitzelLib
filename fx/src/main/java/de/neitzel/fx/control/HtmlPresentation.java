package de.neitzel.fx.control;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSExpressionMemberTermURI;
import com.helger.css.decl.CSSFontFaceRule;
import com.helger.css.decl.ICSSTopLevelRule;
import com.helger.css.decl.visit.CSSVisitor;
import com.helger.css.decl.visit.DefaultCSSUrlVisitor;
import com.helger.css.reader.CSSReader;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.CollectingCSSParseErrorHandler;
import com.helger.css.handler.DoNothingCSSParseExceptionCallback;
import com.helger.css.writer.CSSWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

/** Presentation preserves saved CSS; text indexing deliberately uses the simpler reader markup. */
final class HtmlPresentation {
    private static final Safelist SAFE = Safelist.relaxed()
            .addTags("div", "main", "header", "footer", "nav", "aside", "section", "article", "figure",
                    "figcaption", "small", "mark", "s", "style", "picture", "details", "summary", "time",
                    "form", "fieldset", "legend", "label", "input", "button", "select", "option")
            .addAttributes(":all", "class", "id", "style", "hidden", "dir", "lang")
            .addAttributes("input", "type", "value", "placeholder", "checked", "disabled")
            .addAttributes("button", "type", "disabled")
            .addAttributes("option", "value", "selected")
            .addAttributes("style", "media", "type")
            .removeAttributes("a", "href")
            .addProtocols("img", "src", "data");
    private static final Pattern IMAGE = Pattern.compile(
            "data:image/(?:png|jpeg|gif|webp|svg\\+xml)(?:;[^,]*)?,.+", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    static HtmlDocument prepare(Document source) {
        checkCancelled();
        var images = new EmbeddedImages();
        for (var style : source.select("style")) {
            String css = images.adapt(style.data());
            style.empty().appendChild(new DataNode(css));
        }
        for (var element : source.select("[style]")) element.attr("style", images.adapt(element.attr("style")));
        var baseElement = source.selectFirst("base[href]");
        URI base = HtmlDocument.httpUri(baseElement == null ? source.baseUri() : baseElement.attr("href"));
        // Export inline SVG as an isolated image too, keeping page logos and icons without active SVG DOM.
        for (var svg : source.select("svg").stream()
                .filter(svg -> svg.parents().stream().noneMatch(parent -> parent.tagName().equals("svg"))).toList()) {
            svg.attr("xmlns", "http://www.w3.org/2000/svg");
            var image = new org.jsoup.nodes.Element("img");
            image.attributes().addAll(svg.attributes());
            image.attr("src", "data:image/svg+xml;base64," + Base64.getEncoder()
                    .encodeToString(svg.outerHtml().getBytes(StandardCharsets.UTF_8)));
            svg.replaceWith(image);
        }
        source.select("script, template, noscript, iframe, object, embed, link, meta, base").remove();
        // Cleaner handles the body only. Keep saved styles in their original order, ahead of body styles.
        var headStyles = source.head().select("style").stream().map(style -> style.clone()).toList();
        // Embedded SVG remains an image, never active inline SVG markup (SVG secure image mode).
        for (var image : source.select("img[src]")) {
            String value = images.adapt(image.attr("src").strip());
            image.attr("src", value);
            if (IMAGE.matcher(value).matches()) continue;
            var remote = HtmlDocument.imageUri(value, base);
            if (remote == null) image.removeAttr("src");
            else image.attr("src", remote.toASCIIString());
        }
        var safelist = new Safelist(SAFE);
        for (var element : source.getAllElements()) {
            checkCancelled();
            // Saved custom-element wrappers are inert with JavaScript disabled, but their CSS still matters.
            if (element.tagName().contains("-")) safelist.addTags(element.tagName());
            for (var attribute : element.attributes()) {
                if (presentationAttribute(attribute.getKey())) safelist.addAttributes(":all", attribute.getKey());
            }
        }
        var clean = new Cleaner(safelist).clean(source);
        copyAttributes(source.selectFirst("html"), clean.selectFirst("html"));
        copyAttributes(source.body(), clean.body());
        clean.title(source.title());
        for (var style : headStyles) {
            var saved = clean.head().appendElement("style");
            if (style.hasAttr("media")) saved.attr("media", style.attr("media"));
            saved.appendChild(new DataNode(style.data()));
        }
        var online = render(clean.clone(), base, true);
        var offline = render(clean, base, false);
        return new HtmlDocument(offline.html(), online.html(), online.external());
    }

    private static void copyAttributes(org.jsoup.nodes.Element source, org.jsoup.nodes.Element target) {
        for (String name : new String[]{"class", "id", "style", "hidden", "dir", "lang"}) {
            if (source.hasAttr(name)) target.attr(name, source.attr(name));
        }
        for (var attribute : source.attributes()) {
            if (presentationAttribute(attribute.getKey())) target.attr(attribute.getKey(), attribute.getValue());
        }
    }

    private static boolean presentationAttribute(String name) {
        return name.startsWith("data-") || name.startsWith("aria-") || name.equals("role");
    }

    private record Rendered(String html, boolean external) {}

    private static Rendered render(Document document, URI base, boolean externalImages) {
        boolean[] external = {false};
        for (var image : document.select("img[src]")) {
            if (HtmlDocument.httpUri(image.attr("src")) != null) {
                external[0] = true;
                if (!externalImages) image.removeAttr("src");
            }
        }
        for (var style : document.select("style")) {
            String saved = style.data();
            style.empty().appendChild(new DataNode(css(saved, base, externalImages, external)));
        }
        for (var element : document.select("[style]")) {
            // Parse an attribute as a rule, so URL handling is identical for both kinds of saved CSS.
            String sheet = css("x{" + element.attr("style") + "}", base, externalImages, external);
            int start = sheet.indexOf('{'), end = sheet.lastIndexOf('}');
            element.attr("style", start >= 0 && end > start ? sheet.substring(start + 1, end) : "");
        }
        document.head().prependElement("meta").attr("http-equiv", "Content-Security-Policy")
                .attr("content", policy(externalImages));
        document.head().prependElement("meta").attr("charset", "UTF-8");
        document.head().appendElement("meta").attr("name", "viewport").attr("content", "width=device-width, initial-scale=1");
        document.outputSettings().prettyPrint(false);
        return new Rendered("<!doctype html>" + document.outerHtml(), external[0]);
    }

    private static String css(String source, URI base, boolean allowExternal, boolean[] external) {
        checkCancelled();
        var errors = new CollectingCSSParseErrorHandler();
        var settings = new CSSReaderSettings().setCustomErrorHandler(errors)
                .setCustomExceptionHandler(new DoNothingCSSParseExceptionCallback());
        var sheet = CSSReader.readFromStringReader(source, settings);
        // Keep unsupported author CSS: the document CSP still enforces the resource boundary.
        if (sheet == null || errors.hasParseErrors()) return source;
        sheet.removeAllImportRules();
        CSSVisitor.visitCSSUrl(sheet, new DefaultCSSUrlVisitor() {
            @Override public void onUrlDeclaration(ICSSTopLevelRule rule, CSSDeclaration declaration,
                                                  CSSExpressionMemberTermURI term) {
                String value = term.getURIString();
                if (value.regionMatches(true, 0, "data:", 0, 5) || value.startsWith("#")) return;
                URI remote = HtmlDocument.imageUri(value, base);
                boolean image = !(rule instanceof CSSFontFaceRule);
                if (remote != null && image) external[0] = true;
                term.setURIString(remote != null && image && allowExternal ? remote.toASCIIString() : "data:,");
            }
        });
        // A stylesheet's escaped strings must not terminate its HTML raw-text container.
        return new CSSWriter().getCSSAsString(sheet).replaceAll("(?i)</style", "<\\\\/style");
    }

    static String policy(boolean externalImages) {
        return "default-src 'none'; img-src data:" + (externalImages ? " https: http:" : "")
                + "; style-src 'unsafe-inline'; font-src data:; script-src 'none'; frame-src 'none';"
                + " object-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'";
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
    }

    private HtmlPresentation() {}
}
