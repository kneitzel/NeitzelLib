package de.neitzel.core.image;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utility class for scaling images while maintaining the aspect ratio.
 * Provides methods to create scaled images either as byte arrays or InputStreams.
 */
@Slf4j
public class ImageScaler {

    /**
     * Specifies the target image format for scaling operations.
     * <p>
     * This variable defines the file format that will be used when writing
     * or encoding the scaled image. The format must be compatible with
     * Java's ImageIO framework (e.g., "png", "jpg", "bmp").
     * <p>
     * In this case, the target format is set to "png", allowing images
     * to be scaled and saved or returned as PNG files. It ensures
     * consistent output format across the image scaling methods in the class.
     */
    private static String TARGET_FORMAT = "png";

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ImageScaler() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Scales an image provided as a byte array to the specified dimensions and returns an InputStream of the scaled image.
     * <p>
     * The scaling behavior is determined by the enforceSize parameter:
     * - If enforceSize is false, the provided dimensions are treated as maximum values, maintaining the original aspect ratio.
     * - If enforceSize is true, the resulting image will match the exact dimensions, potentially with transparent borders to keep the original aspect ratio.
     *
     * @param originalImageBytes The byte array representing the original image.
     * @param width              The (maximum) width of the target scaled image.
     * @param height             The (maximum) height of the target scaled image.
     * @param enforceSize        A flag indicating whether to enforce the exact scaled dimensions. If false, the resulting image retains its aspect ratio.
     * @return An InputStream representing the scaled image, or null if the operation fails or the input image is invalid.
     */
    public static InputStream scaledImage(final byte[] originalImageBytes, final int width, final int height, final boolean enforceSize) {
        // Get the scaled image.
        byte[] bytes = createScaledImage(originalImageBytes, width, height, enforceSize);

        // Validation of result.
        if (bytes == null || bytes.length == 0) return null;

        // Return new InputStream.
        return new ByteArrayInputStream(bytes);
    }

    /**
     * Creates a scaled image from the given byte array representation and returns it as a byte array.
     * <p>
     * If enforceSize is set to false, the given dimensions are used as maximum values for scaling.
     * The resulting image may retain its aspect ratio and be smaller than the specified dimensions.
     * If enforceSize is true, the resulting image will match the exact dimensions, potentially adding
     * transparent areas on the top/bottom or right/left sides to maintain the original aspect ratio.
     *
     * @param originalImageBytes The byte array representing the original image.
     * @param width              The (maximum) width of the target image.
     * @param height             The (maximum) height of the target image.
     * @param enforceSize        A flag indicating whether the resulting image should strictly adhere to specified dimensions.
     *                           If false, the image will retain its aspect ratio without empty borders.
     * @return A byte array representing the scaled image, or null if the operation failed or the input was invalid.
     */
    public static byte[] createScaledImage(final byte[] originalImageBytes, final int width, final int height, final boolean enforceSize) {
        // Validation
        if (originalImageBytes == null) return null;
        if (originalImageBytes.length == 0) return null;

        try {
            // Create the image from a byte array.
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(originalImageBytes));
            return createScaledImage(originalImage, width, height, enforceSize);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Creates a scaled version of the given {@link BufferedImage} and returns it as a byte array.
     * <p>
     * The scaling behavior is determined by the enforceSize parameter:
     * - If enforceSize is false, the provided dimensions are treated as maximum values, and the image
     * will maintain its original aspect ratio. The resulting image may be smaller than the specified
     * dimensions.
     * - If enforceSize is true, the resulting image will match the exact specified dimensions, potentially
     * adding transparent padding to fit the aspect ratio.
     *
     * @param originalImage The original image to be scaled. Must not be null.
     * @param width         The specified (maximum) width of the scaled image.
     * @param height        The specified (maximum) height of the scaled image.
     * @param enforceSize   A flag indicating whether the scaled image should strictly conform to the specified
     *                      dimensions. If true, the image may include transparent areas to maintain the aspect ratio.
     * @return A byte array representing the scaled image, or null if the scaling operation fails or the input image is invalid.
     */
    protected static byte[] createScaledImage(final BufferedImage originalImage, final int width, final int height, final boolean enforceSize) {
        // Validation
        if (originalImage == null) return null;

        try {
            // Get the scale factor.
            double scaleWidth = (double) width / (double) originalImage.getWidth();
            double scaleHeight = (double) height / (double) originalImage.getHeight();

            double scaleFactor;
            if (scaleWidth > scaleHeight) {
                scaleFactor = scaleHeight;
            } else {
                scaleFactor = scaleWidth;
            }

            // Calculate target size of scaled image.
            int newHeight = (int) (scaleFactor * originalImage.getHeight());
            int newWidth = (int) (scaleFactor * originalImage.getWidth());

            // Cooordinates of new picture and size of new picture.
            int x, y, usedHeight, usedWidth;
            if (enforceSize) {
                usedHeight = height;
                usedWidth = width;
                x = (width - newWidth) / 2;
                y = (height - newHeight) / 2;
            } else {
                x = 0;
                y = 0;
                usedHeight = newHeight;
                usedWidth = newWidth;
            }

            // Scale the image
            BufferedImage scaledImage = new BufferedImage(usedWidth, usedHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = scaledImage.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(originalImage, x, y, newWidth, newHeight, null);

            // Get the bytes of the image
            try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
                ImageIO.write(scaledImage, TARGET_FORMAT, stream);
                return stream.toByteArray();
            }
        } catch (IOException ex) {
            log.error("IOException while scaling image.", ex);
            return null;
        }
    }
}