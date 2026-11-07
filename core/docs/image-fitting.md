# Opaque image fitting

`ImageScaler.fitOpaque(source, maxWidth, maxHeight, background)` returns an
independent RGB BufferedImage. It fits within positive bounds without enlargement,
keeps aspect ratio to the nearest pixel (minimum one pixel per dimension), and
composites transparent pixels onto the supplied opaque AWT Color. Bilinear
interpolation is used. The source is unchanged and graphics resources are released.

This operation is independent of file format and wire encoding. Callers own image
decoding, any source subsampling, and target quantization. Existing PNG scaling
methods retain their original behavior.

`ImageScalerTest` verifies fitting, no enlargement, transparent/partial-alpha
composition, narrow images, source preservation, and invalid bounds.
