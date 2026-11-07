package de.neitzel.core.image;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImageScalerTest {
    @Test void fitsWithoutUpscalingAndCompositesTransparency() {
        BufferedImage source = new BufferedImage(128,64,BufferedImage.TYPE_INT_ARGB);
        BufferedImage result = ImageScaler.fitOpaque(source,64,64,Color.BLUE);
        assertEquals(64,result.getWidth());
        assertEquals(32,result.getHeight());
        assertEquals(Color.BLUE.getRGB(),result.getRGB(0,0));
        assertEquals(0,source.getRGB(0,0));
        BufferedImage tiny = new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
        tiny.setRGB(0,0,0x80ff0000);
        result = ImageScaler.fitOpaque(tiny,64,64,Color.BLUE);
        assertEquals(1,result.getWidth());
        Color mixed = new Color(result.getRGB(0,0));
        assertEquals(128,mixed.getRed(),1);
        assertEquals(127,mixed.getBlue(),1);
        BufferedImage narrow = ImageScaler.fitOpaque(new BufferedImage(1,1000,BufferedImage.TYPE_INT_ARGB),64,64,Color.BLACK);
        assertEquals(1,narrow.getWidth()); assertEquals(64,narrow.getHeight());
        assertThrows(IllegalArgumentException.class, () -> ImageScaler.fitOpaque(tiny,0,64,Color.BLACK));
    }
}
