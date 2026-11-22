# Saved HTML viewer

`de.neitzel.fx.control.HtmlViewer` displays local HTML and SingleFile exports.
It preserves saved CSS, classes, inline styles, embedded fonts and background
images. SVGs are rendered as isolated images; embedded WebP is converted to PNG
in memory for WebView. Files are never rewritten. The control needs ordinary
JavaFX only, with no MVVM or injection initialization.

```java
// In Application.start(Stage stage):
var viewer = new HtmlViewer();
stage.setScene(new Scene(viewer, 900, 600));
stage.setOnHidden(event -> viewer.close());
stage.show();
viewer.load(Path.of("saved-page.html"));
```

`load(Path)` and `loadHtml(String)` prepare content on an owned virtual thread.
`loadHtml(String, URI)` additionally accepts an HTTP(S) source address for relative
images. It never fetches that address. A saved `<base href>` takes precedence.
Input is limited to 32 MiB (UTF-8 bytes for strings). File loads detect the saved encoding.
WebP conversion is bounded to 16 megapixels per image and a 64 MiB encoded-image
budget per preparation. Invalid or oversized images keep their original data.

For applications that already prepare documents in the background, use
`HtmlDocument.read(Path)` or `HtmlDocument.parse(String[, URI])` off the FX thread,
then `viewer.display(document)` on the FX thread. A prepared document is immutable
and may be shared between viewers; permissions are never shared with it.

All viewer operations and property access belong on the FX thread.
`loadingProperty()` covers preparation and browser loading; `errorProperty()`
reports the current preparation/rendering failure. A progress indicator and error
message are included. New loads cancel pending work and discard stale results.
`clear()` releases the current content and leaves the viewer reusable. `close()`
also cancels pending work, is idempotent, and makes future loads fail.

## External images

Each displayed document starts offline. The included **Load external media**
button calls `loadExternalImages()`, which explicitly allows HTTP(S) images only
for that viewer and document. The same permission covers CSS background images.
`externalImagesAvailableProperty()` indicates detected external image addresses;
`externalImagesAllowedProperty()` reports whether permission was granted.
Loading or displaying another document, clearing, or closing revokes permission.
No permission is stored in configuration or in the source file.

JavaScript, embedded webpages, external stylesheets, external fonts and navigation
remain disabled. Embedded SVG is rendered in
[SVG secure image mode](https://www.w3.org/TR/SVG/latest/conform.html), rather than
as active SVG markup. The document CSP is also applied to saved CSS that the CSS
parser does not understand. This is a saved-document viewer; pages that require
JavaScript or missing source images cannot be reconstructed.

Set `mediaButtonVisible=false` if the application supplies its own action.
Bind that action to availability/loading and call `loadExternalImages()` on click.
Applications still own document tabs, archive paths, metadata, search text, and
opening files in external applications.

## FXML and translations

Construction performs no file or network I/O. Ordinary `FXMLLoader` can create
an idle viewer; the controller explicitly loads its document afterward.

```xml
<?import de.neitzel.fx.control.HtmlViewer?>
<HtmlViewer xmlns:fx="http://javafx.com/fxml/1" fx:id="article"
            mediaButtonVisible="true"
            mediaLoadText="Externe Medien laden"
            mediaReloadText="Externe Medien erneut laden"
            mediaTooltipText="Lädt Bilder nur für diese Ansicht und kontaktiert deren Server."
            noExternalMediaText="Keine auflösbaren externen Bildadressen gefunden."/>
```

The four text attributes also expose writable StringProperties for ordinary
binding or live `Messages.observable(...)` translations. Style classes are
`html-viewer` on the root and `html-viewer-media-load` on its action button.

The tests cover SVG/CSS/WebP rendering through WebView snapshot pixels, offline
opening, per-viewer grants, replacement/close, loopback network requests, error
handling, and ordinary FXML loading. They require a graphical JavaFX session.
